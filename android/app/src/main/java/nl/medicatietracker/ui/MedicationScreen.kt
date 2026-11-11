package nl.medicatietracker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.medicatietracker.MedApp
import nl.medicatietracker.R
import nl.medicatietracker.data.DUTCH
import nl.medicatietracker.data.History
import nl.medicatietracker.data.HistoryDay
import nl.medicatietracker.data.HistoryRow
import nl.medicatietracker.data.LogStatus
import nl.medicatietracker.data.RowStatus
import nl.medicatietracker.data.StreakInfo
import nl.medicatietracker.data.Streaks
import nl.medicatietracker.data.doneLabel
import nl.medicatietracker.data.formatMinute
import nl.medicatietracker.data.formatTime
import nl.medicatietracker.data.label
import nl.medicatietracker.data.toLocalDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val historyDayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", DUTCH)

/**
 * De pagina Medicijnen (voorheen Geschiedenis). Ziet er hetzelfde uit als Taken: reeks en badges, de
 * meter, een maandkalender en de lijst van de gekozen dag. Helemaal onderaan staan je medicijnen en
 * hun meldingen.
 */
@Composable
fun MedicationScreen(app: MedApp, onOpenStars: () -> Unit) {
    val context = LocalContext.current
    val now by rememberNow()
    val today = now.toLocalDate()
    var rangeDays by rememberSaveable { mutableIntStateOf(30) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var shownMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var editing by remember { mutableStateOf<Pair<LocalDate, HistoryRow>?>(null) }
    val selected = LocalDate.ofEpochDay(selectedDay)
    val month = YearMonth.parse(shownMonth)

    val items by app.repository.allItems.collectAsStateWithLifecycle(emptyList())
    val logs by app.repository.allLogs.collectAsStateWithLifecycle(emptyList())

    val allDays = remember(items, logs, now) {
        History.build(items, logs, History.firstDay(items, logs) ?: today, today, now)
    }
    val summary = remember(allDays, rangeDays) { History.summarize(allDays.take(rangeDays)) }
    val streak = remember(allDays) { Streaks.compute(allDays) }
    // De kalender laat ook geplande dagen in de toekomst zien.
    val monthDays = remember(items, logs, now, shownMonth) {
        History.build(items, logs, month.atDay(1), month.atEndOfMonth(), now).associateBy { it.date }
    }
    val selectedRows = remember(items, logs, now, selectedDay) {
        History.build(items, logs, selected, selected, now).single().rows
    }

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
                Column(Modifier.weight(1f)) {
                    ScreenTitle("Medicijnen", today.format(trackerDayFormatter).replaceFirstChar { it.uppercase(DUTCH) })
                }
                IconButton(
                    onClick = { exportLauncher.launch("medicatie-geschiedenis-$today.csv") },
                    modifier = Modifier.padding(end = 8.dp, top = 8.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_px_export), contentDescription = "Exporteren als CSV", modifier = Modifier.size(24.dp))
                }
            }
        }
        item { RangeChips(rangeDays) { rangeDays = it } }
        item { MedicationAchievements(streak, onOpenStars) }
        item { SummaryCard(summary) }
        item {
            MonthCalendar(
                month = month,
                today = today,
                selected = selected,
                countFor = { date ->
                    val rows = monthDays[date]?.rows.orEmpty()
                    DayCount(rows.count { it.countsForStreak }, rows.size)
                },
                noun = "medicijnen",
                onMonthChange = { shownMonth = it.toString() },
                onSelect = { selectedDay = it.toEpochDay() },
            )
        }
        item { SelectedDayHeader(selected, today) }
        item {
            if (selectedRows.isEmpty()) {
                Text(
                    "Op deze dag stonden geen medicijnen.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                DayCard(
                    HistoryDay(selected, selectedRows),
                    // Een dag in de toekomst kun je nog niet afvinken.
                    onRowClick = { row -> if (!selected.isAfter(today)) editing = selected to row },
                )
            }
        }
        item { MedicationSettings(app) }
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
private fun MedicationAchievements(streak: StreakInfo, onOpenStars: () -> Unit) {
    val next = Streaks.BADGES.firstOrNull { it.days > streak.current }
    AchievementsCard(
        current = streak.current,
        currentLabel = if (streak.current == 1) "dag op rij compleet" else "dagen op rij compleet",
        best = streak.best,
        bestLabel = "beste reeks ooit",
        badges = Streaks.BADGES.map { BadgeView(it.label, streak.best >= it.days, "${minOf(streak.current, it.days)}/${it.days}") },
        hint = next?.let {
            val remaining = it.days - streak.current
            "Nog $remaining ${if (remaining == 1) "dag" else "dagen"} tot de badge '${it.label}'."
        },
        onClick = onOpenStars,
    )
}

@Composable
private fun DayCard(day: HistoryDay, onRowClick: (HistoryRow) -> Unit) {
    PixelCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
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
                    StatusIcon(row.status, Modifier.size(24.dp))
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
