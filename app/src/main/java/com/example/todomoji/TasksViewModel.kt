package com.example.todomoji

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todomoji.data.SupabaseTaskRepository
import com.example.todomoji.data.Task
import com.example.todomoji.data.TaskPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class TasksViewModel(
    private val repo: SupabaseTaskRepository,
    private val userId: String
) : ViewModel() {

    // Stream of tasks (owned + shared) keyed by user
    val tasks = repo.tasksFlow(userId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Photos for a task
    fun photos(taskId: String): Flow<List<TaskPhoto>> = repo.photosFlow(taskId)

    /** Plain add (no collaborator) */
    fun addTask(title: String, due: LocalDate) = viewModelScope.launch {
        repo.addTask(title, due)
    }

    /** Add with optional collaborator (requires step 3 repo methods) */
    fun addTaskWithCollaborator(title: String, due: LocalDate, collaboratorEmail: String?) =
        viewModelScope.launch {
            if (collaboratorEmail.isNullOrBlank()) {
                repo.addTask(title, due)
            } else {
                val id = repo.addTaskReturningId(title, due)
                repo.addCollaboratorByEmail(id, collaboratorEmail)
            }
        }

    fun toggleCompleted(id: String, current: Boolean) = viewModelScope.launch {
        repo.toggle(id, current)
    }

    fun deleteTask(id: String) = viewModelScope.launch {
        repo.delete(id)
    }

    fun addPhoto(taskId: String, uri: Uri, ctx: Context) = viewModelScope.launch {
        repo.addPhoto(taskId, uri, ctx)
    }

    fun schedule(id: String, start: LocalTime?, end: LocalTime?) = viewModelScope.launch {
        repo.schedule(id, start, end)
    }

    fun rename(id: String, title: String) = viewModelScope.launch {
        repo.rename(id, title)
    }

    fun setPriority(id: String, priority: Int) = viewModelScope.launch {
        repo.setPriority(id, priority)
    }
}
