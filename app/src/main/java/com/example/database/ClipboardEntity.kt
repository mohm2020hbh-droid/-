package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ClipboardType {
    TEXT, LINK, IMAGE, CODE
}

@Entity(tableName = "clipboard_items")
data class ClipboardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: ClipboardType,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceApp: String? = null,
    val isPinned: Boolean = false,
    val autoDeleteTime: Long? = null,
    val size: Long = content.length.toLong()
)
