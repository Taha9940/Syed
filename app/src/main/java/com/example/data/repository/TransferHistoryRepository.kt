package com.example.data.repository

import com.example.data.local.TransferDao
import com.example.data.local.TransferDirection
import com.example.data.local.TransferEntity
import com.example.data.local.TransferStatus
import kotlinx.coroutines.flow.Flow

class TransferHistoryRepository(private val dao: TransferDao) {
    val allTransfers: Flow<List<TransferEntity>> = dao.getAllTransfers()
    val recentTransfers: Flow<List<TransferEntity>> = dao.getRecentTransfers(5)

    fun getTransfersByDirection(direction: TransferDirection): Flow<List<TransferEntity>> =
        dao.getTransfersByDirection(direction)

    suspend fun insertTransfer(entity: TransferEntity): Long = dao.insertTransfer(entity)

    suspend fun updateStatus(id: Long, status: TransferStatus, error: String? = null) =
        dao.updateStatus(id, status, error)

    suspend fun updateStatusAndPath(id: Long, status: TransferStatus, filePath: String?, error: String? = null) =
        dao.updateStatusAndPath(id, status, filePath, error)

    suspend fun deleteTransfer(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
