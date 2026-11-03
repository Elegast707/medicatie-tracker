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
    fun newTask() = TaskItem(name = "", startDay = maxOf(selectedDay, epochDay))

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item { ScreenTitle("Taken", today.format(taskDayFormatter).replaceFirstChar { it.uppercase(DUTCH) }) }
            item { TaskStreakCard(streak.current, streak.best, onOpenStars) }
            item {
                MonthCalendar(
                    month = YearMonth.parse(shownMonth),
                    today = today,
                    selected = selected,
                    tasks = allTasks,
                    logs = logs,
                    onMonthChange = { shownMonth = it.toString() },
                    onSelect = { selectedDay = it.toEpochDay() },
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (selected == today) "Vandaag" else selected.format(taskDayFormatter).replaceFirstChar { it.uppercase(DUTCH) },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
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
                    TodayTaskCard(
                        task = task,
                        log = dayLogs[task.id],
                        canTick = canTick,
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
private fun TodayTaskCard(task: TaskItem, log: TaskLog?, canTick: Boolean, onDone: () -> Unit, onUndo: () -> Unit) {
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

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", DUTCH)

/**
 * Maandkalender in pixelstijl. Elke dag laat zien hoeveel taken er staan en hoeveel er gedaan zijn:
 * groen als alles gedaan is, rood als er in het verleden iets is blijven liggen. Tik op een dag om de
 * taken van die dag te zien of er een toe te voegen.
 */
@Composable
private fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    tasks: List<TaskItem>,
    logs: List<TaskLog>,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val doneByDay = remember(logs) { logs.groupBy { it.epochDay }.mapValues { (_, l) -> l.map { it.taskId }.toSet() } }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val weeks = (offset + month.lengthOfMonth() + 6) / 7
    PixelCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelButton("<", onClick = { onMonthChange(month.minusMonths(1)) })
                Text(
                    month.format(monthFormatter).replaceFirstChar { it.uppercase(DUTCH) },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable {
                        // Tik op de maand om terug te gaan naar vandaag.
                        onMonthChange(YearMonth.from(today))
                        onSelect(today)
                    },
                )
                PixelButton(">", onClick = { onMonthChange(month.plusMonths(1)) })
            }
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        Weekdays.shortLabel(day),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            for (week in 0 until weeks) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (col in 0 until 7) {
                        val dayOfMonth = week * 7 + col - offset + 1
                        if (dayOfMonth < 1 || dayOfMonth > month.lengthOfMonth()) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val date = month.atDay(dayOfMonth)
                            val planned = TaskRules.tasksOn(tasks, date)
                            val done = doneByDay[date.toEpochDay()].orEmpty()
                            CalendarDay(
                                date = date,
                                total = planned.size,
                                done = planned.count { it.id in done },
                                isToday = date == today,
                                isSelected = date == selected,
                                isPast = date.isBefore(today),
                                onClick = { onSelect(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    total: Int,
    done: Int,
    isToday: Boolean,
    isSelected: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        isSelected -> scheme.primary
        total > 0 && done == total -> DoneGreen
        total > 0 && isPast -> scheme.errorContainer
        total > 0 -> scheme.primaryContainer
        else -> scheme.surface
    }
    val text = when {
        isSelected -> scheme.onPrimary
        total > 0 && done == total -> Color.White
        total > 0 && isPast -> scheme.onErrorContainer
        total > 0 -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val edge = if (isToday) scheme.primary else pixelEdge()
    val light = lerp(fill, Color.White, 0.35f)
    val shade = lerp(fill, pixelEdge(), 0.25f)
    val description = buildString {
        append(date.format(taskDayFormatter))
        if (total > 0) append(", $done van $total taken gedaan") else append(", geen taken")
        if (isToday) append(", vandaag")
    }
    Column(
        modifier
            .height(44.dp)
            .drawBehind { pixelFrame(fill, light, shade, (if (isToday) 3 else 2).dp.toPx(), edge) }
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = text, fontWeight = if (isToday) FontWeight.Bold else null)
        if (total > 0) {
            Text(if (done == total) "✓" else "$done/$total", style = MaterialTheme.typography.labelSmall, color = text)
        }
    }
}
