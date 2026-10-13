package nl.medicatietracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import nl.medicatietracker.MedApp
import nl.medicatietracker.data.LogStatus
import java.time.LocalDateTime

/** Voert [block] uit buiten de main thread en houdt de receiver zolang in leven. */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend (MedApp) -> Unit) {
    val app = context.applicationContext as MedApp
    val pending = goAsync()
    app.appScope.launch {
        try {
            block(app)
        } finally {
            pending.finish()
        }
    }
}

/** Gaat af op het geplande tijdstip en daarna elke X minuten zolang het item niet is afgevinkt. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(ReminderScheduler.EXTRA_ITEM_ID, -1L)
        if (itemId < 0) return
        runAsync(context) { app ->
            val dao = app.database.dao()
            val item = dao.item(itemId) ?: return@runAsync
            if (!item.active || item.deletedAt != null) return@runAsync

            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            val loggedToday = dao.log(itemId, today.toEpochDay()) != null
            val due = today.atStartOfDay().plusMinutes(item.minuteOfDay.toLong())
            if (!loggedToday && !now.isBefore(due.minusMinutes(1))) {
                Notifications.showReminder(context, item, today.toEpochDay(), isRepeat = now.isAfter(due.plusMinutes(1)))
            }
            // Een halve minuut vooruit, zodat een iets te vroeg afgegaan alarm niet direct opnieuw afgaat.
            ReminderScheduler.schedule(context, item, loggedToday, now.plusSeconds(30))
        }
    }
}

/** Verwerkt de knoppen "Genomen/Gedaan" en "Overslaan" in een melding. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(Notifications.EXTRA_ITEM_ID, -1L)
        val epochDay = intent.getLongExtra(Notifications.EXTRA_EPOCH_DAY, Long.MIN_VALUE)
        val status = intent.getStringExtra(Notifications.EXTRA_STATUS)
            ?.let { runCatching { LogStatus.valueOf(it) }.getOrNull() }
        if (itemId < 0 || epochDay == Long.MIN_VALUE || status == null) return
        runAsync(context) { app -> app.repository.setStatus(itemId, epochDay, status) }
    }
}

/** Zet alle herinneringen opnieuw na herstart, update, tijd- of tijdzonewijziging. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) { app -> app.repository.rescheduleAll() }
    }
}
