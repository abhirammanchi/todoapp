package com.example.todomoji

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.todomoji.data.SupabaseTaskRepository

class TasksVmFactory(private val userId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repo = SupabaseTaskRepository()
        @Suppress("UNCHECKED_CAST")
        return TasksViewModel(repo, userId) as T
    }
}