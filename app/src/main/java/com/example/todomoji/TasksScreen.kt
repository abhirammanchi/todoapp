@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
package com.example.todomoji

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.DismissDirection
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.todomoji.data.Task
import kotlinx.coroutines.launch
import java.io.File
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType



@Composable
fun TasksScreen(
    vm: TasksViewModel,
    dateVm: DateViewModel,
    aiVm: AiViewModel,
    onSignOut: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val tasks by vm.tasks.collectAsState(initial = emptyList())
    val photosByTask by vm.photos.collectAsState(initial = emptyMap())
    val selectedDate by dateVm.selected.collectAsState()
    val fmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    // Filters
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var tab by remember { mutableIntStateOf(0) } // 0 Today, 1 All, 2 Completed


    var showAdd by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var collabEmail by remember { mutableStateOf("") }


    // Camera
    var pendingTaskId by remember { mutableStateOf<String?>(null) }
    var pendingOutputUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val taskId = pendingTaskId; val uri = pendingOutputUri
        if (ok && taskId != null && uri != null) vm.addPhoto(taskId, uri, ctx)
        pendingTaskId = null; pendingOutputUri = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val taskId = pendingTaskId
        if (granted && taskId != null) {
            val file = createImageFile(ctx)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            pendingOutputUri = uri; takePictureLauncher.launch(uri)
        } else { pendingTaskId = null; pendingOutputUri = null }
    }

    // Snackbar for undo delete
    val snackbarHostState = remember { SnackbarHostState() }
    var deleted: Task? by remember { mutableStateOf(null) }

    // Derived lists
    val todays = remember(tasks, selectedDate) { tasks.filter { it.due == selectedDate } }
    val filtered = remember(todays, tasks, tab, query) {
        val source = when (tab) {
            0 -> todays.filter { !it.completed }
            1 -> tasks.filter { !it.completed }
            else -> tasks.filter { it.completed }
        }
        if (query.text.isBlank()) source else source.filter { it.title.contains(query.text, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Todomoji — ${selectedDate.format(fmt)}") },
                actions = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier
                            .width(220.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    )
                }
            )
        },
        bottomBar = {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Today") })
                Tab(tab == 1, { tab = 1 }, text = { Text("All") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Completed") })
            }
        },
        floatingActionButton = {
            LargeFloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSignOut) { Text("Log out") }
            }
            // AI Rank header
            val aiState by aiVm.state.collectAsState()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically

            ) {
                Text(
                    if (aiState.caption.isNotBlank()) aiState.caption else "Plan your day",
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = { aiVm.rank(selectedDate, todays) }, enabled = !aiState.loading) {
                    Text(if (aiState.loading) "Thinking…" else "AI Rank")
                }
            }

            if (aiState.error != null) {
                Text(aiState.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }

            // List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(filtered, key = { it.id }) { task ->
                    // ① Start streaming this task's photos from Supabase
                    LaunchedEffect(task.id) { vm.observePhotos(task.id) }

                    // ② Our photo model now has `url` (not `uri`)
                    val photos = photosByTask[task.id].orEmpty().map { it.url }

                    DismissibleTaskRow(
                        task = task,
                        photos = photos,


                        // ③ toggle now needs current completed state
                        onToggle = { vm.toggleCompleted(task.id, task.completed) },

                        onDelete = {
                            val t = task; vm.deleteTask(t.id); deleted = t
                            scope.launch {
                                val res = snackbarHostState.showSnackbar("Task deleted", actionLabel = "Undo")
                                if (res == SnackbarResult.ActionPerformed) {
                                    // naive undo: re-add
                                    vm.addTask(t.title, t.due)
                                }
                            }
                        },

                        // Keep your permission flow the same.
                        // IMPORTANT: when your camera result returns a `Uri`, call:
                        // vm.addPhoto(task.id, capturedUri, ctx)
                        onAddPhoto = {
                            pendingTaskId = task.id
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },

                        onRename = { vm.rename(task.id, it) },
                        onPriority = { vm.setPriority(task.id, it) }
                    )
                }
            }
            if (showAdd) {
                AlertDialog(
                    onDismissRequest = { showAdd = false },
                    title = { Text("New task") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Task title") }
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = collabEmail,
                                onValueChange = { collabEmail = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Collaborator email (optional)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                vm.addTaskWithCollaborator(
                                    title = newTitle,
                                    due = selectedDate,                   // you already have this date state
                                    collaboratorEmail = collabEmail.ifBlank { null }
                                )
                                newTitle = ""
                                collabEmail = ""
                                showAdd = false
                            },
                            enabled = newTitle.isNotBlank()
                        ) { Text("Add") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAdd = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

private fun createImageFile(ctx: android.content.Context): File {
    val dir = File(ctx.cacheDir, "images"); if (!dir.exists()) dir.mkdirs()
    return File(dir, "${System.currentTimeMillis()}.jpg")
}

@Composable
private fun DismissibleTaskRow(
    task: Task,
    photos: List<String>,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onAddPhoto: () -> Unit,
    onRename: (String) -> Unit,
    onPriority: (Int) -> Unit
) {
    val state = rememberDismissState(
        initialValue = DismissValue.Default,
        confirmStateChange = { newValue ->
            when (newValue) {
                DismissValue.DismissedToStart -> { onDelete(); true } // allow removing the item
                DismissValue.DismissedToEnd   -> { onToggle(); false } // toggle but keep it in place
                else -> true
            }
        }
    )
    SwipeToDismiss(
        state = state,
        background = {},
        dismissContent = {
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
                        Spacer(Modifier.width(8.dp))
                        var title by remember(task.id) { mutableStateOf(task.title) }
                        OutlinedTextField(
                            value = title, onValueChange = {
                                title = it; onRename(it)
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(4.dp))
                        AssistChip(
                            onClick = { onPriority(if (task.priority == 1) 0 else 1) },
                            label = { Text(if (task.priority == 1) "High" else "Normal") }
                        )
                        IconButton(onClick = onAddPhoto) { Icon(Icons.Filled.CameraAlt, contentDescription = "Add photo") }
                        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                    if (photos.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            photos.take(5).forEach { uri ->
                                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
                            }
                        }
                    }
                    if (task.start != null && task.end != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(6.dp))
                            Text("${task.start}–${task.end}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    )
}
