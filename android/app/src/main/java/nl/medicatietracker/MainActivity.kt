package nl.medicatietracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.medicatietracker.data.History
import nl.medicatietracker.data.PetMood
import nl.medicatietracker.data.StarEntry
import nl.medicatietracker.data.Streaks
import nl.medicatietracker.ui.HistoryScreen
import nl.medicatietracker.ui.ManageScreen
import nl.medicatietracker.ui.MedTheme
import nl.medicatietracker.ui.PetScreen
import nl.medicatietracker.ui.PixelImage
import nl.medicatietracker.ui.StreakBadge
import nl.medicatietracker.ui.StarCollectionDialog
import nl.medicatietracker.ui.TodayScreen
import nl.medicatietracker.ui.rememberNow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MedApp
        setContent {
            val star by app.repository.heldStar.collectAsStateWithLifecycle(null)
            MedTheme(gold = star != null) {
                AppRoot(app, star)
            }
        }
    }
}

private const val TAB_TODAY = 0
private const val TAB_HISTORY = 1
private const val TAB_PET = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(app: MedApp, star: StarEntry?) {
    var selected by rememberSaveable { mutableIntStateOf(TAB_TODAY) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pet by app.petRepository.pet.collectAsStateWithLifecycle(null)
    val petName = pet?.displayName ?: "Blobje"
    var showStars by rememberSaveable { mutableStateOf(false) }
    val now by rememberNow()
    val today = now.toLocalDate()
    val items by app.repository.allItems.collectAsStateWithLifecycle(emptyList())
    val logs by app.repository.allLogs.collectAsStateWithLifecycle(emptyList())
    val streakDays = remember(items, logs, now) {
        Streaks.compute(History.build(items, logs, History.firstDay(items, logs) ?: today, today, now)).current
    }

    // Een reeks kan ook breken zonder dat er iets wordt afgevinkt, dus bij openen en elke nieuwe dag bijwerken.
    LaunchedEffect(today) { app.appScope.launch { app.repository.syncStars() } }
    LifecycleResumeEffect(Unit) {
        app.appScope.launch { app.repository.syncStars() }
        onPauseOrDispose { }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    // Beheren zit in het zijmenu; het menu opent met de knop linksboven of door te vegen.
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ManageScreen(app, onOpenStars = {
                    scope.launch { drawerState.close() }
                    showStars = true
                })
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu: medicatie en taken beheren")
                        }
                    },
                    actions = { StreakBadge(streakDays, star, onClick = { showStars = true }) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selected == TAB_TODAY,
                        onClick = { selected = TAB_TODAY },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Vandaag") },
                    )
                    NavigationBarItem(
                        selected = selected == TAB_HISTORY,
                        onClick = { selected = TAB_HISTORY },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        label = { Text("Geschiedenis") },
                    )
                    NavigationBarItem(
                        selected = selected == TAB_PET,
                        onClick = { selected = TAB_PET },
                        icon = {
                            val mood = pet?.mood()
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
                        },
                        label = { Text(petName, maxLines = 1) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selected) {
                    TAB_TODAY -> TodayScreen(app)
                    TAB_HISTORY -> HistoryScreen(app)
                    else -> PetScreen(app)
                }
            }
        }
    }

    if (showStars) {
        StarCollectionDialog(app, onDismiss = { showStars = false })
    }
}
