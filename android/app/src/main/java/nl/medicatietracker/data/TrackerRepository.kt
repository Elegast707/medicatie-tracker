package nl.medicatietracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import nl.medicatietracker.reminders.Notifications
import nl.medicatietracker.reminders.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime

private const val PREFS_ACHIEVEMENTS = "prestaties"
private const val KEY_STREAK_START = "gevierdeReeksStart"
private const val KEY_MILESTONE = "gevierdeMijlpaal"

class TrackerRepository(private val context: Context, private val dao: TrackerDao) {

    val items: Flow<List<TrackedItem>> = dao.observeItems()
    val activeItems: Flow<List<TrackedItem>> = dao.observeActiveItems()
    val allItems: Flow<List<TrackedItem>> = dao.observeAllItems()

    fun logsForDay(epochDay: Long): Flow<List<LogEntry>> = dao.observeLogsForDay(epochDay)

    val allLogs: Flow<List<LogEntry>> = dao.observeAllLogs()

    suspend fun setStatus(itemId: Long, epochDay: Long, status: LogStatus) {
        val item = dao.item(itemId) ?: return
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
        celebrateMilestone()
    }

    suspend fun clearStatus(itemId: Long, epochDay: Long) {
        dao.deleteLog(itemId, epochDay)
        dao.item(itemId)?.let { reschedule(it) }
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
     * Stuurt een felicitatie als de huidige reeks een nieuwe mijlpaal heeft bereikt. Elke mijlpaal
     * wordt per reeks maar één keer gevierd, ook als je iets ongedaan maakt en opnieuw afvinkt.
     */
    private suspend fun celebrateMilestone() {
        val items = dao.allItems()
        val logs = dao.allLogs()
        val now = LocalDateTime.now()
        val firstDay = History.firstDay(items, logs) ?: return
        val streak = Streaks.compute(History.build(items, logs, firstDay, now.toLocalDate(), now))
        val start = streak.currentStart?.toEpochDay() ?: return
        val milestone = Streaks.highestMilestoneUpTo(streak.current) ?: return

        val prefs = context.getSharedPreferences(PREFS_ACHIEVEMENTS, Context.MODE_PRIVATE)
        val celebratedStart = prefs.getLong(KEY_STREAK_START, Long.MAX_VALUE)
        val celebratedMilestone = prefs.getInt(KEY_MILESTONE, 0)
        // Een reeks die achteraf naar achteren is verlengd, is nog steeds dezelfde reeks.
        val sameStreak = start <= celebratedStart && celebratedStart != Long.MAX_VALUE
        if (sameStreak && milestone <= celebratedMilestone) return

        prefs.edit()
            .putLong(KEY_STREAK_START, start)
            .putInt(KEY_MILESTONE, milestone)
            .apply()
        Notifications.showMilestone(context, milestone)
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
