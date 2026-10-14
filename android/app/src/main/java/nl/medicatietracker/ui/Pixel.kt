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

/** Tekent een 16-bit kader: donkere rand, lichte bovenkant links en schaduw rechtsonder. */
private fun DrawScope.pixelFrame(fill: Color, light: Color, shade: Color, unit: Float) {
    drawRect(Px.ink)
    drawRect(fill, Offset(unit, unit), Size(size.width - 2 * unit, size.height - 2 * unit))
    drawRect(light, Offset(unit, unit), Size(size.width - 2 * unit, unit))
    drawRect(light, Offset(unit, unit), Size(unit, size.height - 2 * unit))
    drawRect(shade, Offset(unit, size.height - 2 * unit), Size(size.width - 2 * unit, unit))
    drawRect(shade, Offset(size.width - 2 * unit, unit), Size(unit, size.height - 2 * unit))
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = if (enabled) fill else Px.slate
    Box(
        modifier
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .drawBehind {
                val u = 3.dp.toPx()
                if (pressed) pixelFrame(base, base.darken(), Color.White.copy(alpha = 0.3f), u)
                else pixelFrame(base, Color.White.copy(alpha = 0.35f), base.darken(), u)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(text, size = 9.sp, color = if (enabled) textColor else Px.silver, textAlign = TextAlign.Center)
    }
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
