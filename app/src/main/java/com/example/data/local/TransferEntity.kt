package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransferDirection {
    SENT,
    RECEIVED
}

enum class TransferStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "transfer_history")
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val direction: TransferDirection,
    val peerName: String,
    val status: TransferStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val uriString: String? = null,
    val checksum: String = "",
    val errorMessage: String? = null
)
