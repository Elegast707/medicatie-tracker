package nl.medicatietracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.medicatietracker.R
import kotlinx.coroutines.delay
import nl.medicatietracker.data.RowStatus
import java.time.LocalDateTime

// Goud zolang er een ster rechtsboven staat, anders grijs.
private val GoldLight = lightColorScheme(
    primary = Color(0xFF8A6100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF2B1D00),
    secondaryContainer = Color(0xFFF5E1B8),
    onSecondaryContainer = Color(0xFF261A00),
    tertiaryContainer = Color(0xFFFFE8B0),
    onTertiaryContainer = Color(0xFF2B1D00),
    background = Color(0xFFFFF8EC),
    surface = Color(0xFFFFF8EC),
    surfaceVariant = Color(0xFFF0E2C8),
    surfaceContainerLow = Color(0xFFFCF2E0),
    surfaceContainer = Color(0xFFF8EBD0),
    surfaceContainerHigh = Color(0xFFF4E6CC),
    surfaceContainerHighest = Color(0xFFF0E2C8),
)

private val GoldDark = darkColorScheme(
    primary = Color(0xFFF5C451),
    onPrimary = Color(0xFF412D00),
    primaryContainer = Color(0xFF5E4200),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondaryContainer = Color(0xFF54452A),
    onSecondaryContainer = Color(0xFFF5E1B8),
    tertiaryContainer = Color(0xFF5E4200),
    onTertiaryContainer = Color(0xFFFFDF9E),
    background = Color(0xFF18130A),
    surface = Color(0xFF18130A),
    surfaceVariant = Color(0xFF4D4639),
    surfaceContainerLow = Color(0xFF201B10),
    surfaceContainer = Color(0xFF251F14),
    surfaceContainerHigh = Color(0xFF30291E),
    surfaceContainerHighest = Color(0xFF3B3428),
)

private val GreyLight = lightColorScheme(
    primary = Color(0xFF5F6368),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E3E6),
    onPrimaryContainer = Color(0xFF1F2124),
    secondaryContainer = Color(0xFFE3E3E3),
    onSecondaryContainer = Color(0xFF1F1F1F),
    tertiaryContainer = Color(0xFFE6E6E6),
    onTertiaryContainer = Color(0xFF1F1F1F),
    background = Color(0xFFF7F7F7),
    surface = Color(0xFFF7F7F7),
    surfaceVariant = Color(0xFFE2E2E2),
    surfaceContainerLow = Color(0xFFF2F2F2),
    surfaceContainer = Color(0xFFEDEDED),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE2E2E2),
)

private val GreyDark = darkColorScheme(
    primary = Color(0xFFC4C7CB),
    onPrimary = Color(0xFF2E3134),
    primaryContainer = Color(0xFF45484C),
    onPrimaryContainer = Color(0xFFE1E3E6),
    secondaryContainer = Color(0xFF444444),
    onSecondaryContainer = Color(0xFFE3E3E3),
    tertiaryContainer = Color(0xFF444444),
    onTertiaryContainer = Color(0xFFE3E3E3),
    background = Color(0xFF141414),
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF444444),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF202020),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF353535),
)

/**
 * 32-bit pixelstijl: koppen in Press Start 2P, gewone tekst in Pixelify Sans. Pixelfonts lezen kleiner
 * dan gewone letters, dus de tekstmaten liggen iets hoger dan de Material-standaard.
 */
private fun pixelTypography(): Typography {
    val base = Typography()
    fun TextStyle.body(size: Int, line: Int) = copy(fontFamily = PixelBodyFont, fontSize = size.sp, lineHeight = line.sp)
    fun TextStyle.display(size: Int) = copy(fontFamily = PixelFont, fontSize = size.sp, lineHeight = (size * 1.5f).sp, fontWeight = FontWeight.Normal)
    return base.copy(
        displayLarge = base.displayLarge.display(32),
        displayMedium = base.displayMedium.display(26),
        displaySmall = base.displaySmall.display(22),
        headlineLarge = base.headlineLarge.display(20),
        headlineMedium = base.headlineMedium.display(18),
        headlineSmall = base.headlineSmall.display(16),
        titleLarge = base.titleLarge.display(14),
        titleMedium = base.titleMedium.body(19, 24),
        titleSmall = base.titleSmall.body(16, 22),
        bodyLarge = base.bodyLarge.body(18, 24),
        bodyMedium = base.bodyMedium.body(16, 22),
        bodySmall = base.bodySmall.body(14, 19),
        labelLarge = base.labelLarge.body(16, 20),
        labelMedium = base.labelMedium.body(14, 18),
        labelSmall = base.labelSmall.body(13, 16),
    )
}

/** Afgeschuinde hoeken in plaats van ronde, zodat dialogen, velden en chips bij de pixelkaders passen. */
private val PixelShapes = Shapes(
    extraSmall = CutCornerShape(2.dp),
    small = CutCornerShape(3.dp),
    medium = CutCornerShape(4.dp),
    large = CutCornerShape(6.dp),
    extraLarge = CutCornerShape(8.dp),
)

private val PixelType = pixelTypography()

@Composable
fun MedTheme(gold: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = when {
            gold && dark -> GoldDark
            gold -> GoldLight
            dark -> GreyDark
            else -> GreyLight
        },
        typography = PixelType,
        shapes = PixelShapes,
        content = content,
    )
}

@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

@Composable
fun StatusIcon(status: RowStatus, modifier: Modifier = Modifier) {
    when (status) {
        RowStatus.GEDAAN -> Icon(painterResource(R.drawable.ic_px_check), contentDescription = "Gedaan", tint = successColor(), modifier = modifier)
        RowStatus.OVERGESLAGEN -> Icon(painterResource(R.drawable.ic_px_close), contentDescription = "Overgeslagen", tint = MaterialTheme.colorScheme.outline, modifier = modifier)
        RowStatus.GEMIST, RowStatus.TE_LAAT -> Icon(painterResource(R.drawable.ic_px_warning), contentDescription = "Niet gedaan", tint = MaterialTheme.colorScheme.error, modifier = modifier)
        RowStatus.OPEN -> Icon(painterResource(R.drawable.ic_px_check), contentDescription = "Gepland", tint = MaterialTheme.colorScheme.outlineVariant, modifier = modifier)
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
