package com.example.network.p2p

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

enum class FileTypeCategory {
    PHOTO,
    VIDEO,
    AUDIO,
    DOCUMENT_OR_OTHER
}

data class PendingDestination(
    val uri: Uri?,
    val tempFile: File?,
    val outputStream: OutputStream,
    val finalName: String,
    val mimeType: String,
    val isMediaStore: Boolean,
    val relativePath: String = ""
)

data class PublishedFile(
    val uri: Uri,
    val filePath: String,
    val file: File
)

object PublicMediaStorageHelper {

    private const val TAG = "PublicMediaStorage"
    private const val SUBFOLDER_NAME = "Taha"

    /**
     * Determines whether the file is a Photo, Video, Audio, or general Document/Archive/APK.
     */
    fun categorizeFile(fileName: String, mimeType: String): FileTypeCategory {
        val lowerName = fileName.lowercase()
        val lowerMime = mimeType.lowercase()

        return when {
            lowerMime.startsWith("image/") ||
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".webp") ||
                lowerName.endsWith(".gif") || lowerName.endsWith(".heic") ||
                lowerName.endsWith(".heif") || lowerName.endsWith(".dng") ||
                lowerName.endsWith(".bmp") || lowerName.endsWith(".svg") -> FileTypeCategory.PHOTO

            lowerMime.startsWith("video/") ||
                lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".mov") || lowerName.endsWith(".avi") ||
                lowerName.endsWith(".3gp") || lowerName.endsWith(".webm") ||
                lowerName.endsWith(".flv") || lowerName.endsWith(".wmv") ||
                lowerName.endsWith(".m4v") || lowerName.endsWith(".ts") -> FileTypeCategory.VIDEO

            lowerMime.startsWith("audio/") ||
                lowerName.endsWith(".mp3") || lowerName.endsWith(".m4a") ||
                lowerName.endsWith(".wav") || lowerName.endsWith(".aac") ||
                lowerName.endsWith(".flac") || lowerName.endsWith(".ogg") ||
                lowerName.endsWith(".opus") || lowerName.endsWith(".wma") -> FileTypeCategory.AUDIO

            else -> FileTypeCategory.DOCUMENT_OR_OTHER
        }
    }

    /**
     * Resolves the exact, accurate MIME type for a given file.
     */
    fun resolveMimeType(fileName: String, rawMime: String): String {
        val lowerName = fileName.lowercase()
        val dotIdx = lowerName.lastIndexOf('.')
        val ext = if (dotIdx > 0 && dotIdx < lowerName.length - 1) lowerName.substring(dotIdx + 1) else ""

        // Prioritize known extensions for proper Android system handling
        when (ext) {
            "apk" -> return "application/vnd.android.package-archive"
            "zip" -> return "application/zip"
            "pdf" -> return "application/pdf"
            "jpg", "jpeg" -> return "image/jpeg"
            "png" -> return "image/png"
            "webp" -> return "image/webp"
            "gif" -> return "image/gif"
            "mp4" -> return "video/mp4"
            "mkv" -> return "video/x-matroska"
            "mp3" -> return "audio/mpeg"
        }

        if (rawMime.isNotBlank() && rawMime != "application/octet-stream" && rawMime != "*/*" && rawMime.contains("/")) {
            return rawMime
        }

        if (ext.isNotEmpty()) {
            val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!mapped.isNullOrBlank()) {
                return mapped
            }
            // Explicit fallbacks for common file types
            when (ext) {
                "rar" -> return "application/x-rar-compressed"
                "7z" -> return "application/x-7z-compressed"
                "tar" -> return "application/x-tar"
                "gz" -> return "application/gzip"
                "txt" -> return "text/plain"
                "doc" -> return "application/msword"
                "docx" -> return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xls" -> return "application/vnd.ms-excel"
                "xlsx" -> return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "ppt" -> return "application/vnd.ms-powerpoint"
                "pptx" -> return "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "json" -> return "application/json"
                "xml" -> return "application/xml"
                "csv" -> return "text/csv"
                "html" -> return "text/html"
            }
        }
        return "application/octet-stream"
    }

    /**
     * Opens a safe, pending destination in Android shared/public storage.
     * While writing, the item is marked IS_PENDING = 1 (on API 29+) or saved as a .pending file,
     * ensuring it is NEVER published or visible until transfer finishes successfully.
     */
    fun openPendingDestination(
        context: Context,
        originalName: String,
        rawMime: String,
        duplicatePolicy: DuplicatePolicy
    ): PendingDestination? {
        val mimeType = resolveMimeType(originalName, rawMime)
        val category = categorizeFile(originalName, mimeType)

        // Modern MediaStore approach on API 29+ (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val (collectionUri, relativePath) = when (category) {
                    FileTypeCategory.PHOTO -> {
                        Pair(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_PICTURES}/$SUBFOLDER_NAME/")
                    }
                    FileTypeCategory.VIDEO -> {
                        Pair(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_MOVIES}/$SUBFOLDER_NAME/")
                    }
                    FileTypeCategory.AUDIO -> {
                        Pair(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_MUSIC}/$SUBFOLDER_NAME/")
                    }
                    FileTypeCategory.DOCUMENT_OR_OTHER -> {
                        Pair(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER_NAME/")
                    }
                }

                val finalName = resolveUniqueFileNameForMediaStore(
                    context = context,
                    collection = collectionUri,
                    relativePath = relativePath,
                    originalName = originalName,
                    policy = duplicatePolicy
                ) ?: return null

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val itemUri = context.contentResolver.insert(collectionUri, contentValues)
                if (itemUri != null) {
                    val os = context.contentResolver.openOutputStream(itemUri, "w")
                    if (os != null) {
                        return PendingDestination(
                            uri = itemUri,
                            tempFile = null,
                            outputStream = os,
                            finalName = finalName,
                            mimeType = mimeType,
                            isMediaStore = true,
                            relativePath = relativePath
                        )
                    } else {
                        context.contentResolver.delete(itemUri, null, null)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open MediaStore destination, falling back to public filesystem", e)
            }
        }

        // Fallback for API < 29 or if MediaStore insert fails
        return openLegacyPublicDestination(context, originalName, mimeType, category, duplicatePolicy)
    }

    private fun openLegacyPublicDestination(
        context: Context,
        originalName: String,
        mimeType: String,
        category: FileTypeCategory,
        duplicatePolicy: DuplicatePolicy
    ): PendingDestination? {
        val folderType = when (category) {
            FileTypeCategory.PHOTO -> Environment.DIRECTORY_PICTURES
            FileTypeCategory.VIDEO -> Environment.DIRECTORY_MOVIES
            FileTypeCategory.AUDIO -> Environment.DIRECTORY_MUSIC
            FileTypeCategory.DOCUMENT_OR_OTHER -> Environment.DIRECTORY_DOWNLOADS
        }

        val basePublicDir = Environment.getExternalStoragePublicDirectory(folderType)
        val targetDir = File(basePublicDir, SUBFOLDER_NAME)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val candidate = File(targetDir, originalName)
        val finalFile = when {
            !candidate.exists() -> candidate
            duplicatePolicy == DuplicatePolicy.REPLACE -> {
                candidate.delete()
                candidate
            }
            duplicatePolicy == DuplicatePolicy.CANCEL -> return null
            else -> {
                // KEEP_BOTH
                val dotIdx = originalName.lastIndexOf('.')
                val base = if (dotIdx > 0) originalName.substring(0, dotIdx) else originalName
                val ext = if (dotIdx > 0) originalName.substring(dotIdx) else ""
                var counter = 1
                var f: File
                do {
                    f = File(targetDir, "$base ($counter)$ext")
                    counter++
                } while (f.exists())
                f
            }
        }

        // Temporary hidden pending file while downloading so Gallery/File Manager ignores it
        val tempFile = File(targetDir, ".pending_${System.currentTimeMillis()}_${finalFile.name}")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val os = FileOutputStream(tempFile)
        return PendingDestination(
            uri = null,
            tempFile = tempFile,
            outputStream = os,
            finalName = finalFile.name,
            mimeType = mimeType,
            isMediaStore = false,
            relativePath = "$folderType/$SUBFOLDER_NAME/"
        )
    }

    /**
     * Finalizes and publishes the successfully transferred file to Android shared storage.
     * Sets IS_PENDING = 0 and triggers MediaScanner, making it immediately visible in
     * Gallery, Google Photos, and Android File Manager.
     */
    fun finalizeAndPublish(
        context: Context,
        destination: PendingDestination
    ): PublishedFile? {
        try {
            destination.outputStream.flush()
            destination.outputStream.close()
        } catch (_: Exception) {}

        if (destination.isMediaStore && destination.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Publish MediaStore item (set IS_PENDING = 0)
                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(destination.uri, updateValues, null, null)

                // Query the actual filesystem path from MediaStore
                var realPath: String? = null
                try {
                    context.contentResolver.query(
                        destination.uri,
                        arrayOf(MediaStore.MediaColumns.DATA),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (idx >= 0) {
                                realPath = cursor.getString(idx)
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (realPath.isNullOrBlank() && destination.relativePath.isNotBlank()) {
                    val candidate = File(Environment.getExternalStorageDirectory(), "${destination.relativePath}${destination.finalName}")
                    if (candidate.exists()) {
                        realPath = candidate.absolutePath
                    }
                }

                val file = if (!realPath.isNullOrBlank()) File(realPath!!) else File(destination.finalName)

                // Trigger MediaScanner for immediate visibility in gallery/file manager
                if (!realPath.isNullOrBlank()) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(realPath),
                        arrayOf(destination.mimeType),
                        null
                    )
                }

                return PublishedFile(
                    uri = destination.uri,
                    filePath = realPath ?: destination.uri.toString(),
                    file = file
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing MediaStore item", e)
            }
        }

        // Legacy filesystem finalization
        if (destination.tempFile != null && destination.tempFile.exists()) {
            val targetDir = destination.tempFile.parentFile ?: return null
            val finalFile = File(targetDir, destination.finalName)
            if (finalFile.exists()) {
                finalFile.delete()
            }
            val renamed = destination.tempFile.renameTo(finalFile)
            if (renamed) {
                // Notify system media scanner so it appears in Gallery & File Manager
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalFile.absolutePath),
                    arrayOf(destination.mimeType),
                    null
                )
                return PublishedFile(
                    uri = Uri.fromFile(finalFile),
                    filePath = finalFile.absolutePath,
                    file = finalFile
                )
            }
        }

        return null
    }

    /**
     * Safely discards and completely removes an incomplete or failed transfer.
     * Ensures NO corrupted or partial files ever linger in Android shared storage.
     */
    fun discardPendingFile(
        context: Context,
        destination: PendingDestination
    ) {
        try {
            destination.outputStream.close()
        } catch (_: Exception) {}

        if (destination.isMediaStore && destination.uri != null) {
            try {
                context.contentResolver.delete(destination.uri, null, null)
                Log.d(TAG, "Cleaned up incomplete MediaStore row: ${destination.uri}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean up incomplete MediaStore row", e)
            }
        }

        if (destination.tempFile != null && destination.tempFile.exists()) {
            try {
                destination.tempFile.delete()
                Log.d(TAG, "Cleaned up incomplete temp file: ${destination.tempFile.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean up incomplete temp file", e)
            }
        }
    }

    /**
     * Resolves unique display name for MediaStore collections based on DuplicatePolicy.
     */
    private fun resolveUniqueFileNameForMediaStore(
        context: Context,
        collection: Uri,
        relativePath: String,
        originalName: String,
        policy: DuplicatePolicy
    ): String? {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(originalName, "%$SUBFOLDER_NAME%")

        var exists = false
        var existingId = -1L

        try {
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    exists = true
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    if (idCol >= 0) existingId = cursor.getLong(idCol)
                }
            }
        } catch (_: Exception) {}

        if (!exists) return originalName

        return when (policy) {
            DuplicatePolicy.CANCEL -> null
            DuplicatePolicy.REPLACE -> {
                if (existingId != -1L) {
                    try {
                        val itemUri = ContentUris.withAppendedId(collection, existingId)
                        context.contentResolver.delete(itemUri, null, null)
                    } catch (_: Exception) {}
                }
                originalName
            }
            DuplicatePolicy.KEEP_BOTH -> {
                val dotIdx = originalName.lastIndexOf('.')
                val base = if (dotIdx > 0) originalName.substring(0, dotIdx) else originalName
                val ext = if (dotIdx > 0) originalName.substring(dotIdx) else ""

                var counter = 1
                var candidateName: String
                do {
                    candidateName = "$base ($counter)$ext"
                    var candidateExists = false
                    try {
                        context.contentResolver.query(
                            collection,
                            projection,
                            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                            arrayOf(candidateName, "%$SUBFOLDER_NAME%"),
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                candidateExists = true
                            }
                        }
                    } catch (_: Exception) {}
                    counter++
                } while (candidateExists)

                candidateName
            }
        }
    }
}
