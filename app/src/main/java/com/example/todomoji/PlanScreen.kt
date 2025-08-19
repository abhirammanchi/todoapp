package com.example.todomoji

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.example.todomoji.data.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*



@Composable
fun PlanScreen(dateVm: DateViewModel, tasksVm: TasksViewModel) {
    val date by dateVm.selected.collectAsState()
    var currentMonth by remember { mutableStateOf(YearMonth.from(date)) }
    val tasks by tasksVm.tasks.collectAsState()

    val todays = remember(tasks, date) { tasks.filter { it.due == date && !it.completed } }
    val backlog = remember(todays) { todays.filter { it.start == null || it.end == null } }
    val scheduled = remember(todays) { todays.filter { it.start != null && it.end != null } }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Month header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) { Text("Prev") }
            TextButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) { Text("Next") }
        }
        Spacer(Modifier.height(8.dp))
        CalendarRow(currentMonth = currentMonth, selected = date, onSelect = { dateVm.set(it) })

        Spacer(Modifier.height(12.dp))

        // Timeboxing two-pane
        Row(Modifier.fillMaxSize()) {
            // Backlog
            Column(Modifier.width(220.dp).fillMaxHeight().padding(end = 12.dp)) {
                Text("Backlog", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                backlog.forEach { t ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(t.title, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { tasksVm.addToTimelineForDate(taskId = t.id, date = date) }) {
                                    Text("Add to timeline")
                                }
                            }
                        }
                    }
                }
                if (backlog.isEmpty()) {
                    Text("Nothing to schedule. Nice!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Timeline (drag to reschedule)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                DayTimeline(
                    tasks = scheduled,
                    onDrag = { id, newStart -> tasksVm.dragMove(id, newStart) }
                )
            }
        }
    }
}

@Composable
private fun CalendarRow(currentMonth: YearMonth, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val days = remember(currentMonth) { buildMonthGrid(currentMonth) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(220.dp)
    ) {
        items(days) { day ->
            val isSelected = day == selected
            val isOtherMonth = day.month != currentMonth.month

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        when {
                            isSelected   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            isOtherMonth -> Color(0x12121212)
                            else         -> Color.Transparent
                        }
                    )
                    .clickable { onSelect(day) }  // ← simple tap handler
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    color = if (isOtherMonth)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }

    }
}

private fun buildMonthGrid(ym: YearMonth): List<LocalDate> {
    val first = ym.atDay(1)
    val daysInMonth = ym.lengthOfMonth()
    val firstDow = (first.dayOfWeek.value % 7)
    val days = mutableListOf<LocalDate>()
    repeat(firstDow) { i -> days += first.minusDays((firstDow - i).toLong()) }
    repeat(daysInMonth) { i -> days += first.plusDays(i.toLong()) }
    while (days.size % 7 != 0) days += days.last().plusDays(1)
    return days
}

@Composable
private fun DayTimeline(
    tasks: List<Task>,
    onDrag: (id: String, newStart: LocalTime) -> Unit
) {
    val slotHeightDp = 56.dp
    val density = LocalDensity.current
    val slotHeightPx = with(density) { slotHeightDp.toPx() }
    val startOfDay = LocalTime.MIN

    // Layout: hour grid with positioned blocks
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)) {
        // Hour grid
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            (0..23).forEach { h ->
                Row(Modifier.height(slotHeightDp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d:00".format(h), modifier = Modifier.width(56.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Divider(Modifier.height(1.dp).weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        }

        // Task blocks
        tasks.forEach { t ->
            val start = t.start ?: LocalTime.of(9, 0)
            val end = t.end ?: start.plusMinutes(60)
            val topPx = (((start.toSecondOfDay() / 60f) / 60f) * slotHeightPx)
            val heightPx = (((end.toSecondOfDay() - start.toSecondOfDay()) / 3600f) * slotHeightPx)

            var offsetY by remember(t.id) { mutableStateOf(0f) }

            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(80, (topPx + offsetY).toInt()) }
                    .width(240.dp)
                    .height(with(density) { heightPx.toDp() })
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
                    .pointerInput(t.id) {
                        detectDragGestures(
                            onDragEnd = {
                                val newMinutes = ((topPx + offsetY) / slotHeightPx * 60f).toInt().coerceIn(0, 23 * 60 + 59)
                                val newStart = startOfDay.plusMinutes(newMinutes.toLong())
                                onDrag(t.id, newStart)
                                offsetY = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            offsetY += dragAmount.y
                        }
                    }
                    .padding(8.dp)
            ) {
                Column {
                    Text(t.title, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("${start}–${end}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
