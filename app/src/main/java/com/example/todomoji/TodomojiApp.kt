package com.example.todomoji

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.todomoji.ai.AiService
import com.example.todomoji.TasksVmFactory

@Composable
fun TodomojiApp(onSignOut: () -> Unit = {}) {
    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route

    val dateVm: DateViewModel = viewModel()
    val tasksVm: TasksViewModel = viewModel(factory = TasksVmFactory())
    val aiVm: AiViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AiViewModel(AiService(apiKey = BuildConfig.OPENAI_API_KEY.ifBlank { null })) as T
            }
        }
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = current == "tasks",
                    onClick = { nav.navigate("tasks") { launchSingleTop = true; restoreState = true; popUpTo(nav.graph.startDestinationId) { saveState = true } } },
                    label = { Text("Tasks") },
                    icon = { Icon(Icons.Default.TaskAlt, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = current == "calendar",
                    onClick = { nav.navigate("calendar") { launchSingleTop = true; restoreState = true; popUpTo(nav.graph.startDestinationId) { saveState = true } } },
                    label = { Text("Plan") },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = current == "poster",
                    onClick = { nav.navigate("poster") { launchSingleTop = true; restoreState = true; popUpTo(nav.graph.startDestinationId) { saveState = true } } },
                    label = { Text("Poster") },
                    icon = { Icon(Icons.Default.Image, contentDescription = null) }
                )
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "tasks", modifier = Modifier.padding(padding)) {
            composable("tasks") { TasksScreen(vm = tasksVm, dateVm = dateVm, aiVm = aiVm, onSignOut = onSignOut) }
            composable("calendar") { PlanScreen(dateVm = dateVm, tasksVm = tasksVm) }
            composable("poster") { DailyImageScreen(dateVm = dateVm, tasksVm = tasksVm, aiVm = aiVm) }
        }
    }
}
