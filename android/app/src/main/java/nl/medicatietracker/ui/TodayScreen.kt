package nl.medicatietracker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.medicatietracker.MedApp
import nl.medicatietracker.data.DUTCH
import nl.medicatietracker.data.ItemType
import nl.medicatietracker.data.LogEntry
import nl.medicatietracker.data.LogStatus
import nl.medicatietracker.data.RowStatus
import nl.medicatietracker.data.TrackedItem
import nl.medicatietracker.data.doneLabel
import nl.medicatietracker.data.formatMinute
import nl.medicatietracker.data.formatTime
import nl.medicatietracker.reminders.Notifications
import nl.medicatietracker.reminders.ReminderScheduler
import java.time.format.DateTimeFormatter

private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", DUTCH)

@Composable
fun TodayScreen(app: MedApp) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val epochDay = today.toEpochDay()
    val items by app.repository.activeItems.collectAsStateWithLifecycle(emptyList())
    val logs by remember(epochDay) { app.repository.logsForDay(epochDay) }
        .collectAsStateWithLifecycle(emptyList())
    val logsByItem = logs.associateBy { it.itemId }
    val nowMinute = now.hour * 60 + now.minute

    val context = LocalContext.current
    val doneCount = items.count { logsByItem[it.id] != null }
    val title = today.format(dayFormatter).replaceFirstChar { it.uppercase(DUTCH) }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenTitle("Vandaag", title)
        }
        item { PermissionBanners() }
        if (items.isEmpty()) {
            item {
                Text(
                    "Er staat nog niets ingepland. Voeg medicatie of taken toe via 'Beheren'.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("$doneCount van ${items.size} afgevinkt", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(6.dp))
                    LinearProgressIndicator(
                        progress = { if (items.isEmpty()) 0f else doneCount.toFloat() / items.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            for (type in ItemType.entries) {
                val group = items.filter { it.type == type }
                if (group.isEmpty()) continue
                item(key = "header-$type") {
                    Text(
                        if (type == ItemType.MEDICATIE) "Medicatie" else "Taken",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(group, key = { it.id }) { item ->
                    val log = logsByItem[item.id]
                    TodayItemCard(
                        item = item,
                        log = log,
                        overdue = log == null && nowMinute >= item.minuteOfDay,
                        onLog = { status ->
                            app.appScope.launch {
                                val food = app.repository.setStatus(item.id, epochDay, status)
                                if (food != null) {
                                    val petName = app.petRepository.ensurePet().displayName
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "+1 ${food.label.lowercase(DUTCH)} voor $petName!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onUndo = { app.appScope.launch { app.repository.clearStatus(item.id, epochDay) } },
                    )
                }
            }
        }
    }
}

/** Felgroen, zodat "Genomen" er in zowel het gouden als het grijze thema uitspringt. */
private val DoneGreen = Color(0xFF1E8E3E)

/** Groen rondje met een wit vinkje. Net afgevinkt? Dan springt het met een veerkrachtige animatie tevoorschijn. */
@Composable
private fun DoneCheck(log: LogEntry) {
    val fresh = remember(log.id) { System.currentTimeMillis() - log.loggedAt < 3_000 }
    val pop = remember(log.id) { Animatable(if (fresh) 0f else 1f) }
    val turn = remember(log.id) { Animatable(if (fresh) -90f else 0f) }
    LaunchedEffect(log.id) {
        if (!fresh) return@LaunchedEffect
        launch { turn.animateTo(0f, tween(400)) }
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = turn.value
            }
            .background(DoneGreen, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = "Gedaan", tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun TodayItemCard(
    item: TrackedItem,
    log: LogEntry?,
    overdue: Boolean,
    onLog: (LogStatus) -> Unit,
    onUndo: () -> Unit,
) {
    val colors = when {
        log != null -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        overdue -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        else -> CardDefaults.cardColors()
    }
    Card(
        colors = colors,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatMinute(item.minuteOfDay),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    if (item.details.isNotBlank()) {
                        Text(item.details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (log != null) {
                    if (log.status == LogStatus.GEDAAN) {
                        DoneCheck(log)
                    } else {
                        StatusIcon(RowStatus.OVERGESLAGEN)
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            if (log == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onLog(LogStatus.GEDAAN) },
                        colors = ButtonDefaults.buttonColors(containerColor = DoneGreen, contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(item.type.doneLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        onClick = { onLog(LogStatus.OVERGESLAGEN) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Overslaan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = if (log.status == LogStatus.GEDAAN) item.type.doneLabel() else "Overgeslagen"
                    Text(
                        "$label om ${log.loggedAt.formatTime()}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onUndo) { Text("Ongedaan maken") }
                }
            }
        }
    }
}

/** Waarschuwt als meldingen of exacte alarmen niet zijn toegestaan, met een knop om het te regelen. */
@Composable
private fun PermissionBanners() {
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(Notifications.canNotify(context)) }
    var canExact by remember { mutableStateOf(ReminderScheduler.canScheduleExact(context)) }
    var askedOnce by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        canNotify = Notifications.canNotify(context)
        canExact = ReminderScheduler.canScheduleExact(context)
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askedOnce = true
        canNotify = Notifications.canNotify(context)
    }

    if (!canNotify) {
        Banner(
            text = "Meldingen staan uit. Zonder meldingen krijg je geen herinneringen.",
            action = "Meldingen toestaan",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedOnce) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        }
    }
    if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Banner(
            text = "Herinneringen kunnen te laat komen omdat 'Wekkers en herinneringen' uit staat.",
            action = "Toestaan",
        ) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
            )
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(action) }
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}
