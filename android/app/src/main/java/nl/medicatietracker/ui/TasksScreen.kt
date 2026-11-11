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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import nl.medicatietracker.data.History
import nl.medicatietracker.data.TaskStreak
import nl.medicatietracker.data.TaskStreaks
import nl.medicatietracker.data.formatMinute
import nl.medicatietracker.data.TaskItem
import nl.medicatietracker.data.TaskLog
import nl.medicatietracker.data.TaskRecurrence
import nl.medicatietracker.data.TaskRules
import nl.medicatietracker.data.Weekdays
import nl.medicatietracker.data.formatTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

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
    var rangeDays by rememberSaveable { mutableIntStateOf(30) }
    val summary = remember(allTasks, logs, now, rangeDays) {
        History.summarize(TaskRules.history(allTasks, logs, today.minusDays(rangeDays - 1L), today, now))
    }
    // De dag die in de kalender is aangetikt; standaard vandaag.
    var selectedDay by rememberSaveable { mutableLongStateOf(epochDay) }
    var shownMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val selected = LocalDate.ofEpochDay(selectedDay)
    // Ook verwijderde taken, zodat eerdere dagen laten zien wat er toen stond.
    val dayTasks = remember(allTasks, selectedDay) { TaskRules.tasksOn(allTasks, selected) }
    val dayLogs = logs.filter { it.epochDay == selectedDay }.associateBy { it.taskId }
    val doneCount = dayTasks.count { it.id in dayLogs }
    val canTick = !selected.isAfter(today)
    // null = geen dialoog, taak met id 0 = nieuwe taak
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var removing by remember { mutableStateOf<TaskItem?>(null) }
    fun newTask() = TaskItem(name = "", startDay = maxOf(selectedDay, epochDay))

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item { ScreenTitle("Taken", today.format(trackerDayFormatter).replaceFirstChar { it.uppercase(DUTCH) }) }
            item { RangeChips(rangeDays) { rangeDays = it } }
            item { TaskAchievements(streak, onOpenStars) }
            item { SummaryCard(summary, showSkipped = false) }
            item {
                val doneByDay = remember(logs) { logs.groupBy { it.epochDay }.mapValues { (_, l) -> l.map { it.taskId }.toSet() } }
                MonthCalendar(
                    month = YearMonth.parse(shownMonth),
                    today = today,
                    selected = selected,
                    countFor = { date ->
                        val planned = TaskRules.tasksOn(allTasks, date)
                        val done = doneByDay[date.toEpochDay()].orEmpty()
                        DayCount(planned.count { it.id in done }, planned.size)
                    },
                    noun = "taken",
                    onMonthChange = { shownMonth = it.toString() },
                    onSelect = { selectedDay = it.toEpochDay() },
                )
            }
            item {
                SelectedDayHeader(selected, today) {
                    if (!selected.isBefore(today)) {
                        PixelButton(onClick = { editing = newTask() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                            Icon(painterResource(R.drawable.ic_px_add), contentDescription = "Taak op deze dag toevoegen", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (dayTasks.isEmpty()) {
                item {
                    Text(
                        when {
                            tasks.isEmpty() -> "Nog geen taken. Tik op een dag in de kalender en dan op + voor een losse taak of een taak die terugkomt."
                            selected.isBefore(today) -> "Op deze dag stonden geen taken."
                            else -> "Op deze dag staan geen taken. Een dag zonder taken breekt je reeks niet."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("$doneCount van ${dayTasks.size} gedaan", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.size(6.dp))
                        PixelProgressBar(progress = doneCount.toFloat() / dayTasks.size)
                    }
                }
                items(dayTasks, key = { "dag-${it.id}" }) { task ->
                    TaskCard(
                        task = task,
                        log = dayLogs[task.id],
                        canTick = canTick,
                        overdue = selected == today && now.hour * 60 + now.minute >= task.minuteOfDay,
                        onDone = {
                            val day = selectedDay
                            app.appScope.launch {
                                val food = app.taskRepository.setDone(task.id, day)
                                if (food != null) {
                                    val petName = app.petRepository.ensurePet().displayName
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "+1 ${food.label.lowercase(DUTCH)} voor $petName!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onUndo = {
                            val day = selectedDay
                            app.appScope.launch { app.taskRepository.clearDone(task.id, day) }
                        },
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
            // Eerder verwijderde taken staan nog in de kalender; hier haal je ze er helemaal uit.
            val activeNames = tasks.map { it.name }.toSet()
            val removed = allTasks.filter { it.deletedAt != null && it.name !in activeNames }.distinctBy { it.name }
            if (removed.isNotEmpty()) {
                item {
                    Column {
                    Text(
                        "Verwijderde taken",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                    Text(
                        "Deze komen niet meer terug, maar staan nog in eerdere dagen. Tik op de prullenbak om ze er helemaal uit te halen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    }
                }
                items(removed, key = { "weg-${it.id}" }) { task ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(task.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { removing = task }) {
                            Icon(painterResource(R.drawable.ic_px_trash), contentDescription = "'${task.name}' helemaal verwijderen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        PixelButton(
            onClick = { editing = newTask() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_px_add), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            PixelText("Taak toevoegen", color = Color.White)
        }
    }

    removing?.let { task ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("'${task.name}' helemaal verwijderen?") },
            text = { Text("De taak verdwijnt ook uit eerdere dagen, de kalender en de meter. Je reeks wordt opnieuw berekend.") },
            confirmButton = {
                TextButton(onClick = {
                    app.appScope.launch { app.taskRepository.deleteCompletely(task) }
                    removing = null
                }) { Text("Verwijderen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Annuleren") } },
        )
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
            onDelete = { completely ->
                app.appScope.launch {
                    if (completely) app.taskRepository.deleteCompletely(task) else app.taskRepository.delete(task)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun TaskAchievements(streak: TaskStreak, onOpenStars: () -> Unit) {
    val next = TaskStreaks.BADGES.firstOrNull { !it.earned(streak) }
    AchievementsCard(
        current = streak.weeks,
        currentLabel = if (streak.weeks == 1) "week op rij compleet" else "weken op rij compleet",
        best = streak.bestWeeks,
        bestLabel = "beste reeks in weken",
        badges = TaskStreaks.BADGES.map { BadgeView(it.label, it.earned(streak), it.progress(streak), it.tier.medal) },
        hint = buildString {
            if (streak.weekTotal > 0) {
                append("Deze week ${streak.weekDone} van ${streak.weekTotal} taken gedaan. ")
            }
            append("Een week (ma t/m zo) of maand is compleet als alle taken die erin staan gedaan zijn.")
            next?.let { append(" Volgende badge: '${it.label}'.") }
        },
        onClick = onOpenStars,
    )
}

/** Een taak om af te vinken, net als een medicijn op Vandaag: tijdstip, naam en een grote groene knop. */
@Composable
internal fun TaskCard(task: TaskItem, log: TaskLog?, canTick: Boolean, overdue: Boolean, onDone: () -> Unit, onUndo: () -> Unit) {
    val container = when {
        log != null -> MaterialTheme.colorScheme.surfaceVariant
        overdue && canTick -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    PixelCard(container = container, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatMinute(task.minuteOfDay), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.name, style = MaterialTheme.typography.titleMedium)
                    if (task.details.isNotBlank()) {
                        Text(task.details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (log != null) DoneCheck("${log.taskId}-${log.epochDay}-${log.doneAt}", log.doneAt)
            }
            Spacer(Modifier.size(8.dp))
            if (log == null && !canTick) {
                Text("Gepland", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (log == null) {
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
                formatMinute(task.minuteOfDay),
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
    onDelete: (completely: Boolean) -> Unit,
) {
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var details by remember { mutableStateOf(initial.details) }
    var recurrence by remember { mutableStateOf(initial.recurrence) }
    var startDay by remember { mutableLongStateOf(initial.startDay) }
    // Nieuwe taak op weekdagen: begin met de dag van vandaag aangevinkt.
    var weekdays by remember { mutableIntStateOf(if (initial.weekdays == 0) Weekdays.bit(today.dayOfWeek) else initial.weekdays) }
    var intervalDays by remember { mutableIntStateOf(initial.intervalDays.coerceIn(2, MAX_INTERVAL)) }
    var minuteOfDay by remember { mutableIntStateOf(initial.minuteOfDay) }
    var showTimePicker by remember { mutableStateOf(false) }
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
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Text("Om ${formatMinute(minuteOfDay)}")
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
                    "Op dit tijdstip krijg je een melding, en daarna elk uur opnieuw tot je de taak afvinkt. " +
                        "Elke taak die je afvinkt levert eten op voor je blobje. Doe je alle taken van een week, dan groeit je takenreeks.",
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
                            minuteOfDay = minuteOfDay,
                        ),
                    )
                },
            ) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Tijdstip") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    minuteOfDay = state.hour * 60 + state.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Annuleren") } },
        )
    }

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
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vanaf vandaag: de taak komt niet meer terug, maar eerdere dagen blijven zoals ze waren.")
                    Text("Helemaal: de taak verdwijnt ook uit eerdere dagen, de kalender en de meter. Je reeks wordt opnieuw berekend.")
                    TextButton(onClick = { onDelete(false) }) { Text("Vanaf vandaag verwijderen") }
                    TextButton(onClick = { onDelete(true) }) { Text("Helemaal verwijderen", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren") } },
        )
    }
}

private const val MAX_INTERVAL = 60

