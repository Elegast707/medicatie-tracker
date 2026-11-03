package nl.medicatietracker.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.medicatietracker.MedApp
import nl.medicatietracker.R
import nl.medicatietracker.data.DUTCH
import nl.medicatietracker.data.TaskItem
import nl.medicatietracker.data.TaskLog
import nl.medicatietracker.data.TaskRecurrence
import nl.medicatietracker.data.TaskRules
import nl.medicatietracker.data.Weekdays
import nl.medicatietracker.data.formatTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val taskDayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", DUTCH)
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", DUTCH)

private const val DAY_MS = 86_400_000L

private fun LocalDate.label(today: LocalDate): String = when (this) {
    today -> "vandaag"
    today.plusDays(1) -> "morgen"
    else -> format(shortDateFormatter)
}

/**
 * Het tabblad Taken. Staat los van medicatie: eigen lijst, eigen reeks en eigen sterren. Wel verdien je
 * met elke afgevinkte taak eten voor het blobje.
 */
@Composable
fun TasksScreen(app: MedApp, onOpenStars: () -> Unit) {
    val context = LocalContext.current
    val now by rememberNow()
    val today = now.toLocalDate()
    val epochDay = today.toEpochDay()
    val tasks by app.taskRepository.tasks.collectAsStateWithLifecycle(emptyList())
    val allTasks by app.taskRepository.allTasks.collectAsStateWithLifecycle(emptyList())
    val logs by app.taskRepository.logs.collectAsStateWithLifecycle(emptyList())
    val streak = remember(allTasks, logs, now) { TaskRules.streak(allTasks, logs, now) }
    val todayTasks = remember(tasks, today) { TaskRules.tasksOn(tasks, today) }
    val todayLogs = logs.filter { it.epochDay == epochDay }.associateBy { it.taskId }
    val doneCount = todayTasks.count { it.id in todayLogs }
    // null = geen dialoog, taak met id 0 = nieuwe taak
    var editing by remember { mutableStateOf<TaskItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item { ScreenTitle("Taken", today.format(taskDayFormatter).replaceFirstChar { it.uppercase(DUTCH) }) }
            item { TaskStreakCard(streak.current, streak.best, onOpenStars) }
            if (todayTasks.isEmpty()) {
                item {
                    Text(
                        if (tasks.isEmpty()) {
                            "Nog geen taken. Tik op 'Taak toevoegen' voor een losse taak of een taak die terugkomt."
                        } else {
                            "Vandaag staan er geen taken. Een dag zonder taken breekt je reeks niet."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("$doneCount van ${todayTasks.size} gedaan", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(6.dp))
                        PixelProgressBar(progress = doneCount.toFloat() / todayTasks.size)
                    }
                }
                items(todayTasks, key = { "vandaag-${it.id}" }) { task ->
                    TodayTaskCard(
                        task = task,
                        log = todayLogs[task.id],
                        onDone = {
                            app.appScope.launch {
                                val food = app.taskRepository.setDone(task.id, epochDay)
                                if (food != null) {
                                    val petName = app.petRepository.ensurePet().displayName
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "+1 ${food.label.lowercase(DUTCH)} voor $petName!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onUndo = { app.appScope.launch { app.taskRepository.clearDone(task.id, epochDay) } },
                    )
                }
            }
            if (tasks.isNotEmpty()) {
                item {
                    Text(
                        "Alle taken",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                items(tasks, key = { "alle-${it.id}" }) { task ->
                    TaskListRow(task, today, onClick = { editing = task })
                }
            }
        }
        PixelButton(
            onClick = { editing = TaskItem(name = "", startDay = epochDay) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_px_add), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            PixelText("Taak toevoegen", color = Color.White)
        }
    }

    editing?.let { task ->
        EditTaskDialog(
            initial = task,
            today = today,
            onDismiss = { editing = null },
            onSave = { updated ->
                app.appScope.launch { app.taskRepository.save(updated) }
                editing = null
            },
            onDelete = {
                app.appScope.launch { app.taskRepository.delete(task) }
                editing = null
            },
        )
    }
}

@Composable
private fun TaskStreakCard(current: Int, best: Int, onOpenStars: () -> Unit) {
    PixelCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onOpenStars)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Takenreeks: $current ${if (current == 1) "dag" else "dagen"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Een dag telt als al je taken van die dag gedaan zijn. Beste reeks: $best. " +
                    "Deze reeks staat los van je medicatie. Tik voor je takensterren.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayTaskCard(task: TaskItem, log: TaskLog?, onDone: () -> Unit, onUndo: () -> Unit) {
    val container = if (log != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerHighest
    PixelCard(container = container, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.name, style = MaterialTheme.typography.titleMedium)
                    if (task.details.isNotBlank()) {
                        Text(task.details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (log != null) DoneCheck("${log.taskId}-${log.epochDay}-${log.doneAt}", log.doneAt)
            }
            Spacer(Modifier.size(8.dp))
            if (log == null) {
                PixelButton(onClick = onDone, fill = DoneGreen, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(painterResource(R.drawable.ic_px_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Gedaan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gedaan om ${log.doneAt.formatTime()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onUndo) { Text("Ongedaan maken") }
                }
            }
        }
    }
}

@Composable
private fun TaskListRow(task: TaskItem, today: LocalDate, onClick: () -> Unit) {
    val next = remember(task, today) { TaskRules.nextOccurrence(task, today) }
    PixelCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(task.name, style = MaterialTheme.typography.titleMedium)
            val parts = listOfNotNull(
                TaskRules.describe(task) { it.format(shortDateFormatter) },
                when {
                    next != null -> "volgende: ${next.label(today)}"
                    task.recurrence == TaskRecurrence.EENMALIG && task.startDay < today.toEpochDay() -> "voorbij"
                    else -> null
                },
                task.details.takeIf { it.isNotBlank() },
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun recurrenceLabel(recurrence: TaskRecurrence): String = when (recurrence) {
    TaskRecurrence.EENMALIG -> "Eenmalig"
    TaskRecurrence.DAGELIJKS -> "Elke dag"
    TaskRecurrence.WEEKDAGEN -> "Weekdagen"
    TaskRecurrence.INTERVAL -> "Elke X dagen"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditTaskDialog(
    initial: TaskItem,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (TaskItem) -> Unit,
    onDelete: () -> Unit,
) {
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var details by remember { mutableStateOf(initial.details) }
    var recurrence by remember { mutableStateOf(initial.recurrence) }
    var startDay by remember { mutableLongStateOf(initial.startDay) }
    // Nieuwe taak op weekdagen: begin met de dag van vandaag aangevinkt.
    var weekdays by remember { mutableIntStateOf(if (initial.weekdays == 0) Weekdays.bit(today.dayOfWeek) else initial.weekdays) }
    var intervalDays by remember { mutableIntStateOf(initial.intervalDays.coerceIn(2, MAX_INTERVAL)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && (recurrence != TaskRecurrence.WEEKDAGEN || weekdays != 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nieuwe taak" else "Taak wijzigen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Wat moet je doen?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Notitie (optioneel)") },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskRecurrence.entries.forEach { option ->
                        FilterChip(selected = recurrence == option, onClick = { recurrence = option }, label = { Text(recurrenceLabel(option)) })
                    }
                }
                val date = LocalDate.ofEpochDay(startDay)
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Text((if (recurrence == TaskRecurrence.EENMALIG) "Op " else "Vanaf ") + date.label(today))
                }
                when (recurrence) {
                    TaskRecurrence.WEEKDAGEN -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = Weekdays.contains(weekdays, day),
                                onClick = { weekdays = Weekdays.toggle(weekdays, day) },
                                label = { Text(Weekdays.shortLabel(day)) },
                            )
                        }
                    }
                    TaskRecurrence.INTERVAL -> Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelButton("-", onClick = { intervalDays = (intervalDays - 1).coerceAtLeast(2) }, enabled = intervalDays > 2)
                        Text(
                            "Elke $intervalDays dagen",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        PixelButton("+", onClick = { intervalDays = (intervalDays + 1).coerceAtMost(MAX_INTERVAL) }, enabled = intervalDays < MAX_INTERVAL)
                    }
                    else -> Unit
                }
                Text(
                    "Elke taak die je afvinkt levert eten op voor je blobje. Zijn al je taken van een dag gedaan, dan groeit je takenreeks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Verwijderen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            details = details.trim(),
                            recurrence = recurrence,
                            startDay = startDay,
                            weekdays = if (recurrence == TaskRecurrence.WEEKDAGEN) weekdays else 0,
                            intervalDays = if (recurrence == TaskRecurrence.INTERVAL) intervalDays else initial.intervalDays,
                        ),
                    )
                },
            ) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )

    if (showDatePicker) {
        // De datumkiezer rekent in UTC-middernacht; een dag kiezen in het verleden kan niet, behalve de huidige.
        val earliest = minOf(today.toEpochDay(), initial.startDay)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDay * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = Math.floorDiv(utcTimeMillis, DAY_MS) >= earliest
                override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.ofEpochDay(earliest).year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { startDay = Math.floorDiv(it, DAY_MS) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuleren") } },
        ) { DatePicker(state = state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("'${initial.name}' verwijderen?") },
            text = { Text("De taak komt niet meer terug. Eerdere dagen in je takenreeks blijven kloppen.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Verwijderen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren") } },
        )
    }
}

private const val MAX_INTERVAL = 60
