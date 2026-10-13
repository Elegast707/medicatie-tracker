package nl.medicatietracker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.medicatietracker.MedApp
import nl.medicatietracker.data.Badge
import nl.medicatietracker.data.DUTCH
import nl.medicatietracker.data.History
import nl.medicatietracker.data.HistoryDay
import nl.medicatietracker.data.HistoryRow
import nl.medicatietracker.data.LogStatus
import nl.medicatietracker.data.RowStatus
import nl.medicatietracker.data.StreakInfo
import nl.medicatietracker.data.Streaks
import nl.medicatietracker.data.Summary
import nl.medicatietracker.data.doneLabel
import nl.medicatietracker.data.formatMinute
import nl.medicatietracker.data.formatTime
import nl.medicatietracker.data.label
import nl.medicatietracker.data.toLocalDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val historyDayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", DUTCH)
private val ranges = listOf(7 to "7 dagen", 30 to "30 dagen", 90 to "90 dagen")

@Composable
fun HistoryScreen(app: MedApp) {
    val context = LocalContext.current
    val now by rememberNow()
    val today = now.toLocalDate()
    var rangeDays by rememberSaveable { mutableIntStateOf(30) }
    var editing by remember { mutableStateOf<Pair<LocalDate, HistoryRow>?>(null) }

    val items by app.repository.allItems.collectAsStateWithLifecycle(emptyList())
    val logs by app.repository.allLogs.collectAsStateWithLifecycle(emptyList())

    val allDays = remember(items, logs, now) {
        History.build(items, logs, History.firstDay(items, logs) ?: today, today, now)
    }
    val shownDays = allDays.take(rangeDays).filter { it.rows.isNotEmpty() }
    val summary = remember(allDays, rangeDays) { History.summarize(allDays.take(rangeDays)) }
    val streak = remember(allDays) { Streaks.compute(allDays) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.appScope.launch {
            val (allItems, allLogs) = app.repository.exportData()
            val exportNow = LocalDateTime.now()
            val firstDay = History.firstDay(allItems, allLogs) ?: exportNow.toLocalDate()
            val csv = History.toCsv(History.build(allItems, allLogs, firstDay, exportNow.toLocalDate(), exportNow))
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
            }.isSuccess
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (ok) "Geschiedenis opgeslagen" else "Opslaan mislukt", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { ScreenTitle("Geschiedenis") }
                IconButton(
                    onClick = { exportLauncher.launch("medicatie-geschiedenis-$today.csv") },
                    modifier = Modifier.padding(end = 8.dp, top = 8.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Exporteren als CSV")
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ranges.forEach { (days, label) ->
                    FilterChip(selected = rangeDays == days, onClick = { rangeDays = days }, label = { Text(label) })
                }
            }
        }
        item { AchievementsCard(streak) }
        item { SummaryCard(summary) }
        if (shownDays.isEmpty()) {
            item {
                Text("Nog geen geschiedenis.", modifier = Modifier.padding(16.dp))
            }
        }
        items(shownDays, key = { it.date.toEpochDay() }) { day ->
            DayCard(day, onRowClick = { row -> editing = day.date to row })
        }
    }

    editing?.let { (date, row) ->
        EditLogDialog(
            date = date,
            row = row,
            onDismiss = { editing = null },
            onSet = { status ->
                app.appScope.launch { app.repository.setStatus(row.itemId, date.toEpochDay(), status) }
                editing = null
            },
            onClear = {
                app.appScope.launch { app.repository.clearStatus(row.itemId, date.toEpochDay()) }
                editing = null
            },
        )
    }
}

@Composable
private fun AchievementsCard(streak: StreakInfo) {
    Card(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("${streak.current}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(if (streak.current == 1) "dag op rij compleet" else "dagen op rij compleet", style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f)) {
                    Text("${streak.best}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("beste reeks ooit", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Streaks.BADGES.forEach { badge ->
                    BadgeTile(badge, earned = streak.best >= badge.days, current = streak.current, modifier = Modifier.weight(1f))
                }
            }
            val next = Streaks.BADGES.firstOrNull { it.days > streak.current }
            if (next != null) {
                val remaining = next.days - streak.current
                Text(
                    "Nog $remaining ${if (remaining == 1) "dag" else "dagen"} tot de badge '${next.label}'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, earned: Boolean, current: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (earned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (earned) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (earned) {
                Icon(Icons.Filled.Star, contentDescription = "Behaald", modifier = Modifier.size(22.dp))
            } else {
                Text("${minOf(current, badge.days)}/${badge.days}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Text(badge.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun SummaryCard(summary: Summary) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text("${summary.percentage}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("afgevinkt in deze periode", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${summary.done} gedaan · ${summary.skipped} overgeslagen · ${summary.missed} gemist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary.perItem.forEach { stats ->
                Column {
                    Row {
                        Text(stats.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${stats.done}/${stats.total} · ${stats.percentage}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(
                        progress = { stats.percentage / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCard(day: HistoryDay, onRowClick: (HistoryRow) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day.date.format(historyDayFormatter).replaceFirstChar { it.uppercase(DUTCH) },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${day.done}/${day.rows.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (day.done == day.rows.size) successColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            day.rows.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onRowClick(row) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusIcon(row.status, Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(formatMinute(row.minuteOfDay), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    Text(row.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(statusText(day.date, row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun statusText(date: LocalDate, row: HistoryRow): String {
    val loggedAt = row.loggedAt ?: return row.status.label(row.type)
    val label = row.status.label(row.type)
    return if (loggedAt.toLocalDate() == date) "$label ${loggedAt.formatTime()}" else "$label (achteraf)"
}

@Composable
private fun EditLogDialog(
    date: LocalDate,
    row: HistoryRow,
    onDismiss: () -> Unit,
    onSet: (LogStatus) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${row.name} · ${formatMinute(row.minuteOfDay)}") },
        text = {
            Column {
                Text(date.format(historyDayFormatter))
                Spacer(Modifier.size(8.dp))
                Text("Huidige status: ${row.status.label(row.type)}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = { onSet(LogStatus.GEDAAN) }) { Text("Markeer als ${row.type.doneLabel().lowercase(DUTCH)}") }
                TextButton(onClick = { onSet(LogStatus.OVERGESLAGEN) }) { Text("Markeer als overgeslagen") }
                if (row.status == RowStatus.GEDAAN || row.status == RowStatus.OVERGESLAGEN) {
                    TextButton(onClick = onClear) { Text("Registratie wissen") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Sluiten") } },
    )
}
