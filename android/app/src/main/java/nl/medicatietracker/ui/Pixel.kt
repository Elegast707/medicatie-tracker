package nl.medicatietracker.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import nl.medicatietracker.R

/** Sweetie 16-palet, hetzelfde als in tools/generate_sprites.py. */
object Px {
    val ink = Color(0xFF1A1C2C)
    val plum = Color(0xFF5D275D)
    val red = Color(0xFFB13E53)
    val orange = Color(0xFFEF7D57)
    val yellow = Color(0xFFFFCD75)
    val lime = Color(0xFFA7F070)
    val green = Color(0xFF38B764)
    val teal = Color(0xFF257179)
    val navy = Color(0xFF29366F)
    val blue = Color(0xFF3B5DC9)
    val sky = Color(0xFF41A6F6)
    val cyan = Color(0xFF73EFF7)
    val white = Color(0xFFF4F4F4)
    val silver = Color(0xFF94B0C2)
    val slate = Color(0xFF566C86)
    val night = Color(0xFF333C57)
}

val PixelFont = FontFamily(Font(R.font.press_start_2p))

/** Rustiger pixelfont voor gewone tekst; leest beter dan Press Start 2P op kleine maat. */
val PixelBodyFont = FontFamily(
    Font(R.font.pixelify_sans_regular, FontWeight.Normal),
    Font(R.font.pixelify_sans_semibold, FontWeight.Medium),
    Font(R.font.pixelify_sans_semibold, FontWeight.SemiBold),
    Font(R.font.pixelify_sans_semibold, FontWeight.Bold),
)

@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 10.sp,
    color: Color = Px.ink,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        style = TextStyle(fontFamily = PixelFont, fontSize = size, lineHeight = size * 1.6f),
    )
}

/**
 * Tekent een pixelkader: donkere rand zonder hoekpixels, lichte bovenkant links en schaduw rechtsonder.
 * De weggelaten hoekpixels geven de typische afgeronde hoek van pixelart.
 */
internal fun DrawScope.pixelFrame(fill: Color, light: Color, shade: Color, unit: Float, edge: Color = Px.ink) {
    val w = size.width
    val h = size.height
    drawRect(edge, Offset(unit, 0f), Size(w - 2 * unit, h))
    drawRect(edge, Offset(0f, unit), Size(w, h - 2 * unit))
    drawRect(fill, Offset(unit, unit), Size(w - 2 * unit, h - 2 * unit))
    drawRect(light, Offset(unit, unit), Size(w - 2 * unit, unit))
    drawRect(light, Offset(unit, unit), Size(unit, h - 2 * unit))
    drawRect(shade, Offset(unit, h - 2 * unit), Size(w - 2 * unit, unit))
    drawRect(shade, Offset(w - 2 * unit, unit), Size(unit, h - 2 * unit))
}

@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    fill: Color = Px.white,
    light: Color = Color.White,
    shade: Color = Px.silver,
    pixel: Dp = 3.dp,
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .drawBehind { pixelFrame(fill, light, shade, pixel.toPx()) }
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Color = Px.green,
    textColor: Color = Px.white,
) {
    PixelButton(onClick, modifier, enabled, fill, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        PixelText(text, size = 9.sp, color = if (enabled) textColor else Px.silver, textAlign = TextAlign.Center)
    }
}

/** Pixelknop met eigen inhoud; zakt een pixel in als je erop drukt. */
@Composable
fun PixelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Color = Px.green,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = if (enabled) fill else Px.slate
    Row(
        modifier
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                val u = 3.dp.toPx()
                if (pressed) pixelFrame(base, base.darken(), Color.White.copy(alpha = 0.3f), u)
                else pixelFrame(base, Color.White.copy(alpha = 0.35f), base.darken(), u)
            }
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Randkleur van pixelkaders in de app-thema's: zwart in donker, bijna zwart in licht. */
@Composable
fun pixelEdge(): Color = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.onSurface

/** Kaart in pixelstijl, in de kleuren van het huidige thema. Vervangt de ronde Material-kaart. */
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable ColumnScope.() -> Unit,
) {
    val edge = pixelEdge()
    val light = lerp(container, Color.White, if (isSystemInDarkTheme()) 0.12f else 0.6f)
    val shade = lerp(container, edge, 0.2f)
    val contentColor = MaterialTheme.colorScheme.contentColorFor(container).takeOrElse { LocalContentColor.current }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier.drawBehind { pixelFrame(container, light, shade, 3.dp.toPx(), edge) },
            content = content,
        )
    }
}

/** Voortgangsbalk in blokjes, zoals een levensbalk in een game. */
@Composable
fun PixelProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    segments: Int = 12,
    fill: Color = MaterialTheme.colorScheme.primary,
) {
    val edge = pixelEdge()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val value = progress.coerceIn(0f, 1f)
    val on = when {
        value <= 0f -> 0
        value >= 1f -> segments
        else -> (value * segments).roundToInt().coerceIn(1, segments - 1)
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(18.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
    ) {
        val u = 3.dp.toPx()
        drawRect(edge)
        val seg = (size.width - 2 * u - u * (segments - 1)) / segments
        for (i in 0 until segments) {
            val x = u + i * (seg + u)
            drawRect(if (i < on) fill else track, Offset(x, u), Size(seg, size.height - 2 * u))
            if (i < on) drawRect(Color.White.copy(alpha = 0.35f), Offset(x, u), Size(seg, u))
        }
    }
}

/** Selectiekader om het icoontje van het actieve tabblad, in plaats van het ronde Material-bolletje. */
@Composable
fun PixelSelection(selected: Boolean, content: @Composable () -> Unit) {
    val fill = MaterialTheme.colorScheme.primaryContainer
    val edge = pixelEdge()
    val light = lerp(fill, Color.White, 0.45f)
    val shade = lerp(fill, edge, 0.25f)
    Box(
        Modifier
            .then(if (selected) Modifier.drawBehind { pixelFrame(fill, light, shade, 2.dp.toPx(), edge) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun Color.darken(): Color = Color(red * 0.7f, green * 0.7f, blue * 0.7f, alpha)

/** Pixelart scherp vergroot, zonder vervaging. */
@Composable
fun PixelImage(@DrawableRes res: Int, contentDescription: String?, modifier: Modifier = Modifier) {
    Image(
        bitmap = ImageBitmap.imageResource(res),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}
