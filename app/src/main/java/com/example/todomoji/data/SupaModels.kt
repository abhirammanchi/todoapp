package com.example.todomoji.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

// --- Postgrest rows ---

@Serializable
data class TaskRow(
    val id: String,
    val user_id: String,
    val title: String,
    val completed: Boolean = false,
    val due: String,                 // ISO "yyyy-MM-dd"
    val start: String? = null,       // "HH:mm:ss" or null
    val end: String? = null,
    val priority: Int = 0,
    val note: String? = null
)

@Serializable
data class TaskRowInsert(
    val user_id: String,
    val title: String,
    val due: String,
    val completed: Boolean = false,
    val start: String? = null,
    val end: String? = null,
    val priority: Int = 0,
    val note: String? = null
)

@Serializable
data class TaskPhotoRow(
    val id: String,
    val user_id: String,
    val task_id: String,
    val storage_path: String
)

// --- Domain models your UI uses ---

data class Task(
    val id: String,                  // NOTE: now String (UUID)
    val userId: String,
    val title: String,
    val completed: Boolean,
    val due: LocalDate,
    val start: LocalTime?,
    val end: LocalTime?,
    val priority: Int,
    val note: String?,
    val shared: Boolean
)

data class TaskPhoto(
    val url: String                  // signed URL or local content Uri string
)

@kotlinx.serialization.Serializable
data class TaskCollaboratorRow(
    val task_id: String,
    val user_id: String
)

@kotlinx.serialization.Serializable
data class TaskRowInsertWithId(
    val id: String,
    val user_id: String,
    val title: String,
    val due: String
)

@Serializable
data class ProfileRow(
    val id: String,
    val email: String
)
// --- Mappers ---

fun TaskRow.toDomain(currentUserId: String, shared: Boolean = (user_id != currentUserId)): Task = Task(
    id = id,
    userId = user_id,
    title = title,
    completed = completed,
    due = LocalDate.parse(due),
    start = start?.let(LocalTime::parse),
    end = end?.let(LocalTime::parse),
    priority = priority ?: 0,
    note = note,
    shared = shared
)
