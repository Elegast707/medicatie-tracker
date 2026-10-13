package nl.medicatietracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.medicatietracker.data.RowStatus
import java.time.LocalDateTime

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B2A9E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
)

@Composable
fun MedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

@Composable
fun StatusIcon(status: RowStatus, modifier: Modifier = Modifier) {
    when (status) {
        RowStatus.GEDAAN -> Icon(Icons.Filled.Check, contentDescription = "Gedaan", tint = successColor(), modifier = modifier)
        RowStatus.OVERGESLAGEN -> Icon(Icons.Filled.Close, contentDescription = "Overgeslagen", tint = MaterialTheme.colorScheme.outline, modifier = modifier)
        RowStatus.GEMIST, RowStatus.TE_LAAT -> Icon(Icons.Filled.Warning, contentDescription = "Niet gedaan", tint = MaterialTheme.colorScheme.error, modifier = modifier)
        RowStatus.OPEN -> Icon(Icons.Filled.Check, contentDescription = "Gepland", tint = MaterialTheme.colorScheme.outlineVariant, modifier = modifier)
    }
}

/** Huidige tijd, elke 30 seconden ververst; zo wisselt "vandaag" vanzelf om middernacht. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        delay(30_000)
        value = LocalDateTime.now()
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
