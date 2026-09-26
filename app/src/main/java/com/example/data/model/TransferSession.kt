package com.example.data.model

data class UserProfile(
    val displayName: String = "Syed User",
    val photoUri: String? = null,
    val deviceName: String = android.os.Build.MODEL ?: "Android Device"
) {
    val initials: String
        get() {
            val parts = displayName.trim().split("\\s+".toRegex())
            return if (parts.size >= 2) {
                "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
            } else if (displayName.isNotBlank()) {
                displayName.take(2).uppercase()
            } else {
                "SY"
            }
        }
}

data class PeerDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String = "",
    val status: String = "Available",
    val lastSeen: Long = System.currentTimeMillis()
)

data class TransferSession(
    val sessionId: String,
    val token: String,
    val host: String,
    val port: Int,
    val senderName: String,
    val files: List<SessionFileMeta>,
    val expiresAt: Long,
    val isGroup: Boolean = false,
    val groupName: String = ""
)

data class SessionFileMeta(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val checksum: String = ""
)

data class AppSettings(
    val defaultDownloadFolder: String = "Download/SYED",
    val autoAccept: Boolean = false,
    val connectionTimeoutSeconds: Int = 30,
    val transferConfirmation: Boolean = true,
    val keepTransferHistory: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}
