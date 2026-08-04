package com.example.repositories

import com.example.database.SnippetDao
import com.example.database.SnippetEntity
import kotlinx.coroutines.flow.Flow

class SnippetRepository(private val snippetDao: SnippetDao) {

    val allSnippets: Flow<List<SnippetEntity>> = snippetDao.getAllSnippets()

    suspend fun insert(snippet: SnippetEntity): Long {
        return snippetDao.insertSnippet(snippet)
    }

    suspend fun update(snippet: SnippetEntity) {
        snippetDao.updateSnippet(snippet)
    }

    suspend fun delete(snippet: SnippetEntity) {
        snippetDao.deleteSnippet(snippet)
    }

    suspend fun deleteById(id: Long) {
        snippetDao.deleteSnippetById(id)
    }

    suspend fun getSnippetByShortcut(shortcut: String): SnippetEntity? {
        return snippetDao.getSnippetByShortcut(shortcut)
    }
}
