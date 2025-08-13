package com.example.todomoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(dateVm: DateViewModel) { // <-- injected from TodomojiApp
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val selectedDate by dateVm.selected.collectAsState()
    val days = remember(currentMonth) { buildMonthGrid(currentMonth) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) { Text("Prev") }
            TextButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) { Text("Next") }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(it, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(days.size) { idx ->
                val day = days[idx]
                val isSelected = day == selectedDate
                val isOtherMonth = day.month != currentMonth.month

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                isOtherMonth -> Color(0x18181818)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { dateVm.set(day) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        fontWeight = if (isSelected) FontWeight.Bold else null,
                        color = if (isOtherMonth) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Selected: $selectedDate")
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