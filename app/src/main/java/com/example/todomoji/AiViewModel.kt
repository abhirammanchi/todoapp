package com.example.todomoji

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todomoji.ai.AiService
import com.example.todomoji.data.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AiUiState(
    val loading: Boolean = false,
    val caption: String = "",
    val orderedIds: List<Long> = emptyList(),
    val error: String? = null
)

class AiViewModel(
    private val service: AiService
) : ViewModel() {

    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state

    fun rank(date: LocalDate, tasks: List<Task>) {
        if (tasks.isEmpty()) {
            _state.value = AiUiState()
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val (ordered, caption) = service.rankTasks(date, tasks)  // <-- updated call
                _state.value = AiUiState(
                    loading = false,
                    caption = caption,
                    orderedIds = ordered,
                    error = null
                )
            } catch (t: Throwable) {
                _state.value = AiUiState(loading = false, error = t.message ?: "AI error")
            }
        }
    }

    fun clear() {
        _state.value = AiUiState()
    }
}
