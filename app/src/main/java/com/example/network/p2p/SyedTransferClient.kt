package com.example.network.p2p

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.local.TransferDirection
import com.example.data.local.TransferEntity
import com.example.data.local.TransferStatus
import com.example.data.model.SessionFileMeta
import com.example.data.repository.TransferHistoryRepository
import com.example.network.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

enum class DuplicatePolicy {
    REPLACE,
    KEEP_BOTH,
    CANCEL
}

data class ClientTransferProgress(
    val fileId: String,
    val fileName: String,
    val currentFileIndex: Int,
    val totalFiles: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val progressPercent: Int,
    val speedBytesPerSec: Long,
    val remainingSeconds: Long,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null
)

class SyedTransferClient(
    private val context: Context,
    private val historyRepository: TransferHistoryRepository
) {
    companion object {
        private const val TAG = "SyedTransferClient"
    }

    private val _transferProgress = MutableStateFlow<ClientTransferProgress?>(null)
    val transferProgress: StateFlow<ClientTransferProgress?> = _transferProgress.asStateFlow()

    @Volatile
    private var isCancelled = false

    @Volatile
    private var isPaused = false

    suspend fun fetchManifest(host: String, port: Int, token: String, receiverName: String): Pair<String, List<SessionFileMeta>>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("http://$host:$port/manifest?token=$token&receiver=${java.net.URLEncoder.encode(receiverName, "UTF-8")}")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }

                if (connection.responseCode == 200) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val senderName = json.optString("senderName", "Syed User")
                    val filesArray = json.getJSONArray("files")
                    val fileList = mutableListOf<SessionFileMeta>()

                    for (i in 0 until filesArray.length()) {
                        val item = filesArray.getJSONObject(i)
                        fileList.add(
                            SessionFileMeta(
                                id = item.getString("id"),
                                name = item.getString("name"),
                                size = item.getLong("size"),
                                mimeType = item.getString("mimeType"),
                                checksum = item.optString("checksum", "")
                            )
                        )
                    }
                    Pair(senderName, fileList)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching manifest", e)
                null
            }
        }
    }

    suspend fun downloadFiles(
        host: String,
        port: Int,
        token: String,
        senderName: String,
        receiverName: String,
        files: List<SessionFileMeta>,
        duplicatePolicy: DuplicatePolicy = DuplicatePolicy.KEEP_BOTH,
        onFileDownloaded: ((fileName: String, targetFile: File) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        isCancelled = false
        isPaused = false

        val downloadDir = getDownloadDirectory()
        var overallSuccess = true

        files.forEachIndexed { index, fileMeta ->
            if (isCancelled || !coroutineContext.isActive) return@withContext false

            val targetFile = resolveDestinationFile(downloadDir, fileMeta.name, duplicatePolicy)
            if (targetFile == null) {
                // Cancelled due to duplicate policy
                return@withContext false
            }

            // Insert pending record in history
            val historyId = historyRepository.insertTransfer(
                TransferEntity(
                    fileId = fileMeta.id,
                    fileName = targetFile.name,
                    fileSize = fileMeta.size,
                    mimeType = fileMeta.mimeType,
                    direction = TransferDirection.RECEIVED,
                    peerName = senderName,
                    status = TransferStatus.IN_PROGRESS,
                    filePath = targetFile.absolutePath,
                    checksum = fileMeta.checksum
                )
            )

            val success = downloadSingleFile(
                host = host,
                port = port,
                token = token,
                fileMeta = fileMeta,
                targetFile = targetFile,
                fileIndex = index + 1,
                totalFiles = files.size
            )

            if (success) {
                // Verify checksum if provided
                var isIntegrityValid = true
                if (fileMeta.checksum.isNotBlank()) {
                    val computedSha = NetworkUtils.calculateSha256(targetFile.inputStream())
                    if (!computedSha.equals(fileMeta.checksum, ignoreCase = true)) {
                        isIntegrityValid = false
                        Log.e(TAG, "Checksum mismatch for ${targetFile.name}!")
                    }
                }

                if (isIntegrityValid) {
                    historyRepository.updateStatus(historyId, TransferStatus.COMPLETED)
                    withContext(Dispatchers.Main) {
                        onFileDownloaded?.invoke(targetFile.name, targetFile)
                    }
                } else {
                    overallSuccess = false
                    historyRepository.updateStatus(historyId, TransferStatus.FAILED, "Integrity check failed: file corrupted")
                }
            } else {
                overallSuccess = false
                val status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.FAILED
                historyRepository.updateStatus(historyId, status, "Download interrupted")
            }
        }

        _transferProgress.value = _transferProgress.value?.copy(
            isCompleted = overallSuccess,
            isFailed = !overallSuccess
        )

        overallSuccess
    }

    private suspend fun downloadSingleFile(
        host: String,
        port: Int,
        token: String,
        fileMeta: SessionFileMeta,
        targetFile: File,
        fileIndex: Int,
        totalFiles: Int
    ): Boolean {
        var startOffset = 0L
        if (targetFile.exists() && targetFile.length() < fileMeta.size) {
            startOffset = targetFile.length() // Resume partial download
        } else if (targetFile.exists() && targetFile.length() >= fileMeta.size) {
            targetFile.delete()
        }

        return try {
            val urlStr = "http://$host:$port/download?fileId=${fileMeta.id}&token=$token"
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 30000
                requestMethod = "GET"
                if (startOffset > 0) {
                    setRequestProperty("Range", "bytes=$startOffset-")
                }
            }

            val responseCode = connection.responseCode
            if (responseCode != 200 && responseCode != 206) {
                Log.e(TAG, "Unexpected response code: $responseCode")
                return false
            }

            val inputStream: InputStream = connection.inputStream
            val outputStream = FileOutputStream(targetFile, startOffset > 0)

            val buffer = ByteArray(64 * 1024)
            var bytesTransferred = startOffset
            var lastSampleTime = System.currentTimeMillis()
            var lastSampleBytes = bytesTransferred
            var speedBytesPerSec = 0L

            var bytesRead = 0
            while (!isCancelled && inputStream.read(buffer).also { bytesRead = it } != -1) {
                while (isPaused && !isCancelled) {
                    kotlinx.coroutines.delay(200)
                }

                outputStream.write(buffer, 0, bytesRead)
                bytesTransferred += bytesRead

                val now = System.currentTimeMillis()
                val delta = now - lastSampleTime
                if (delta >= 500) {
                    speedBytesPerSec = ((bytesTransferred - lastSampleBytes) * 1000) / delta
                    lastSampleTime = now
                    lastSampleBytes = bytesTransferred

                    val percent = if (fileMeta.size > 0) ((bytesTransferred * 100) / fileMeta.size).toInt() else 0
                    val remainingBytes = (fileMeta.size - bytesTransferred).coerceAtLeast(0)
                    val remainingSec = if (speedBytesPerSec > 0) remainingBytes / speedBytesPerSec else 0L

                    _transferProgress.value = ClientTransferProgress(
                        fileId = fileMeta.id,
                        fileName = targetFile.name,
                        currentFileIndex = fileIndex,
                        totalFiles = totalFiles,
                        bytesTransferred = bytesTransferred,
                        totalBytes = fileMeta.size,
                        progressPercent = percent,
                        speedBytesPerSec = speedBytesPerSec,
                        remainingSeconds = remainingSec
                    )
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            val success = bytesTransferred >= fileMeta.size
            _transferProgress.value = ClientTransferProgress(
                fileId = fileMeta.id,
                fileName = targetFile.name,
                currentFileIndex = fileIndex,
                totalFiles = totalFiles,
                bytesTransferred = bytesTransferred,
                totalBytes = fileMeta.size,
                progressPercent = if (fileMeta.size > 0) ((bytesTransferred * 100) / fileMeta.size).toInt() else 100,
                speedBytesPerSec = speedBytesPerSec,
                remainingSeconds = 0,
                isCompleted = success,
                isFailed = !success
            )

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading ${fileMeta.name}", e)
            _transferProgress.value = _transferProgress.value?.copy(
                isFailed = true,
                errorMessage = e.localizedMessage
            )
            false
        }
    }

    private fun resolveDestinationFile(dir: File, originalName: String, policy: DuplicatePolicy): File? {
        val candidate = File(dir, originalName)
        if (!candidate.exists()) return candidate

        return when (policy) {
            DuplicatePolicy.REPLACE -> {
                candidate.delete()
                candidate
            }
            DuplicatePolicy.CANCEL -> null
            DuplicatePolicy.KEEP_BOTH -> {
                val dotIndex = originalName.lastIndexOf('.')
                val base = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
                val ext = if (dotIndex > 0) originalName.substring(dotIndex) else ""

                var counter = 1
                var newFile: File
                do {
                    newFile = File(dir, "$base ($counter)$ext")
                    counter++
                } while (newFile.exists())
                newFile
            }
        }
    }

    private fun getDownloadDirectory(): File {
        val externalDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val syedFolder = File(externalDownloads, "SYED")
        if (!syedFolder.exists()) {
            syedFolder.mkdirs()
        }
        if (syedFolder.exists() && syedFolder.canWrite()) {
            return syedFolder
        }
        val appFolder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SYED")
        if (!appFolder.exists()) appFolder.mkdirs()
        return appFolder
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun cancel() {
        isCancelled = true
        _transferProgress.value = _transferProgress.value?.copy(
            isFailed = true,
            errorMessage = "Cancelled by user"
        )
    }
}
