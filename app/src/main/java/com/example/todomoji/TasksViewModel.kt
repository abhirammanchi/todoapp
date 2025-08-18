package com.example.todomoji

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todomoji.data.SupabaseTaskRepository
import com.example.todomoji.data.Task
import com.example.todomoji.data.TaskPhoto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime


class TasksViewModel(
    private val repo: SupabaseTaskRepository,
    private val userId: String
) : ViewModel() {

    // All tasks for the user; filter by date in UI
    val tasks: StateFlow<List<Task>> =
        repo.tasksFlow(userId).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Per-task photos (signed URLs), cached in-memory
    private val _photos = MutableStateFlow<Map<String, List<TaskPhoto>>>(emptyMap())
    val photos: StateFlow<Map<String, List<TaskPhoto>>> = _photos.asStateFlow()

    fun observePhotos(taskId: String) {
        // attach once per task id
        if (_photos.value.containsKey(taskId)) return
        viewModelScope.launch {
            repo.photosFlow(taskId).collect { list ->
                _photos.update { old -> old + (taskId to list) }
            }
        }
    }

    fun addTask(title: String, due: LocalDate) = viewModelScope.launch {
        if (title.isNotBlank()) repo.addTask(title, due)
    }

    fun addTaskWithCollaborator(
        title: String,
        due: java.time.LocalDate,
        collaboratorEmail: String?
    ) = viewModelScope.launch {
        val taskId = repo.addTaskReturningId(title, due)
        if (!collaboratorEmail.isNullOrBlank()) {
            runCatching { repo.addCollaboratorByEmail(taskId, collaboratorEmail) }
                .onFailure { /* optionally expose a snackbar / state error */ }
        }
    }


    fun toggle(taskId: String, currentCompleted: Boolean) = viewModelScope.launch {
        repo.toggle(taskId, currentCompleted)
    }

    fun delete(taskId: String) = viewModelScope.launch { repo.delete(taskId) }

    fun addPhoto(taskId: String, localUri: Uri, ctx: Context) = viewModelScope.launch {
        val url = repo.addPhoto(taskId, localUri, ctx)
        // Optimistic update (append)
        val cur = _photos.value[taskId].orEmpty()
        _photos.update { it + (taskId to (cur + TaskPhoto(url))) }
    }
    fun addToTimelineForDate(date: LocalDate, id: String) = viewModelScope.launch {
        // simple default slot: 09:00–10:00
        repo.schedule(id, LocalTime.of(9, 0), LocalTime.of(10, 0))
    }

    fun dragMove(id: String, newStart: LocalTime) = viewModelScope.launch {
        // keep the same duration if we can guess it; fall back to 60m
        val current = tasks.value.firstOrNull { it.id == id }
        val durationMinutes = when {
            current?.start != null && current.end != null ->
                (java.time.Duration.between(current.start, current.end).toMinutes()).toInt().coerceAtLeast(15)
            else -> 60
        }
        repo.schedule(id, newStart, newStart.plusMinutes(durationMinutes.toLong()))
    }
    fun rename(id: String, title: String) = viewModelScope.launch { repo.rename(id, title) }
    fun setPriority(id: String, priority: Int) = viewModelScope.launch { repo.setPriority(id, priority) }
}
