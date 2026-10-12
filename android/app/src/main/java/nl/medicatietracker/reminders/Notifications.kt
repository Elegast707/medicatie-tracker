package nl.medicatietracker.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import nl.medicatietracker.MainActivity
import nl.medicatietracker.R
import nl.medicatietracker.data.ItemType
import nl.medicatietracker.data.LogStatus
import nl.medicatietracker.data.TrackedItem
import nl.medicatietracker.data.doneLabel
import nl.medicatietracker.data.formatMinute

object Notifications {
    private const val CHANNEL_ID = "herinneringen"
    private const val TEST_NOTIFICATION_ID = 999_999

    const val ACTION_LOG = "nl.medicatietracker.LOG"
    const val EXTRA_ITEM_ID = "itemId"
    const val EXTRA_EPOCH_DAY = "epochDay"
    const val EXTRA_STATUS = "status"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Herinneringen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Herinneringen voor medicatie en taken"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showReminder(context: Context, item: TrackedItem, epochDay: Long, isRepeat: Boolean) {
        val title = if (item.type == ItemType.MEDICATIE) "Tijd voor ${item.name}" else item.name
        val text = buildString {
            append(if (isRepeat) "Nog niet afgevinkt · gepland om " else "Gepland om ")
            append(formatMinute(item.minuteOfDay))
            if (item.details.isNotBlank()) append(" · ").append(item.details)
        }
        val builder = baseBuilder(context, item.id.toInt(), title, text)
            .addAction(0, item.type.doneLabel(), logIntent(context, item.id, epochDay, LogStatus.GEDAAN))
            .addAction(0, "Overslaan", logIntent(context, item.id, epochDay, LogStatus.OVERGESLAGEN))
        notify(context, item.id.toInt(), builder)
    }

    fun showTest(context: Context) {
        val builder = baseBuilder(
            context,
            TEST_NOTIFICATION_ID,
            "Testmelding",
            "Meldingen werken. Zo ziet een herinnering eruit.",
        )
        notify(context, TEST_NOTIFICATION_ID, builder)
    }

    fun cancel(context: Context, itemId: Long) {
        NotificationManagerCompat.from(context).cancel(itemId.toInt())
    }

    private fun baseBuilder(context: Context, requestCode: Int, title: String, text: String): NotificationCompat.Builder {
        val openApp = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
    }

    private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canNotify(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Toestemming is net ingetrokken; niets aan te doen.
        }
    }

    private fun logIntent(context: Context, itemId: Long, epochDay: Long, status: LogStatus): PendingIntent {
        // De action verschilt per status, zodat "Genomen" en "Overslaan" aparte PendingIntents zijn.
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction("$ACTION_LOG.${status.name}")
            .putExtra(EXTRA_ITEM_ID, itemId)
            .putExtra(EXTRA_EPOCH_DAY, epochDay)
            .putExtra(EXTRA_STATUS, status.name)
        return PendingIntent.getBroadcast(
            context,
            itemId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
