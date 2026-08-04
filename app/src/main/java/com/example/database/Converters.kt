package com.example.database

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromClipboardType(value: ClipboardType): String {
        return value.name
    }

    @TypeConverter
    fun toClipboardType(value: String): ClipboardType {
        return try {
            ClipboardType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ClipboardType.TEXT
        }
    }
}
