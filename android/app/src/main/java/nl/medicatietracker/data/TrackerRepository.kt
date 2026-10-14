package nl.medicatietracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.medicatietracker.reminders.Notifications
import nl.medicatietracker.reminders.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime

private const val PREFS_ACHIEVEMENTS = "prestaties"
private const val KEY_CELEBRATED_ON = "gevierdOp"
// Oudere versies bewaarden de start van de gevierde reeks; die dag ligt nooit na de vierdag.
private const val KEY_STREAK_START = "gevierdeReeksStart"
private const val KEY_MILESTONE = "gevierdeMijlpaal"

class TrackerRepository(
    private val context: Context,
    private val dao: TrackerDao,
    private val pet: PetRepository,
) {

    val items: Flow<List<TrackedItem>> = dao.observeItems()
    val activeItems: Flow<List<TrackedItem>> = dao.observeActiveItems()
    val allItems: Flow<List<TrackedItem>> = dao.observeAllItems()

    fun logsForDay(epochDay: Long): Flow<List<LogEntry>> = dao.observeLogsForDay(epochDay)

    val allLogs: Flow<List<LogEntry>> = dao.observeAllLogs()

    /** De ster die nu rechtsboven staat, of null zonder reeks van minstens een week. */
    val heldStar: Flow<StarEntry?> = dao.observeHeldStar()
    val collectedStars: Flow<List<StarEntry>> = dao.observeCollectedStars()

    private val starLock = Mutex()

    /** Logt een moment. Geeft het verdiende eten voor het blobje terug, als er iets verdiend is. */
    suspend fun setStatus(itemId: Long, epochDay: Long, status: LogStatus): Food? {
        val item = dao.item(itemId) ?: return null
        dao.upsertLog(
            LogEntry(
                itemId = itemId,
                epochDay = epochDay,
                status = status,
                loggedAt = System.currentTimeMillis(),
                itemName = item.name,
                itemType = item.type,
                scheduledMinute = item.minuteOfDay,
            ),
        )
        Notifications.cancel(context, itemId)
        reschedule(item)
        val food = if (status == LogStatus.GEDAAN) pet.grantFoodFor(itemId, epochDay) else null
        val coins = syncStars()
        celebrateMilestone(coins)
        return food
    }

    suspend fun clearStatus(itemId: Long, epochDay: Long) {
        dao.deleteLog(itemId, epochDay)
        dao.item(itemId)?.let { reschedule(it) }
        syncStars()
    }

    /**
     * Zet de ster rechtsboven gelijk met de huidige reeks en bewaart vervangen of verloren sterren
     * in de verzameling. Ook nodig als er niets is afgevinkt: een reeks breekt vanzelf na een gemiste dag.
     * Elke nieuw verdiende ster levert muntjes op voor het blobje; geeft het aantal muntjes terug.
     */
    suspend fun syncStars(): Int = starLock.withLock {
        val now = LocalDateTime.now()
        val streak = currentStreak(now) ?: StreakInfo(0, null, 0)
        val changes = Stars.update(streak, dao.heldStar(), now.toLocalDate())
        if (changes.isEmpty()) return@withLock 0
        dao.saveStars(changes)
        // Nieuwe sterren hebben nog geen id; bestaande sterren die naar de verzameling gaan wel.
        val coins = changes.filter { it.id == 0L }.sumOf { PetRules.coinsForStar(it.tier) }
        if (coins > 0) pet.addCoins(coins)
        coins
    }

    suspend fun save(item: TrackedItem) {
        val saved = if (item.id == 0L) {
            item.copy(id = dao.insert(item))
        } else {
            dao.update(item)
            item
        }
        reschedule(saved)
    }

    suspend fun delete(item: TrackedItem) {
        val deleted = item.copy(deletedAt = System.currentTimeMillis())
        dao.update(deleted)
        reschedule(deleted)
    }

    suspend fun rescheduleAll() {
        dao.activeItems().forEach { reschedule(it) }
    }

    suspend fun exportData(): Pair<List<TrackedItem>, List<LogEntry>> = dao.allItems() to dao.allLogs()

    /**
     * Stuurt een felicitatie als de huidige reeks een nieuwe mijlpaal heeft bereikt, met de muntjes
     * die er net mee verdiend zijn.
     */
    private suspend fun celebrateMilestone(coins: Int) {
        val now = LocalDateTime.now()
        val petName = pet.ensurePet().displayName
        val streak = currentStreak(now)

        val prefs = context.getSharedPreferences(PREFS_ACHIEVEMENTS, Context.MODE_PRIVATE)
        val celebratedOn = prefs.getLong(KEY_CELEBRATED_ON, prefs.getLong(KEY_STREAK_START, Long.MIN_VALUE))
        val last = Celebration(prefs.getInt(KEY_MILESTONE, 0), celebratedOn).takeIf { celebratedOn != Long.MIN_VALUE }
        val milestone = streak?.let { Streaks.milestoneToCelebrate(it, last) }
        if (milestone == null) {
            if (coins > 0) Notifications.showCoins(context, coins, petName)
            return
        }

        prefs.edit()
            .putLong(KEY_CELEBRATED_ON, now.toLocalDate().toEpochDay())
            .putInt(KEY_MILESTONE, milestone)
            .apply()
        Notifications.showMilestone(context, milestone, coins, petName)
    }

    private suspend fun currentStreak(now: LocalDateTime): StreakInfo? {
        val items = dao.allItems()
        val logs = dao.allLogs()
        val firstDay = History.firstDay(items, logs) ?: return null
        return Streaks.compute(History.build(items, logs, firstDay, now.toLocalDate(), now))
    }

    private suspend fun reschedule(item: TrackedItem) {
        if (!item.active || item.deletedAt != null) {
            ReminderScheduler.cancel(context, item.id)
            Notifications.cancel(context, item.id)
            return
        }
        val loggedToday = dao.log(item.id, LocalDate.now().toEpochDay()) != null
        ReminderScheduler.schedule(context, item, loggedToday)
    }
}
