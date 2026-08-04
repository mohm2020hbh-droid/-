package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snippet_items")
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val content: String,
    val shortcut: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
