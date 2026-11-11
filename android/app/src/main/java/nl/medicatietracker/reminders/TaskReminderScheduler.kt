package nl.medicatietracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import nl.medicatietracker.data.TaskItem
import nl.medicatietracker.data.TaskRules
import java.time.LocalDateTime
import java.time.ZoneId

/** Herinneringen voor taken: op het ingestelde tijdstip, en daarna elk uur tot de taak is afgevinkt. */
object TaskReminderScheduler {
    const val ACTION_REMIND_TASK = "nl.medicatietracker.REMIND_TASK"
    const val EXTRA_TASK_ID = "taskId"
    const val REPEAT_MINUTES = 60

    /**
     * Het volgende moment voor een melding, of null als de taak nooit meer terugkomt:
     * - staat de taak vandaag en is hij nog niet gedaan: op het tijdstip, daarna elk uur tot middernacht;
     * - anders: op het tijdstip van de eerstvolgende dag waarop de taak staat.
     */
    fun nextTrigger(task: TaskItem, doneToday: Boolean, now: LocalDateTime): LocalDateTime? {
        if (task.deletedAt != null) return null
        val today = now.toLocalDate()
        if (task.occursOn(today) && !doneToday) {
            val next = ReminderScheduler.nextTrigger(task.minuteOfDay, REPEAT_MINUTES, loggedToday = false, now = now)
            if (next.toLocalDate() == today) return next
        }
        val nextDay = TaskRules.nextOccurrence(task, today.plusDays(1)) ?: return null
        return nextDay.atStartOfDay().plusMinutes(task.minuteOfDay.toLong())
    }

    fun schedule(context: Context, task: TaskItem, doneToday: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val next = nextTrigger(task, doneToday, now)
        if (next == null) {
            cancel(context, task.id)
            return
        }
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context, task.id)
        try {
            if (ReminderScheduler.canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, taskId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, taskId))
    }

    private fun pendingIntent(context: Context, taskId: Long): PendingIntent {
        // Eigen receiver en action, zodat een taak nooit het alarm van een medicijn met hetzelfde id vervangt.
        val intent = Intent(context, TaskAlarmReceiver::class.java)
            .setAction(ACTION_REMIND_TASK)
            .putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
