package nl.medicatietracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.medicatietracker.data.History
import nl.medicatietracker.data.StarEntry
import nl.medicatietracker.data.Streaks
import nl.medicatietracker.ui.HistoryScreen
import nl.medicatietracker.ui.ManageScreen
import nl.medicatietracker.ui.MedTheme
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

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Vandaag", Icons.Filled.Home),
    Tab("Geschiedenis", Icons.Filled.DateRange),
    Tab("Beheren", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(app: MedApp, star: StarEntry?) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { StreakBadge(streakDays, star, onClick = { showStars = true }) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                0 -> TodayScreen(app)
                1 -> HistoryScreen(app)
                else -> ManageScreen(app, onOpenStars = { showStars = true })
            }
        }
    }

    if (showStars) {
        StarCollectionDialog(app, onDismiss = { showStars = false })
    }
}
