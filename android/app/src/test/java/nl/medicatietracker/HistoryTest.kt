package nl.medicatietracker

import nl.medicatietracker.data.History
import nl.medicatietracker.data.ItemType
import nl.medicatietracker.data.LogEntry
import nl.medicatietracker.data.LogStatus
import nl.medicatietracker.data.RowStatus
import nl.medicatietracker.data.TrackedItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryTest {
    private val today = LocalDate.of(2026, 10, 12)
    private val now = today.atTime(12, 0)

    private fun millis(date: LocalDate, hour: Int = 0) =
        date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val pill = TrackedItem(id = 1, name = "Pil", type = ItemType.MEDICATIE, minuteOfDay = 8 * 60, createdAt = millis(today.minusDays(5)))
    private val walk = TrackedItem(id = 2, name = "Wandelen", type = ItemType.TAAK, minuteOfDay = 18 * 60, createdAt = millis(today.minusDays(5)))

    private fun log(item: TrackedItem, date: LocalDate, status: LogStatus = LogStatus.GEDAAN) = LogEntry(
        itemId = item.id,
        epochDay = date.toEpochDay(),
        status = status,
        loggedAt = millis(date, 9),
        itemName = item.name,
        itemType = item.type,
        scheduledMinute = item.minuteOfDay,
    )

    @Test
    fun statusesPerDay() {
        val logs = listOf(log(pill, today), log(pill, today.minusDays(1)), log(walk, today.minusDays(1), LogStatus.OVERGESLAGEN))
        val days = History.build(listOf(pill, walk), logs, today.minusDays(2), today, now)

        assertEquals(listOf(today, today.minusDays(1), today.minusDays(2)), days.map { it.date })
        assertEquals(listOf(RowStatus.GEDAAN, RowStatus.OPEN), days[0].rows.map { it.status })
        assertEquals(listOf(RowStatus.GEDAAN, RowStatus.OVERGESLAGEN), days[1].rows.map { it.status })
        assertEquals(listOf(RowStatus.GEMIST, RowStatus.GEMIST), days[2].rows.map { it.status })
    }

    @Test
    fun overdueToday() {
        val days = History.build(listOf(pill), emptyList(), today, today, now)
        assertEquals(RowStatus.TE_LAAT, days.single().rows.single().status)
    }

    @Test
    fun itemsOnlyExpectedWhileTheyExist() {
        val deleted = walk.copy(deletedAt = millis(today.minusDays(1), 10))
        val days = History.build(listOf(deleted), emptyList(), today.minusDays(10), today, now)
        val withRows = days.filter { it.rows.isNotEmpty() }.map { it.date }
        // Aangemaakt 5 dagen geleden, gisteren verwijderd.
        assertEquals((2..5).map { today.minusDays(it.toLong()) }, withRows)
    }

    @Test
    fun summaryCounts() {
        val logs = listOf(
            log(pill, today.minusDays(1)), log(walk, today.minusDays(1)),
            log(pill, today.minusDays(2)), log(walk, today.minusDays(2), LogStatus.OVERGESLAGEN),
            log(pill, today.minusDays(3)),
        )
        val days = History.build(listOf(pill, walk), logs, today.minusDays(5), today, now)
        val summary = History.summarize(days)
        assertEquals(4, summary.done)
        assertEquals(1, summary.skipped)
        assertEquals(5, summary.missed)
    }

    @Test
    fun csvEscapesSeparators() {
        val item = pill.copy(name = "Pil; 5mg")
        val csv = History.toCsv(History.build(listOf(item), listOf(log(item, today)), today, today, LocalDateTime.of(today, now.toLocalTime())))
        assertEquals("2026-10-12;08:00;\"Pil; 5mg\";Medicatie;Genomen;2026-10-12 09:00", csv.lines()[1])
    }
}
