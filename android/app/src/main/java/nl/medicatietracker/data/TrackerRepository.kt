package nl.medicatietracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import nl.medicatietracker.reminders.Notifications
import nl.medicatietracker.reminders.ReminderScheduler
import java.time.LocalDate

class TrackerRepository(private val context: Context, private val dao: TrackerDao) {

    val items: Flow<List<TrackedItem>> = dao.observeItems()
    val activeItems: Flow<List<TrackedItem>> = dao.observeActiveItems()
    val allItems: Flow<List<TrackedItem>> = dao.observeAllItems()

    fun logsForDay(epochDay: Long): Flow<List<LogEntry>> = dao.observeLogsForDay(epochDay)

    fun logsBetween(fromDay: Long, toDay: Long): Flow<List<LogEntry>> =
        dao.observeLogsBetween(fromDay, toDay)

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
