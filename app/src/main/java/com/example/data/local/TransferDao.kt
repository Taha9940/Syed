package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer_history WHERE direction = :direction ORDER BY timestamp DESC")
    fun getTransfersByDirection(direction: TransferDirection): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransfers(limit: Int = 5): Flow<List<TransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(entity: TransferEntity): Long

    @Update
    suspend fun updateTransfer(entity: TransferEntity)

    @Query("UPDATE transfer_history SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TransferStatus, error: String? = null)

    @Query("UPDATE transfer_history SET status = :status, filePath = :filePath, errorMessage = :error WHERE id = :id")
    suspend fun updateStatusAndPath(id: Long, status: TransferStatus, filePath: String?, error: String? = null)

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAll()
}
