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
import nl.medicatietracker.data.Streaks
import nl.medicatietracker.data.TaskItem
import nl.medicatietracker.data.TrackedItem
import nl.medicatietracker.data.doneLabel
import nl.medicatietracker.data.formatMinute

object Notifications {
    private const val CHANNEL_ID = "herinneringen"
    private const val ACHIEVEMENT_CHANNEL_ID = "prestaties"
    private const val TEST_NOTIFICATION_ID = 999_999
    private const val MILESTONE_NOTIFICATION_ID = 888_888
    private const val TASK_MILESTONE_NOTIFICATION_ID = 888_889

    const val ACTION_LOG = "nl.medicatietracker.LOG"
    const val ACTION_TASK_DONE = "nl.medicatietracker.TASK_DONE"
    const val EXTRA_TASK_ID = "taskId"

    /** Taken krijgen meldingnummers boven die van medicijnen, zodat ze elkaar niet overschrijven. */
    private const val TASK_NOTIFICATION_BASE = 2_000_000
    const val EXTRA_ITEM_ID = "itemId"
    const val EXTRA_EPOCH_DAY = "epochDay"
    const val EXTRA_STATUS = "status"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Herinneringen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Herinneringen voor medicatie en taken"
            enableVibration(true)
        }
        val achievements = NotificationChannel(ACHIEVEMENT_CHANNEL_ID, "Prestaties", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Felicitaties als je een week, maand of langer alles hebt afgevinkt"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, achievements))
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

    fun showTaskReminder(context: Context, task: TaskItem, epochDay: Long, isRepeat: Boolean) {
        val id = taskNotificationId(task.id)
        val text = buildString {
            append(if (isRepeat) "Nog niet afgevinkt · gepland om " else "Gepland om ")
            append(formatMinute(task.minuteOfDay))
            if (task.details.isNotBlank()) append(" · ").append(task.details)
        }
        val intent = Intent(context, TaskActionReceiver::class.java)
            .setAction(ACTION_TASK_DONE)
            .putExtra(EXTRA_TASK_ID, task.id)
            .putExtra(EXTRA_EPOCH_DAY, epochDay)
        val done = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = baseBuilder(context, id, task.name, text).addAction(0, "Gedaan", done)
        notify(context, id, builder)
    }

    fun cancelTask(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskNotificationId(taskId))
    }

    private fun taskNotificationId(taskId: Long): Int = TASK_NOTIFICATION_BASE + taskId.toInt()

    /** Felicitatie voor een complete week of maand in de takenreeks. */
    fun showTaskMilestone(context: Context, title: String, message: String, coins: Int, petName: String) {
        val text = message + if (coins > 0) " Je verdiende $coins muntjes voor $petName!" else ""
        val builder = baseBuilder(context, TASK_MILESTONE_NOTIFICATION_ID, "🎉 Taken: $title", text, ACHIEVEMENT_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        notify(context, TASK_MILESTONE_NOTIFICATION_ID, builder)
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

    fun showMilestone(context: Context, milestone: Int, coins: Int, petName: String) {
        val text = Streaks.message(milestone) + if (coins > 0) " Je verdiende $coins muntjes voor $petName!" else ""
        val title = "🎉 " + Streaks.title(milestone)
        val id = MILESTONE_NOTIFICATION_ID
        val builder = baseBuilder(context, id, title, text, ACHIEVEMENT_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        notify(context, id, builder)
    }

    fun showCoins(context: Context, coins: Int, petName: String, tasks: Boolean = false) {
        val id = if (tasks) TASK_MILESTONE_NOTIFICATION_ID else MILESTONE_NOTIFICATION_ID
        val title = if (tasks) "⭐ Nieuwe takenster!" else "⭐ Nieuwe ster!"
        val builder = baseBuilder(context, id, title, "Je verdiende $coins muntjes voor $petName!", ACHIEVEMENT_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        notify(context, id, builder)
    }

    fun cancel(context: Context, itemId: Long) {
        NotificationManagerCompat.from(context).cancel(itemId.toInt())
    }

    private fun baseBuilder(
        context: Context,
        requestCode: Int,
        title: String,
        text: String,
        channelId: String = CHANNEL_ID,
    ): NotificationCompat.Builder {
        val openApp = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId)
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
