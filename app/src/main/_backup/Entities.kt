package com.example.todomoji.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks", indices = [Index("dueIso")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val completed: Boolean,
    val dueIso: String,
    val startIsoTime: String? = null, // "HH:mm"
    val endIsoTime: String? = null,
    val priority: Int = 0,
    val note: String? = null
)

@Entity(tableName = "task_photos", indices = [Index("taskId")])
data class TaskPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val uri: String
)
