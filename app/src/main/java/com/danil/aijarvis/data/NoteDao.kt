package com.danil.aijarvis.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Insert
    suspend fun insert(note: Note): Long

    @Delete
    suspend fun delete(note: Note)

    @Query("UPDATE notes SET title = :title, summary = :summary, tasks = :tasks, tags = :tags WHERE id = :id")
    suspend fun applyAi(id: Long, title: String, summary: String, tasks: String, tags: String)
}