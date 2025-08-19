package com.example.todomoji

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@Composable
fun DailyImageScreen(
    dateVm: DateViewModel,
    tasksVm: TasksViewModel,
    aiVm: AiViewModel,
    posterVm: DailyImageViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val date by dateVm.selected.collectAsState(initial = java.time.LocalDate.now())
    val tasks by tasksVm.tasks.collectAsState(initial = emptyList())
    val photosByTask by tasksVm.photos.collectAsState(initial = emptyMap())
    val aiState by aiVm.state.collectAsState()
    val posterState by posterVm.state.collectAsState()

    val todays = remember(tasks, date) { tasks.filter { it.due == date } }
    val ordered = remember(todays, aiState.orderedIds) {
        if (aiState.orderedIds.isEmpty()) todays
        else {
            val order: Map<String, Int> = aiState.orderedIds
                .withIndex()
                .associate { (idx, id) -> id.toString() to idx }

            // Sort tasks by that rank; unknown ids go to the end
            todays.sortedBy { task -> order[task.id.toString()] ?: Int.MAX_VALUE }
        }
    }
    val todaysPhotoUris: List<String> = remember(ordered, photosByTask) {
        ordered.flatMap { t ->
            (photosByTask[t.id] ?: emptyList()).map { p -> p.url }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Todomoji Poster — $date", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (aiState.caption.isBlank()) {
                        aiVm.rank(date, todays)
                    } else {
                        posterVm.generateWithOpenAI(ctx, date, ordered, aiState.caption, photoUris = todaysPhotoUris)
                    }
                },
                enabled = !posterState.loading
            ) { Text(if (posterState.loading) "Generating…" else "Generate") }

            OutlinedButton(
                onClick = {
                    posterVm.generateWithOpenAI(
                        ctx, date, ordered, aiState.caption,
                        seed = System.nanoTime(),
                        photoUris = todaysPhotoUris
                    )
                },
                enabled = !posterState.loading
            ) { Text("Regenerate") }

            OutlinedButton(
                onClick = {
                    posterState.uri?.let { sharePoster(ctx, it) }
                },
                enabled = posterState.uri != null && !posterState.loading
            ) { Text("Share") }

            TextButton(onClick = { posterVm.clear() }) { Text("Clear") }
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                posterState.loading -> CircularProgressIndicator()
                posterState.uri != null -> {
                    AsyncImage(
                        model = posterState.uri,
                        contentDescription = "Todomoji Poster",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                }
                posterState.error != null -> Text(posterState.error!!, color = MaterialTheme.colorScheme.error)
                else -> Text("No poster yet. Tap Generate to create one.")
            }
        }

        Spacer(Modifier.height(12.dp))

        val included = todaysPhotoUris
        if (included.isNotEmpty()) {
            Text("Included photos (${included.size}):", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                included.take(6).forEach { u ->
                    AsyncImage(model = u, contentDescription = null, modifier = Modifier.size(72.dp))
                }
            }
        }
    }
}

private fun sharePoster(ctx: android.content.Context, uri: android.net.Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(ctx.contentResolver, "todomoji_poster", uri)
    }
    ctx.startActivity(Intent.createChooser(intent, "Share Todomoji"))
}
