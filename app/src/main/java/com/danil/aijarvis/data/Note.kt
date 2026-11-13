package com.danil.aijarvis.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val rawText: String,
    val title: String? = null,
    val summary: String? = null,
    val tasks: String? = null,
    val tags: String? = null
)

