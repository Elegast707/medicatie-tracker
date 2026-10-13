package nl.medicatietracker

import nl.medicatietracker.data.History
import nl.medicatietracker.data.ItemType
import nl.medicatietracker.data.LogEntry
import nl.medicatietracker.data.LogStatus
import nl.medicatietracker.data.Streaks
import nl.medicatietracker.data.TrackedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreaksTest {
    private val today = LocalDate.of(2026, 10, 13)
    private val now = today.atTime(12, 0)

    private fun millis(date: LocalDate, hour: Int = 0) =
        date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // Overslaan van de pil breekt de reeks; overslaan van de wandeling niet.
    private val pill = TrackedItem(id = 1, name = "Pil", type = ItemType.MEDICATIE, minuteOfDay = 8 * 60, createdAt = millis(today.minusDays(9)))
    private val walk = TrackedItem(id = 2, name = "Wandelen", type = ItemType.TAAK, minuteOfDay = 18 * 60, skipAllowed = true, createdAt = millis(today.minusDays(9)))
    private val items = listOf(pill, walk)

    private fun log(item: TrackedItem, daysAgo: Long, status: LogStatus = LogStatus.GEDAAN): LogEntry {
        val date = today.minusDays(daysAgo)
        return LogEntry(
            itemId = item.id,
            epochDay = date.toEpochDay(),
            status = status,
            loggedAt = millis(date, 9),
            itemName = item.name,
            itemType = item.type,
            scheduledMinute = item.minuteOfDay,
        )
    }

    private fun bothDone(vararg daysAgo: Long) = daysAgo.flatMap { listOf(log(pill, it), log(walk, it)) }

    private fun streak(logs: List<LogEntry>) =
        Streaks.compute(History.build(items, logs, today.minusDays(9), today, now))

    @Test
    fun allowedSkipKeepsStreak_disallowedSkipBreaksIt() {
        val logs = bothDone(1, 2) +
            listOf(log(pill, 3), log(walk, 3, LogStatus.OVERGESLAGEN)) +
            listOf(log(pill, 4, LogStatus.OVERGESLAGEN), log(walk, 4))
        val info = streak(logs)
        assertEquals(3, info.current)
        assertEquals(today.minusDays(3), info.currentStart)
    }

    @Test
    fun todayCountsOnlyWhenComplete() {
        // Vandaag is alleen de pil gedaan; de wandeling staat nog open en breekt de reeks niet.
        assertEquals(2, streak(bothDone(1, 2) + log(pill, 0)).current)
        assertEquals(3, streak(bothDone(0, 1, 2)).current)
    }

    @Test
    fun missedDayResetsCurrentButKeepsBest() {
        // Dagen 8 t/m 5 compleet, dag 4 gemist, dagen 3 t/m 1 compleet.
        val info = streak(bothDone(8, 7, 6, 5, 3, 2, 1))
        assertEquals(3, info.current)
        assertEquals(4, info.best)
    }

    @Test
    fun noStreakWhenYesterdayMissed() {
        val info = streak(bothDone(3, 2))
        assertEquals(0, info.current)
        assertNull(info.currentStart)
        assertEquals(2, info.best)
    }

    @Test
    fun milestones() {
        listOf(7, 14, 21, 28, 30, 35, 90, 91, 180, 365).forEach { assertTrue("$it", Streaks.isMilestone(it)) }
        listOf(0, 1, 6, 8, 29, 31, 89).forEach { assertFalse("$it", Streaks.isMilestone(it)) }
        assertNull(Streaks.highestMilestoneUpTo(6))
        assertEquals(7, Streaks.highestMilestoneUpTo(10))
        assertEquals(30, Streaks.highestMilestoneUpTo(31))
        assertEquals("Een maand compleet!", Streaks.title(30))
        assertEquals("14 dagen op rij!", Streaks.title(14))
    }
}
