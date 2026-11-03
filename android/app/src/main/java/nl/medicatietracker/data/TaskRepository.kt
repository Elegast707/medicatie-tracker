package nl.medicatietracker.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.medicatietracker.reminders.Notifications
import java.time.LocalDate
import java.time.LocalDateTime

private const val PREFS_TASK_ACHIEVEMENTS = "taakprestaties"
private const val KEY_CELEBRATED_ON = "gevierdOp"
private const val KEY_MILESTONE = "gevierdeMijlpaal"

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
        when {
            old == null -> dao.insert(task.copy(id = 0))
            // Een nieuw schema mag eerdere dagen niet achteraf veranderen (en zo je reeks breken). Daarom
            // stopt de oude taak vandaag en gaat een nieuwe verder; vandaag al afgevinkt blijft afgevinkt.
            old.scheduleDiffers(task) && old.createdAt.toLocalDate().isBefore(today) -> db.withTransaction {
                val now = System.currentTimeMillis()
                dao.update(old.copy(deletedAt = now))
                val newId = dao.insert(task.copy(id = 0, createdAt = now, deletedAt = null))
                dao.log(old.id, today.toEpochDay())?.let { dao.upsertLog(it.copy(taskId = newId)) }
            }
            else -> dao.update(task)
        }
        // Een nieuwe of gewijzigde taak kan de dag van vandaag weer open zetten.
        syncStars()
    }

    suspend fun delete(task: TaskItem) {
        dao.update(task.copy(deletedAt = System.currentTimeMillis()))
        syncStars()
    }

    /** Vinkt een taak af. Geeft het verdiende eten voor het blobje terug, als er iets verdiend is. */
    suspend fun setDone(taskId: Long, epochDay: Long): Food? {
        val task = dao.task(taskId) ?: return null
        dao.upsertLog(TaskLog(taskId, epochDay, System.currentTimeMillis(), task.name))
        val food = pet.grantFoodForTask(taskId, epochDay)
        val coins = syncStars()
        celebrateMilestone(coins)
        return food
    }

    suspend fun clearDone(taskId: Long, epochDay: Long) {
        dao.deleteLog(taskId, epochDay)
        syncStars()
    }

    /**
     * Zet de takenster gelijk met de takenreeks, net als bij medicatie. Elke nieuw verdiende ster levert
     * muntjes op voor het blobje; geeft het aantal muntjes terug.
     */
    suspend fun syncStars(): Int = starLock.withLock {
        val now = LocalDateTime.now()
        val streak = TaskRules.streak(dao.allTasks(), dao.allLogs(), now)
        val changes = Stars.update(streak, starDao.heldStar(StarTrack.TAKEN.name), now.toLocalDate(), StarTrack.TAKEN)
        if (changes.isEmpty()) return@withLock 0
        starDao.saveStars(changes)
        val coins = changes.filter { it.id == 0L }.sumOf { PetRules.coinsForStar(it.tier) }
        if (coins > 0) pet.addCoins(coins)
        coins
    }

    private suspend fun celebrateMilestone(coins: Int) {
        val now = LocalDateTime.now()
        val petName = pet.ensurePet().displayName
        val streak = TaskRules.streak(dao.allTasks(), dao.allLogs(), now)

        val prefs = context.getSharedPreferences(PREFS_TASK_ACHIEVEMENTS, Context.MODE_PRIVATE)
        val celebratedOn = prefs.getLong(KEY_CELEBRATED_ON, Long.MIN_VALUE)
        val last = Celebration(prefs.getInt(KEY_MILESTONE, 0), celebratedOn).takeIf { celebratedOn != Long.MIN_VALUE }
        val milestone = Streaks.milestoneToCelebrate(streak, last)
        if (milestone == null) {
            if (coins > 0) Notifications.showCoins(context, coins, petName, tasks = true)
            return
        }
        prefs.edit()
            .putLong(KEY_CELEBRATED_ON, now.toLocalDate().toEpochDay())
            .putInt(KEY_MILESTONE, milestone)
            .apply()
        Notifications.showMilestone(context, milestone, coins, petName, tasks = true)
    }
}
