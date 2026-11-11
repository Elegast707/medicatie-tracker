package nl.medicatietracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.annotation.DrawableRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.medicatietracker.data.History
import nl.medicatietracker.data.PetMood
import nl.medicatietracker.data.StarEntry
import nl.medicatietracker.data.Streaks
import nl.medicatietracker.data.TaskRules
import nl.medicatietracker.ui.MedicationScreen
import nl.medicatietracker.ui.AppTint
import nl.medicatietracker.ui.MedTheme
import nl.medicatietracker.ui.PetScreen
import nl.medicatietracker.ui.PixelImage
import nl.medicatietracker.ui.PixelTabButton
import nl.medicatietracker.ui.pixelEdge
import nl.medicatietracker.ui.StreakBadge
import nl.medicatietracker.ui.StarCollectionDialog
import nl.medicatietracker.ui.TasksScreen
import nl.medicatietracker.ui.TodayScreen
import nl.medicatietracker.ui.rememberNow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MedApp
        setContent {
            val star by app.repository.heldStar.collectAsStateWithLifecycle(null)
            val pet by app.petRepository.pet.collectAsStateWithLifecycle(null)
            val now by rememberNow()
            // Zonder ster volgt de app de kleur van het blobje: groen, of rood als het honger heeft.
            val hungry = remember(pet, now) { pet?.mood() == PetMood.HONGER }
            MedTheme(
                when {
                    star != null -> AppTint.GOUD
                    hungry -> AppTint.PET_ROOD
                    else -> AppTint.PET_GROEN
                },
            ) {
                AppRoot(app, star)
            }
        }
    }
}

private const val TAB_TODAY = 0
private const val TAB_HISTORY = 1
private const val TAB_PET = 2
// Nieuwer tabblad; het nummer staat los van de volgorde onderaan.
private const val TAB_TASKS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(app: MedApp, star: StarEntry?) {
    var selected by rememberSaveable { mutableIntStateOf(TAB_TODAY) }
    val pet by app.petRepository.pet.collectAsStateWithLifecycle(null)
    val petName = pet?.displayName ?: "Blobje"
    var showStars by rememberSaveable { mutableStateOf(false) }
    var showTaskStars by rememberSaveable { mutableStateOf(false) }
    val now by rememberNow()
    val today = now.toLocalDate()
    val items by app.repository.allItems.collectAsStateWithLifecycle(emptyList())
    val logs by app.repository.allLogs.collectAsStateWithLifecycle(emptyList())
    val streakDays = remember(items, logs, now) {
        Streaks.compute(History.build(items, logs, History.firstDay(items, logs) ?: today, today, now)).current
    }
    val tasks by app.taskRepository.allTasks.collectAsStateWithLifecycle(emptyList())
    val taskLogs by app.taskRepository.logs.collectAsStateWithLifecycle(emptyList())
    val taskStreakWeeks = remember(tasks, taskLogs, now) { TaskRules.streak(tasks, taskLogs, now).weeks }
    val taskStar by app.taskRepository.heldStar.collectAsStateWithLifecycle(null)

    // Een reeks kan ook breken zonder dat er iets wordt afgevinkt, dus bij openen en elke nieuwe dag bijwerken.
    LaunchedEffect(today) {
        app.appScope.launch {
            app.repository.syncStars()
            app.taskRepository.syncStars()
        }
    }
    LifecycleResumeEffect(Unit) {
        app.appScope.launch {
            app.repository.syncStars()
            app.taskRepository.syncStars()
        }
        onPauseOrDispose { }
    }

    val edge = pixelEdge()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                // In het tabblad Taken staat rechtsboven de aparte takenreeks, in weken.
                actions = {
                    if (selected == TAB_TASKS) {
                        StreakBadge(taskStreakWeeks, taskStar, onClick = { showTaskStars = true }, singular = "week", plural = "weken", tasks = true)
                    } else {
                        StreakBadge(streakDays, star, onClick = { showStars = true })
                    }
                },
                modifier = Modifier.pixelEdgeLine(edge, top = false),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.pixelEdgeLine(edge, top = true)) {
                val navColors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                NavigationBarItem(
                    selected = selected == TAB_TODAY,
                    onClick = { selected = TAB_TODAY },
                    icon = { NavIcon(selected == TAB_TODAY, R.drawable.ic_px_home) },
                    label = { Text("Vandaag") },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = selected == TAB_TASKS,
                    onClick = { selected = TAB_TASKS },
                    icon = { NavIcon(selected == TAB_TASKS, R.drawable.ic_px_tasks) },
                    label = { Text("Taken") },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = selected == TAB_HISTORY,
                    onClick = { selected = TAB_HISTORY },
                    icon = { NavIcon(selected == TAB_HISTORY, R.drawable.ic_px_calendar) },
                    label = { Text("Medicijnen") },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = selected == TAB_PET,
                    onClick = { selected = TAB_PET },
                    colors = navColors,
                    icon = {
                        val mood = pet?.mood()
                        PixelTabButton(selected == TAB_PET) {
                            PixelImage(
                                when (mood) {
                                    PetMood.HONGER -> R.drawable.pet_blob_hungry_0
                                    PetMood.VERDRIETIG -> R.drawable.pet_blob_sad_0
                                    else -> R.drawable.pet_blob_happy_0
                                },
                                contentDescription = when (mood) {
                                    PetMood.HONGER, PetMood.VERDRIETIG -> "$petName ${mood?.label}"
                                    else -> null
                                },
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    },
                    label = { Text(petName, maxLines = 1) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                TAB_TODAY -> TodayScreen(app)
                TAB_TASKS -> TasksScreen(app, onOpenStars = { showTaskStars = true })
                TAB_HISTORY -> MedicationScreen(app, onOpenStars = { showStars = true })
                else -> PetScreen(app)
            }
        }
    }

    if (showStars) {
        StarCollectionDialog(app, onDismiss = { showStars = false })
    }
    if (showTaskStars) {
        StarCollectionDialog(app, onDismiss = { showTaskStars = false }, tasks = true)
    }
}

@Composable
private fun NavIcon(selected: Boolean, @DrawableRes icon: Int) {
    PixelTabButton(selected) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

/** Donkere pixelrand van 3dp boven- of onderlangs een balk. */
private fun Modifier.pixelEdgeLine(edge: Color, top: Boolean): Modifier = drawWithContent {
    drawContent()
    val u = 3.dp.toPx()
    drawRect(edge, Offset(0f, if (top) 0f else size.height - u), Size(size.width, u))
}
