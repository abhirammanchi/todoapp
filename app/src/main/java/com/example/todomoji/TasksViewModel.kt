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

class TasksViewModel(
    private val repo: SupabaseTaskRepository
) : ViewModel() {

    // All tasks for the user; filter by date in UI
    val tasks: StateFlow<List<Task>> =
        repo.tasksFlow().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
}
