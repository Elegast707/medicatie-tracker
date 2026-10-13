package nl.medicatietracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.medicatietracker.MedApp
import nl.medicatietracker.R
import nl.medicatietracker.data.DUTCH
import nl.medicatietracker.data.StarEntry
import nl.medicatietracker.data.StarReason
import nl.medicatietracker.data.StarTier
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val starDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", DUTCH)

private fun Long.formatDay(): String = LocalDate.ofEpochDay(this).format(starDateFormatter)

/**
 * Rechtsboven: het aantal dagen op rij, met de ster ernaast. Loopt er een reeks maar is er nog geen
 * ster, dan staat er een gouden vinkje. Tikken opent de verzameling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakBadge(days: Int, star: StarEntry?, onClick: () -> Unit) {
    if (days < 1 && star == null) return
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "$days",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (days == 1) "dag" else "dagen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Image(
                painter = painterResource(star?.tier?.drawable ?: R.drawable.ic_check_gold),
                contentDescription = (star?.tier?.label ?: "Reeks loopt") + ", tik voor je verzameling",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarCollectionDialog(app: MedApp, onDismiss: () -> Unit) {
    val held by app.repository.heldStar.collectAsStateWithLifecycle(null)
    val collected by app.repository.collectedStars.collectAsStateWithLifecycle(emptyList())

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sterrencollectie") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Sluiten") }
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { CurrentStarCard(held) }
                item { TierCounts(collected) }
                if (collected.isEmpty()) {
                    item {
                        Text(
                            "Nog geen sterren verzameld. Een ster komt in je verzameling zodra hij " +
                                "vervangen wordt door een hogere, of als je reeks breekt.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(collected, key = { it.id }) { star ->
                    ListItem(
                        leadingContent = {
                            Image(painterResource(star.tier.drawable), contentDescription = null, modifier = Modifier.size(36.dp))
                        },
                        headlineContent = { Text(star.tier.label) },
                        supportingContent = {
                            val end = star.collectedOn?.formatDay().orEmpty()
                            val how = if (star.reason == StarReason.GEBROKEN) "reeks gebroken op $end" else "vervangen op $end"
                            Text("Behaald ${star.earnedOn.formatDay()} · $how")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentStarCard(held: StarEntry?) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (held != null) {
                Image(painterResource(held.tier.drawable), contentDescription = null, modifier = Modifier.size(56.dp))
                Column {
                    Text(held.tier.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Sinds ${held.earnedOn.formatDay()}", style = MaterialTheme.typography.bodyMedium)
                    val next = StarTier.entries.firstOrNull { it > held.tier }
                    if (next != null) {
                        Text(
                            "Bij ${next.days} dagen op rij krijg je een ${next.label.lowercase(DUTCH)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    "Je hebt nu geen ster. Vink ${StarTier.BRONS.days} dagen op rij alles af voor een ${StarTier.BRONS.label.lowercase(DUTCH)}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TierCounts(collected: List<StarEntry>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        StarTier.entries.forEach { tier ->
            val count = collected.count { it.tier == tier }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(tier.drawable),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).alpha(if (count > 0) 1f else 0.3f),
                )
                Text("× $count", style = MaterialTheme.typography.titleSmall)
                Text("${tier.days} dagen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
