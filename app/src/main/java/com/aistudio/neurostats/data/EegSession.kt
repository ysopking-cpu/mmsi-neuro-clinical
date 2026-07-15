package com.aistudio.neurostats.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "eeg_sessions")
data class EegSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val durationSeconds: Int,
    val csvData: String,
    val label: String
)
