package nl.medicatietracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.medicatietracker.MedApp
import nl.medicatietracker.R
import nl.medicatietracker.data.ItemType
import nl.medicatietracker.data.TrackedItem
import nl.medicatietracker.data.formatMinute
import nl.medicatietracker.reminders.Notifications

private val repeatOptions = listOf(0, 5, 10, 15, 30, 60)

private fun repeatLabel(minutes: Int): String =
    if (minutes <= 0) "Niet herhalen" else "Elke $minutes minuten herhalen"

@Composable
fun ManageScreen(app: MedApp, onOpenStars: () -> Unit) {
    val context = LocalContext.current
    val items by app.repository.items.collectAsStateWithLifecycle(emptyList())
    // null = geen dialoog, item met id 0 = nieuw item
    var editing by remember { mutableStateOf<TrackedItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item { ScreenTitle("Beheren", "Tik op een item om het te wijzigen") }
            for (type in ItemType.entries) {
                val group = items.filter { it.type == type }
                item(key = "header-$type") {
                    Text(
                        if (type == ItemType.MEDICATIE) "Medicatie" else "Taken",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                if (group.isEmpty()) {
                    item(key = "empty-$type") {
                        Text(
                            "Nog niets toegevoegd.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(group, key = { it.id }) { item ->
                    ListItem(
                        modifier = Modifier.clickable { editing = item },
                        leadingContent = {
                            Text(formatMinute(item.minuteOfDay), style = MaterialTheme.typography.titleMedium)
                        },
                        headlineContent = { Text(item.name) },
                        supportingContent = {
                            val parts = listOfNotNull(
                                item.details.takeIf { it.isNotBlank() },
                                repeatLabel(item.repeatMinutes),
                                "overslaan mag".takeIf { item.skipAllowed },
                            )
                            Text(parts.joinToString(" · "))
                        },
                        trailingContent = {
                            Switch(
                                checked = item.active,
                                onCheckedChange = { active -> app.appScope.launch { app.repository.save(item.copy(active = active)) } },
                            )
                        },
                    )
                    HorizontalDivider()
                }
            }
            item {
                PixelCard(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Meldingen", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Met de schakelaar zet je herinneringen voor een item tijdelijk uit. " +
                                "Komen meldingen niet of te laat? Zet dan batterijoptimalisatie voor deze app uit.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = { Notifications.showTest(context) }, shape = MaterialTheme.shapes.small) { Text("Testmelding sturen") }
                    }
                }
            }
            item {
                PixelCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sterren", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Brons bij 7 dagen op rij, zilver bij 30, goud bij 90 en blauw bij 365. " +
                                "Vervangen en verloren sterren bewaar je in je verzameling.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = onOpenStars, shape = MaterialTheme.shapes.small) { Text("Sterrencollectie bekijken") }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = TrackedItem(name = "", type = ItemType.MEDICATIE, minuteOfDay = 9 * 60) },
            icon = { Icon(painterResource(R.drawable.ic_px_add), contentDescription = null, modifier = Modifier.size(24.dp)) },
            text = { Text("Toevoegen") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { item ->
        EditItemDialog(
            initial = item,
            onDismiss = { editing = null },
            onSave = { updated ->
                app.appScope.launch { app.repository.save(updated) }
                editing = null
            },
            onDelete = {
                app.appScope.launch { app.repository.delete(item) }
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditItemDialog(
    initial: TrackedItem,
    onDismiss: () -> Unit,
    onSave: (TrackedItem) -> Unit,
    onDelete: () -> Unit,
) {
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var details by remember { mutableStateOf(initial.details) }
    var type by remember { mutableStateOf(initial.type) }
    var minuteOfDay by remember { mutableIntStateOf(initial.minuteOfDay) }
    var repeatMinutes by remember { mutableIntStateOf(initial.repeatMinutes) }
    var skipAllowed by remember { mutableStateOf(initial.skipAllowed) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRepeatMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nieuw item" else "Item wijzigen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == ItemType.MEDICATIE, onClick = { type = ItemType.MEDICATIE }, label = { Text("Medicatie") })
                    FilterChip(selected = type == ItemType.TAAK, onClick = { type = ItemType.TAAK }, label = { Text("Taak") })
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naam") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text(if (type == ItemType.MEDICATIE) "Dosis / notitie (optioneel)" else "Notitie (optioneel)") },
                    singleLine = true,
                )
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Text("Elke dag om ${formatMinute(minuteOfDay)}")
                }
                Box {
                    OutlinedButton(onClick = { showRepeatMenu = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                        Text(repeatLabel(repeatMinutes))
                    }
                    DropdownMenu(expanded = showRepeatMenu, onDismissRequest = { showRepeatMenu = false }) {
                        repeatOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(repeatLabel(option)) },
                                onClick = {
                                    repeatMinutes = option
                                    showRepeatMenu = false
                                },
                            )
                        }
                    }
                }
                Text(
                    "Zolang je het niet afvinkt, krijg je opnieuw een melding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Overslaan breekt de reeks niet", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (skipAllowed) {
                                "Overslaan telt als afgevinkt voor je reeks."
                            } else {
                                "Alleen ${if (type == ItemType.MEDICATIE) "nemen" else "doen"} telt; overslaan breekt je reeks."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = skipAllowed, onCheckedChange = { skipAllowed = it })
                }
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Verwijderen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            details = details.trim(),
                            type = type,
                            minuteOfDay = minuteOfDay,
                            repeatMinutes = repeatMinutes,
                            skipAllowed = skipAllowed,
                        ),
                    )
                },
            ) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = minuteOfDay / 60,
            initialMinute = minuteOfDay % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Tijdstip") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    minuteOfDay = state.hour * 60 + state.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Annuleren") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("'${initial.name}' verwijderen?") },
            text = { Text("Je krijgt er geen herinneringen meer voor. De geschiedenis blijft bewaard.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Verwijderen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren") } },
        )
    }
}
