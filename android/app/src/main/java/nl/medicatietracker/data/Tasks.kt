package nl.medicatietracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Hoe vaak een taak terugkomt. */
enum class TaskRecurrence { EENMALIG, DAGELIJKS, WEEKDAGEN, INTERVAL }

/**
 * Een taak uit het tabblad Taken. Staat los van medicatie: eigen lijst, eigen reeks en eigen sterren.
 */
@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Notitie, optioneel. */
    val details: String = "",
    val recurrence: TaskRecurrence = TaskRecurrence.EENMALIG,
    /** Eerste dag (epochDay). Bij een eenmalige taak de dag waarop hij gedaan moet worden. */
    val startDay: Long,
    /** Bij [TaskRecurrence.WEEKDAGEN]: bit 0 = maandag ... bit 6 = zondag. */
    val weekdays: Int = 0,
    /** Bij [TaskRecurrence.INTERVAL]: elke zoveel dagen, gerekend vanaf [startDay]. */
    val intervalDays: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    /** Gezet bij verwijderen, zodat eerdere dagen in de reeks blijven kloppen. */
    val deletedAt: Long? = null,
) {
    /** Of het schema anders is dan dat van [other] (naam en notitie tellen niet mee). */
    fun scheduleDiffers(other: TaskItem): Boolean =
        recurrence != other.recurrence || startDay != other.startDay ||
            weekdays != other.weekdays || intervalDays != other.intervalDays

    /** Of de taak op [date] gedaan moet worden. */
    fun occursOn(date: LocalDate): Boolean {
        val day = date.toEpochDay()
        if (day < startDay) return false
        // Een taak telt pas vanaf de dag dat je hem aanmaakte, anders zou hij achteraf een reeks breken.
        if (createdAt.toLocalDate().isAfter(date)) return false
        deletedAt?.let { if (!date.isBefore(it.toLocalDate())) return false }
        return when (recurrence) {
            TaskRecurrence.EENMALIG -> day == startDay
            TaskRecurrence.DAGELIJKS -> true
            TaskRecurrence.WEEKDAGEN -> Weekdays.contains(weekdays, date.dayOfWeek)
            TaskRecurrence.INTERVAL -> (day - startDay) % intervalDays.coerceAtLeast(1) == 0L
        }
    }
}

/** Een afgevinkte taak op een bepaalde dag. */
@Entity(tableName = "task_logs", primaryKeys = ["taskId", "epochDay"])
data class TaskLog(
    val taskId: Long,
    val epochDay: Long,
    val doneAt: Long,
    /** Momentopname van de naam, voor als de taak later wijzigt. */
    val taskName: String,
)

object Weekdays {
    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0
    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)

    private val SHORT = listOf("ma", "di", "wo", "do", "vr", "za", "zo")
    fun shortLabel(day: DayOfWeek): String = SHORT[day.value - 1]
}

object TaskRules {
    /** Taken voor [date], op naam gesorteerd. */
    fun tasksOn(tasks: List<TaskItem>, date: LocalDate): List<TaskItem> =
        tasks.filter { it.occursOn(date) }.sortedBy { it.name.lowercase(DUTCH) }

    /**
     * Bouwt per dag (nieuwste eerst) op welke taken er stonden en of ze gedaan zijn, in dezelfde vorm
     * als de medicatiegeschiedenis. Zo werkt [Streaks.compute] er ook voor: dagen zonder taken tellen
     * niet mee maar breken de reeks ook niet, en vandaag breekt pas na middernacht.
     */
    fun history(tasks: List<TaskItem>, logs: List<TaskLog>, from: LocalDate, to: LocalDate, now: LocalDateTime): List<HistoryDay> {
        val today = now.toLocalDate()
        val done = logs.groupBy { it.epochDay }.mapValues { (_, l) -> l.associateBy { it.taskId } }
        val days = mutableListOf<HistoryDay>()
        var date = to
        while (!date.isBefore(from)) {
            val dayLogs = done[date.toEpochDay()].orEmpty()
            val rows = tasksOn(tasks, date).map { task ->
                val log = dayLogs[task.id]
                val status = when {
                    log != null -> RowStatus.GEDAAN
                    date.isBefore(today) -> RowStatus.GEMIST
                    else -> RowStatus.OPEN
                }
                HistoryRow(task.id, task.name, ItemType.TAAK, 0, status, log?.doneAt)
            }
            days += HistoryDay(date, rows)
            date = date.minusDays(1)
        }
        return days
    }

    fun firstDay(tasks: List<TaskItem>): LocalDate? =
        tasks.minOfOrNull { maxOf(it.startDay, it.createdAt.toLocalDate().toEpochDay()) }?.let(LocalDate::ofEpochDay)

    fun streak(tasks: List<TaskItem>, logs: List<TaskLog>, now: LocalDateTime): StreakInfo {
        val today = now.toLocalDate()
        val first = firstDay(tasks)?.takeIf { !it.isAfter(today) } ?: return StreakInfo(0, null, 0)
        return Streaks.compute(history(tasks, logs, first, today, now))
    }

    /** Korte omschrijving van hoe vaak een taak terugkomt. */
    fun describe(task: TaskItem, format: (LocalDate) -> String): String = when (task.recurrence) {
        TaskRecurrence.EENMALIG -> "Eenmalig, ${format(LocalDate.ofEpochDay(task.startDay))}"
        TaskRecurrence.DAGELIJKS -> "Elke dag"
        TaskRecurrence.WEEKDAGEN -> DayOfWeek.entries
            .filter { Weekdays.contains(task.weekdays, it) }
            .joinToString(", ") { Weekdays.shortLabel(it) }
            .ifEmpty { "Geen dagen gekozen" }
        TaskRecurrence.INTERVAL -> "Elke ${task.intervalDays} dagen"
    }

    /** Dagen tot de volgende keer dat de taak terugkomt, vanaf [from] (0 = vandaag), of null als nooit meer. */
    fun nextOccurrence(task: TaskItem, from: LocalDate, lookAhead: Long = 366): LocalDate? =
        (0..lookAhead).asSequence().map { from.plus(it, ChronoUnit.DAYS) }.firstOrNull { task.occursOn(it) }
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY name")
    fun observeTasks(): Flow<List<TaskItem>>

    /** Ook verwijderde taken, nodig voor de reeks. */
    @Query("SELECT * FROM tasks")
    fun observeAllTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks")
    suspend fun allTasks(): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: Long): TaskItem?

    @Insert
    suspend fun insert(task: TaskItem): Long

    @Update
    suspend fun update(task: TaskItem)

    @Query("SELECT * FROM task_logs")
    fun observeLogs(): Flow<List<TaskLog>>

    @Query("SELECT * FROM task_logs")
    suspend fun allLogs(): List<TaskLog>

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId AND epochDay = :epochDay")
    suspend fun log(taskId: Long, epochDay: Long): TaskLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: TaskLog)

    @Query("DELETE FROM task_logs WHERE taskId = :taskId AND epochDay = :epochDay")
    suspend fun deleteLog(taskId: Long, epochDay: Long)
}
