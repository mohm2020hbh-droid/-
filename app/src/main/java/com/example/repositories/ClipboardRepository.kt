package com.example.repositories

import com.example.database.ClipboardDao
import com.example.database.ClipboardEntity
import com.example.database.ClipboardType
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class ClipboardRepository(private val clipboardDao: ClipboardDao) {

    val allItems: Flow<List<ClipboardEntity>> = clipboardDao.getAllItems()

    suspend fun getLatestItem(): ClipboardEntity? {
        return clipboardDao.getLatestItem()
    }

    suspend fun insertItem(content: String, type: ClipboardType, sourceApp: String? = null, retentionDays: Int = 30): Boolean {
        // Prevent empty content
        if (content.trim().isEmpty()) return false

        // Check size limit: limit items to 5MB (roughly 5,000,000 chars)
        val sizeLimit = 5 * 1024 * 1024
        if (content.length > sizeLimit) {
            // Prevent oversized items
            return false
        }

        // Prevent immediate sequential duplicate
        val latest = clipboardDao.getLatestItem()
        if (latest != null && latest.content == content && latest.type == type) {
            // Sequential duplicate detected: just update its timestamp to bring it to top
            val updated = latest.copy(timestamp = System.currentTimeMillis())
            clipboardDao.updateItem(updated)
            return true
        }

        val autoDeleteTime = if (retentionDays > 0) {
            System.currentTimeMillis() + (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        } else {
            null
        }

        val entity = ClipboardEntity(
            content = content,
            type = type,
            sourceApp = sourceApp,
            autoDeleteTime = autoDeleteTime,
            size = content.length.toLong()
        )
        clipboardDao.insertItem(entity)
        return true
    }

    suspend fun updateItem(item: ClipboardEntity) {
        clipboardDao.updateItem(item)
    }

    suspend fun togglePin(item: ClipboardEntity) {
        clipboardDao.updateItem(item.copy(isPinned = !item.isPinned))
    }

    suspend fun deleteItem(item: ClipboardEntity) {
        clipboardDao.deleteItem(item)
    }

    suspend fun deleteById(id: Long) {
        clipboardDao.deleteItemById(id)
    }

    suspend fun deleteAll() {
        clipboardDao.deleteAllItems()
    }

    suspend fun deleteUnpinned() {
        clipboardDao.deleteUnpinned()
    }

    suspend fun cleanExpired() {
        clipboardDao.deleteExpired(System.currentTimeMillis())
    }
}
