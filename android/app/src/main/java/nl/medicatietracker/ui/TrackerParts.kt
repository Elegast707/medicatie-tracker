package nl.medicatietracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.medicatietracker.R
import nl.medicatietracker.data.DUTCH
import nl.medicatietracker.data.Summary
import nl.medicatietracker.data.Weekdays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/*
 * Bouwstenen die de pagina's Medicijnen en Taken delen, zodat ze er hetzelfde uitzien: het blok met de
 * reeks en badges, de meter met hoeveel er is afgevinkt, en de maandkalender.
 */

internal val trackerDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", DUTCH)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", DUTCH)

internal val SUMMARY_RANGES = listOf(7 to "7 dagen", 30 to "30 dagen", 90 to "90 dagen")

/** Keuze voor de periode van de meter. */
@Composable
internal fun RangeChips(rangeDays: Int, onChange: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SUMMARY_RANGES.forEach { (days, label) ->
            FilterChip(selected = rangeDays == days, onClick = { onChange(days) }, label = { Text(label) })
        }
    }
}

/** Een badge zoals hij in het blok getoond wordt. */
internal data class BadgeView(val label: String, val earned: Boolean, val progress: String)

/**
 * Het blok met de huidige reeks, de beste reeks en de badges. Tikken opent de sterrenverzameling.
 */
@Composable
internal fun AchievementsCard(
    current: Int,
    currentLabel: String,
    best: Int,
    bestLabel: String,
    badges: List<BadgeView>,
    hint: String?,
    onClick: () -> Unit,
) {
    PixelCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("$current", style = MaterialTheme.typography.headlineMedium)
                    Text(currentLabel, style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f)) {
                    Text("$best", style = MaterialTheme.typography.headlineMedium)
                    Text(bestLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.forEach { BadgeTile(it, Modifier.weight(1f)) }
            }
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: BadgeView, modifier: Modifier = Modifier) {
    Surface(
        shape = CutCornerShape(3.dp),
        color = if (badge.earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (badge.earned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (badge.earned) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (badge.earned) {
                Image(painterResource(R.drawable.ic_star_gold), contentDescription = "Behaald", modifier = Modifier.size(24.dp))
            } else {
                Text(badge.progress, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Text(badge.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** De meter: welk deel in de gekozen periode is afgevinkt, ook per medicijn of taak. */
@Composable
internal fun SummaryCard(summary: Summary, showSkipped: Boolean = true) {
    PixelCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text("${summary.percentage}%", style = MaterialTheme.typography.headlineMedium)
                Text("afgevinkt in deze periode", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (showSkipped) {
                    "${summary.done} gedaan · ${summary.skipped} overgeslagen · ${summary.missed} gemist"
                } else {
                    "${summary.done} gedaan · ${summary.missed} gemist"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary.perItem.forEach { stats ->
                Column {
                    Row {
                        Text(stats.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${stats.done}/${stats.total} · ${stats.percentage}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    PixelProgressBar(
                        progress = stats.percentage / 100f,
                        modifier = Modifier.padding(top = 4.dp),
                        segments = 10,
                    )
                }
            }
        }
    }
}

/** Hoeveel er op een dag stond en hoeveel daarvan is afgevinkt. */
internal data class DayCount(val done: Int, val total: Int)

/**
 * Maandkalender in pixelstijl. Elke dag laat zien hoeveel er staat en hoeveel er is afgevinkt: groen
 * als alles gedaan is, rood als er in het verleden iets is blijven liggen. Tik op een dag om die dag te
 * bekijken; tik op de maand om terug te gaan naar vandaag.
 */
@Composable
internal fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    countFor: (LocalDate) -> DayCount,
    noun: String,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val weeks = (offset + month.lengthOfMonth() + 6) / 7
    PixelCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelButton("<", onClick = { onMonthChange(month.minusMonths(1)) })
                Text(
                    month.format(monthFormatter).replaceFirstChar { it.uppercase(DUTCH) },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable {
                        onMonthChange(YearMonth.from(today))
                        onSelect(today)
                    },
                )
                PixelButton(">", onClick = { onMonthChange(month.plusMonths(1)) })
            }
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        Weekdays.shortLabel(day),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            for (week in 0 until weeks) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (col in 0 until 7) {
                        val dayOfMonth = week * 7 + col - offset + 1
                        if (dayOfMonth < 1 || dayOfMonth > month.lengthOfMonth()) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val date = month.atDay(dayOfMonth)
                            val count = countFor(date)
                            CalendarDay(
                                date = date,
                                total = count.total,
                                done = count.done,
                                noun = noun,
                                isToday = date == today,
                                isSelected = date == selected,
                                isPast = date.isBefore(today),
                                onClick = { onSelect(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    total: Int,
    done: Int,
    noun: String,
    isToday: Boolean,
    isSelected: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        isSelected -> scheme.primary
        total > 0 && done == total -> DoneGreen
        total > 0 && isPast -> scheme.errorContainer
        total > 0 -> scheme.primaryContainer
        else -> scheme.surface
    }
    val text = when {
        isSelected -> scheme.onPrimary
        total > 0 && done == total -> Color.White
        total > 0 && isPast -> scheme.onErrorContainer
        total > 0 -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val edge = if (isToday) scheme.primary else pixelEdge()
    val light = lerp(fill, Color.White, 0.35f)
    val shade = lerp(fill, pixelEdge(), 0.25f)
    val description = buildString {
        append(date.format(trackerDayFormatter))
        if (total > 0) append(", $done van $total $noun afgevinkt") else append(", niets gepland")
        if (isToday) append(", vandaag")
    }
    Column(
        modifier
            .height(44.dp)
            .drawBehind { pixelFrame(fill, light, shade, (if (isToday) 3 else 2).dp.toPx(), edge) }
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = text, fontWeight = if (isToday) FontWeight.Bold else null)
        if (total > 0) {
            Text(if (done == total) "✓" else "$done/$total", style = MaterialTheme.typography.labelSmall, color = text)
        }
    }
}

/** Kopje boven de lijst van de gekozen dag. */
@Composable
internal fun SelectedDayHeader(selected: LocalDate, today: LocalDate, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected == today) "Vandaag" else selected.format(trackerDayFormatter).replaceFirstChar { it.uppercase(DUTCH) },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}
