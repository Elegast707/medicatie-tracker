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

// Goud zolang er een ster rechtsboven staat, anders de kleur van het blobje.
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

// Zonder ster: de kleuren van het blobje (Sweetie 16), groen en rood als het honger heeft.
private val PetGreenLight = lightColorScheme(
    primary = Color(0xFF1F7A45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F2A8),
    onPrimaryContainer = Color(0xFF0B2E12),
    secondaryContainer = Color(0xFFD4EFC4),
    onSecondaryContainer = Color(0xFF0F2A14),
    tertiaryContainer = Color(0xFFDDF5C0),
    onTertiaryContainer = Color(0xFF0B2E12),
    background = Color(0xFFF3FBEA),
    surface = Color(0xFFF3FBEA),
    surfaceVariant = Color(0xFFDCEBD0),
    surfaceContainerLow = Color(0xFFEEF8E3),
    surfaceContainer = Color(0xFFE8F3DC),
    surfaceContainerHigh = Color(0xFFE2EED6),
    surfaceContainerHighest = Color(0xFFDCEBD0),
)

private val PetGreenDark = darkColorScheme(
    primary = Color(0xFFA7F070),
    onPrimary = Color(0xFF0B2E12),
    primaryContainer = Color(0xFF1F5A30),
    onPrimaryContainer = Color(0xFFC9F2A8),
    secondaryContainer = Color(0xFF2C4A30),
    onSecondaryContainer = Color(0xFFD4EFC4),
    tertiaryContainer = Color(0xFF1F5A30),
    onTertiaryContainer = Color(0xFFC9F2A8),
    background = Color(0xFF0E1A10),
    surface = Color(0xFF0E1A10),
    surfaceVariant = Color(0xFF34483A),
    surfaceContainerLow = Color(0xFF142317),
    surfaceContainer = Color(0xFF182A1B),
    surfaceContainerHigh = Color(0xFF213525),
    surfaceContainerHighest = Color(0xFF2B402F),
)

private val PetRedLight = lightColorScheme(
    primary = Color(0xFFA3354A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD3C4),
    onPrimaryContainer = Color(0xFF3A0D18),
    secondaryContainer = Color(0xFFF7D9D2),
    onSecondaryContainer = Color(0xFF3A1218),
    tertiaryContainer = Color(0xFFFFE0D2),
    onTertiaryContainer = Color(0xFF3A0D18),
    background = Color(0xFFFFF3EF),
    surface = Color(0xFFFFF3EF),
    surfaceVariant = Color(0xFFF0DAD4),
    surfaceContainerLow = Color(0xFFFCEEE9),
    surfaceContainer = Color(0xFFF8E6E0),
    surfaceContainerHigh = Color(0xFFF4E0DA),
    surfaceContainerHighest = Color(0xFFF0DAD4),
)

private val PetRedDark = darkColorScheme(
    primary = Color(0xFFFF9C7E),
    onPrimary = Color(0xFF4A1020),
    primaryContainer = Color(0xFF6E2236),
    onPrimaryContainer = Color(0xFFFFD3C4),
    secondaryContainer = Color(0xFF553036),
    onSecondaryContainer = Color(0xFFF7D9D2),
    tertiaryContainer = Color(0xFF6E2236),
    onTertiaryContainer = Color(0xFFFFD3C4),
    background = Color(0xFF1C0F12),
    surface = Color(0xFF1C0F12),
    surfaceVariant = Color(0xFF4D3A3C),
    surfaceContainerLow = Color(0xFF241418),
    surfaceContainer = Color(0xFF2A181C),
    surfaceContainerHigh = Color(0xFF352226),
    surfaceContainerHighest = Color(0xFF402C30),
)

/** Kleur van de app: goud met een ster, anders de kleur van het blobje. */
enum class AppTint { GOUD, PET_GROEN, PET_ROOD }

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
fun MedTheme(tint: AppTint, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = when (tint) {
            AppTint.GOUD -> if (dark) GoldDark else GoldLight
            AppTint.PET_GROEN -> if (dark) PetGreenDark else PetGreenLight
            AppTint.PET_ROOD -> if (dark) PetRedDark else PetRedLight
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
