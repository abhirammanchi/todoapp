package com.example.todomoji

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.todomoji.data.SupabaseTaskRepository

class TasksVmFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TasksViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TasksViewModel(SupabaseTaskRepository()) as T
        }
        throw IllegalArgumentException("Unknown VM ${modelClass.name}")
    }
}
