package nl.medicatietracker.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.medicatietracker.reminders.Notifications
import nl.medicatietracker.reminders.TaskReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime

private const val PREFS_TASK_ACHIEVEMENTS = "taakprestaties"
// De reeks telt sinds versie 7 in weken en maanden; daarom nieuwe sleutels.
private const val KEY_RUN_START = "gevierdeReeksStart"
private const val KEY_WEEKS = "gevierdeWeken"
private const val KEY_MONTHS = "gevierdeMaanden"

/** Het tabblad Taken: losse en terugkerende taken met een eigen reeks en eigen sterren. */
class TaskRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val pet: PetRepository,
) {
    private val dao = db.taskDao()
    private val starDao = db.dao()

    val tasks: Flow<List<TaskItem>> = dao.observeTasks()
    val allTasks: Flow<List<TaskItem>> = dao.observeAllTasks()
    val logs: Flow<List<TaskLog>> = dao.observeLogs()

    val heldStar: Flow<StarEntry?> = starDao.observeHeldStar(StarTrack.TAKEN.name)
    val collectedStars: Flow<List<StarEntry>> = starDao.observeCollectedStars(StarTrack.TAKEN.name)

    private val starLock = Mutex()

    suspend fun save(task: TaskItem) {
        val old = if (task.id == 0L) null else dao.task(task.id)
        val today = LocalDate.now()
        val savedId = when {
            old == null -> dao.insert(task.copy(id = 0))
            // Een nieuw schema mag eerdere dagen niet achteraf veranderen (en zo je reeks breken). Daarom
            // stopt de oude taak vandaag en gaat een nieuwe verder; vandaag al afgevinkt blijft afgevinkt.
            old.scheduleDiffers(task) && old.createdAt.toLocalDate().isBefore(today) -> db.withTransaction {
                val now = System.currentTimeMillis()
                dao.update(old.copy(deletedAt = now))
                val newId = dao.insert(task.copy(id = 0, createdAt = now, deletedAt = null))
                dao.log(old.id, today.toEpochDay())?.let { dao.upsertLog(it.copy(taskId = newId)) }
                newId
            }
            else -> {
                dao.update(task)
                task.id
            }
        }
        if (old != null && old.id != savedId) cancelReminder(old.id)
        dao.task(savedId)?.let { reschedule(it) }
        // Een nieuwe of gewijzigde taak kan de dag van vandaag weer open zetten.
        syncStars()
    }

    suspend fun delete(task: TaskItem) {
        dao.update(task.copy(deletedAt = System.currentTimeMillis()))
        cancelReminder(task.id)
        syncStars()
    }

    /**
     * Verwijdert een taak helemaal: ook uit eerdere dagen, de kalender en de meter. Eerdere versies van
     * dezelfde taak (van voor een schemawijziging) gaan mee. Je reeks wordt opnieuw berekend; eten dat
     * het blobje al kreeg blijft.
     */
    suspend fun deleteCompletely(task: TaskItem) {
        val ids = dao.allTasks()
            .filter { it.id == task.id || (it.deletedAt != null && it.name == task.name) }
            .map { it.id }
        db.withTransaction {
            dao.deleteLogsFor(ids)
            dao.deleteTasks(ids)
        }
        ids.forEach { cancelReminder(it) }
        syncStars()
    }

    /** Vinkt een taak af. Geeft het verdiende eten voor het blobje terug, als er iets verdiend is. */
    suspend fun setDone(taskId: Long, epochDay: Long): Food? {
        val task = dao.task(taskId) ?: return null
        dao.upsertLog(TaskLog(taskId, epochDay, System.currentTimeMillis(), task.name))
        Notifications.cancelTask(context, taskId)
        reschedule(task)
        val food = pet.grantFoodForTask(taskId, epochDay)
        val coins = syncStars()
        celebrateMilestone(coins)
        return food
    }

    suspend fun clearDone(taskId: Long, epochDay: Long) {
        dao.deleteLog(taskId, epochDay)
        dao.task(taskId)?.let { reschedule(it) }
        syncStars()
    }

    /**
     * Zet de takenmedaille gelijk met de takenreeks, net als de ster bij medicatie. Elke nieuwe medaille levert
     * muntjes op voor het blobje; geeft het aantal muntjes terug.
     */
    suspend fun syncStars(): Int = starLock.withLock {
        val now = LocalDateTime.now()
        val streak = TaskRules.streak(dao.allTasks(), dao.allLogs(), now).asStarStreak()
        val changes = Stars.update(streak, starDao.heldStar(StarTrack.TAKEN.name), now.toLocalDate(), StarTrack.TAKEN)
        if (changes.isEmpty()) return@withLock 0
        starDao.saveStars(changes)
        val coins = changes.filter { it.id == 0L }.sumOf { PetRules.coinsForStar(it.tier) }
        if (coins > 0) pet.addCoins(coins)
        coins
    }

    /** Zet de meldingen van alle taken opnieuw, bijvoorbeeld na een herstart van de telefoon. */
    suspend fun rescheduleAll() {
        dao.allTasks().filter { it.deletedAt == null }.forEach { reschedule(it) }
    }

    private suspend fun reschedule(task: TaskItem) {
        if (task.deletedAt != null) {
            cancelReminder(task.id)
            return
        }
        val doneToday = dao.log(task.id, LocalDate.now().toEpochDay()) != null
        TaskReminderScheduler.schedule(context, task, doneToday)
    }

    private fun cancelReminder(taskId: Long) {
        TaskReminderScheduler.cancel(context, taskId)
        Notifications.cancelTask(context, taskId)
    }

    /** Felicitatie bij elke nieuwe complete week of maand, één keer per reeks. */
    private suspend fun celebrateMilestone(coins: Int) {
        val now = LocalDateTime.now()
        val petName = pet.ensurePet().displayName
        val streak = TaskRules.streak(dao.allTasks(), dao.allLogs(), now)
        val runStart = streak.runStart?.toEpochDay() ?: Long.MIN_VALUE

        val prefs = context.getSharedPreferences(PREFS_TASK_ACHIEVEMENTS, Context.MODE_PRIVATE)
        // Een nieuwe reeks (na een gemiste taak) begint weer bij nul.
        val sameRun = prefs.getLong(KEY_RUN_START, Long.MIN_VALUE) == runStart
        val weeks = if (sameRun) prefs.getInt(KEY_WEEKS, 0) else 0
        val months = if (sameRun) prefs.getInt(KEY_MONTHS, 0) else 0
        val milestone = TaskStreaks.milestoneToCelebrate(streak, weeks, months)
        if (milestone == null) {
            if (coins > 0) Notifications.showCoins(context, coins, petName, tasks = true)
            return
        }
        prefs.edit()
            .putLong(KEY_RUN_START, runStart)
            .putInt(KEY_WEEKS, maxOf(weeks, milestone.weeks))
            .putInt(KEY_MONTHS, maxOf(months, milestone.months))
            .apply()
        Notifications.showTaskMilestone(context, milestone.title, milestone.message, coins, petName)
    }
}
