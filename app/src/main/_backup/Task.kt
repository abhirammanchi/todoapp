package com.example.todomoji.data

import java.time.LocalDate
import java.time.LocalTime

data class Task(
    val id: Long,
    val title: String,
    val completed: Boolean = false,
    val due: LocalDate,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val priority: Int = 0,      // 0=normal 1=high
    val note: String? = null
)
