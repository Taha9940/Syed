package com.example.data.model

import android.net.Uri

data class SharedFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val uriString: String,
    val checksum: String = "",
    val category: FileCategory = FileCategory.OTHER,
    val isApk: Boolean = false,
    val apkVersion: String? = null
)

enum class FileCategory(val displayName: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    APKS("APKs"),
    ARCHIVES("Archives"),
    OTHER("Other")
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val index = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, index.toDouble())
    return "%.1f %s".format(value, units[index])
}
