package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_items ORDER BY isPinned DESC, timestamp DESC")
    fun getAllItems(): Flow<List<ClipboardEntity>>

    @Query("SELECT * FROM clipboard_items WHERE id = :id")
    suspend fun getItemById(id: Long): ClipboardEntity?

    @Query("SELECT * FROM clipboard_items ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestItem(): ClipboardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ClipboardEntity): Long

    @Update
    suspend fun updateItem(item: ClipboardEntity)

    @Delete
    suspend fun deleteItem(item: ClipboardEntity)

    @Query("DELETE FROM clipboard_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM clipboard_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM clipboard_items WHERE isPinned = 0")
    suspend fun deleteUnpinned()

    @Query("DELETE FROM clipboard_items WHERE autoDeleteTime IS NOT NULL AND autoDeleteTime < :currentTime AND isPinned = 0")
    suspend fun deleteExpired(currentTime: Long)
}
