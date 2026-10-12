package nl.medicatietracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import nl.medicatietracker.data.TrackedItem
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {
    const val ACTION_REMIND = "nl.medicatietracker.REMIND"
    const val EXTRA_ITEM_ID = "itemId"

    /**
     * Het volgende moment waarop een herinnering moet afgaan:
     * - al afgevinkt vandaag: morgen op het geplande tijdstip;
     * - nog vóór het geplande tijdstip: vandaag op het geplande tijdstip;
     * - daarna: elke [repeatMinutes] minuten opnieuw, tot middernacht.
     */
    fun nextTrigger(minuteOfDay: Int, repeatMinutes: Int, loggedToday: Boolean, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        val dueToday = today.atStartOfDay().plusMinutes(minuteOfDay.toLong())
        val dueTomorrow = dueToday.plusDays(1)
        if (loggedToday) return dueTomorrow
        if (now.isBefore(dueToday)) return dueToday
        if (repeatMinutes <= 0) return dueTomorrow
        val elapsed = Duration.between(dueToday, now).toMinutes()
        val next = dueToday.plusMinutes((elapsed / repeatMinutes + 1) * repeatMinutes)
        return if (next.toLocalDate() == today) next else dueTomorrow
    }

    fun schedule(context: Context, item: TrackedItem, loggedToday: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val next = nextTrigger(item.minuteOfDay, item.repeatMinutes, loggedToday, now)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context, item.id)
        try {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, itemId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, itemId))
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pendingIntent(context: Context, itemId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_ITEM_ID, itemId)
        return PendingIntent.getBroadcast(
            context,
            itemId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
