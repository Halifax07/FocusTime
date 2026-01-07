package com.example.delayme.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AppType {
    WHITE_LIST, // Necessary
    BLACK_LIST, // Fragmented
    UNLISTED    // Auto-detect
}

enum class TimeCategory {
    NECESSARY, // Focus
    FRAGMENTED, // Distraction
    LIFE,      // Utility/Life
    REST       // Screen Off
}

@Entity(tableName = "app_configs")
data class AppConfig(
    @PrimaryKey val packageName: String,
    val appName: String,
    val type: AppType
)

data class TimeSegment(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val packageName: String?, // Null for Rest/Screen Off
    val category: TimeCategory,
    val durationMinutes: Long
)
