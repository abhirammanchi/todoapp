package com.example.todomoji.repo

import com.example.todomoji.data.Task
import com.example.todomoji.data.TaskPhoto
import com.example.todomoji.data.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

class TaskRepository(private val dao: TaskDao) {
    val allTasks: Flow<List<Task>> = dao.allTasks().map { it.map { e -> e.toDomain() } }
    val allPhotosMap: Flow<Map<Long, List<TaskPhoto>>> = dao.allPhotos().map { list ->
        list.groupBy({ it.taskId }, { it.toDomain() })
    }

    suspend fun addTask(title: String, due: LocalDate) {
        dao.insertTask(TaskEntity(title = title.trim(), completed = false, dueIso = due.toString()))
    }
    suspend fun toggleComplete(taskId: Long) = dao.toggleTask(taskId)
    suspend fun deleteTask(taskId: Long) { dao.deletePhotosForTask(taskId); dao.deleteTask(taskId) }
    suspend fun addPhoto(taskId: Long, uri: String) { dao.insertPhoto(TaskPhotoEntity(taskId = taskId, uri = uri)) }

    suspend fun updateTitle(taskId: Long, title: String) = dao.updateTitle(taskId, title.trim())
    suspend fun updateNote(taskId: Long, note: String?) = dao.updateNote(taskId, note)
    suspend fun updatePriority(taskId: Long, priority: Int) = dao.updatePriority(taskId, priority)

    suspend fun schedule(taskId: Long, start: LocalTime?, end: LocalTime?) =
        dao.updateSchedule(taskId, start?.toString(), end?.toString())
}

private fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    completed = completed,
    due = LocalDate.parse(dueIso),
    start = startIsoTime?.let { LocalTime.parse(it) },
    end = endIsoTime?.let { LocalTime.parse(it) },
    priority = priority,
    note = note
)
private fun TaskPhotoEntity.toDomain() = TaskPhoto(id = id, taskId = taskId, uri = uri)
