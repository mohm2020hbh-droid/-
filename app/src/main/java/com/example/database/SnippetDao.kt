package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippet_items ORDER BY timestamp DESC")
    fun getAllSnippets(): Flow<List<SnippetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: SnippetEntity): Long

    @Update
    suspend fun updateSnippet(snippet: SnippetEntity)

    @Delete
    suspend fun deleteSnippet(snippet: SnippetEntity)

    @Query("DELETE FROM snippet_items WHERE id = :id")
    suspend fun deleteSnippetById(id: Long)

    @Query("SELECT * FROM snippet_items WHERE shortcut = :shortcut LIMIT 1")
    suspend fun getSnippetByShortcut(shortcut: String): SnippetEntity?
}
