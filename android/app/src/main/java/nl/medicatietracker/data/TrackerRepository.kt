package nl.medicatietracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import nl.medicatietracker.reminders.Notifications
import nl.medicatietracker.reminders.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime

private const val PREFS_ACHIEVEMENTS = "prestaties"
private const val KEY_CELEBRATED_ON = "gevierdOp"
// Oudere versies bewaarden de start van de gevierde reeks; die dag ligt nooit na de vierdag.
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

    /** Stuurt een felicitatie als de huidige reeks een nieuwe mijlpaal heeft bereikt. */
    private suspend fun celebrateMilestone() {
        val items = dao.allItems()
        val logs = dao.allLogs()
        val now = LocalDateTime.now()
        val firstDay = History.firstDay(items, logs) ?: return
        val streak = Streaks.compute(History.build(items, logs, firstDay, now.toLocalDate(), now))

        val prefs = context.getSharedPreferences(PREFS_ACHIEVEMENTS, Context.MODE_PRIVATE)
        val celebratedOn = prefs.getLong(KEY_CELEBRATED_ON, prefs.getLong(KEY_STREAK_START, Long.MIN_VALUE))
        val last = Celebration(prefs.getInt(KEY_MILESTONE, 0), celebratedOn).takeIf { celebratedOn != Long.MIN_VALUE }
        val milestone = Streaks.milestoneToCelebrate(streak, last) ?: return

        prefs.edit()
            .putLong(KEY_CELEBRATED_ON, now.toLocalDate().toEpochDay())
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
