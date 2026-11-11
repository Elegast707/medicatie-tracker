package nl.medicatietracker

import nl.medicatietracker.data.StarTier
import nl.medicatietracker.data.StarTrack
import nl.medicatietracker.data.Stars
import nl.medicatietracker.data.TaskItem
import nl.medicatietracker.data.TaskLog
import nl.medicatietracker.data.TaskRecurrence
import nl.medicatietracker.data.TaskRules
import nl.medicatietracker.data.TaskStreak
import nl.medicatietracker.data.TaskStreaks
import nl.medicatietracker.reminders.TaskReminderScheduler
import nl.medicatietracker.data.Weekdays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TasksTest {
    /** Donderdag 1 januari 2026. */
    private val start = LocalDate.of(2026, 1, 1)

    private fun millis(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        recurrence: TaskRecurrence,
        startDay: LocalDate = start,
        weekdays: Int = 0,
        intervalDays: Int = 2,
        deletedOn: LocalDate? = null,
    ) = TaskItem(
        id = id,
        name = "Taak $id",
        recurrence = recurrence,
        startDay = startDay.toEpochDay(),
        weekdays = weekdays,
        intervalDays = intervalDays,
        createdAt = millis(start),
        deletedAt = deletedOn?.let { millis(it) + 3_600_000 },
    )

    private fun done(id: Long, vararg dates: LocalDate) = dates.map { TaskLog(id, it.toEpochDay(), millis(it), "Taak $id") }

    @Test
    fun oneOffTaskOnlyOnItsDay() {
        val t = task(1, TaskRecurrence.EENMALIG, startDay = start.plusDays(3))
        assertFalse(t.occursOn(start))
        assertTrue(t.occursOn(start.plusDays(3)))
        assertFalse(t.occursOn(start.plusDays(4)))
    }

    @Test
    fun weekdayTaskFollowsChosenDays() {
        val mask = Weekdays.bit(DayOfWeek.MONDAY) or Weekdays.bit(DayOfWeek.THURSDAY)
        val t = task(1, TaskRecurrence.WEEKDAGEN, weekdays = mask)
        assertTrue(t.occursOn(start)) // donderdag
        assertFalse(t.occursOn(start.plusDays(1))) // vrijdag
        assertTrue(t.occursOn(start.plusDays(4))) // maandag
    }

    @Test
    fun intervalTaskCountsFromStart() {
        val t = task(1, TaskRecurrence.INTERVAL, intervalDays = 3)
        assertTrue(t.occursOn(start))
        assertFalse(t.occursOn(start.plusDays(1)))
        assertTrue(t.occursOn(start.plusDays(3)))
        assertTrue(t.occursOn(start.plusDays(6)))
    }

    @Test
    fun taskDoesNotCountBeforeCreationOrAfterDeletion() {
        val t = task(1, TaskRecurrence.DAGELIJKS, startDay = start.minusDays(10), deletedOn = start.plusDays(2))
        assertFalse(t.occursOn(start.minusDays(1)))
        assertTrue(t.occursOn(start.plusDays(1)))
        assertFalse(t.occursOn(start.plusDays(2)))
    }

    /** Maandag 5 januari 2026. */
    private val monday = LocalDate.of(2026, 1, 5)

    @Test
    fun weekWithOnlyTuesdayAndThursdayIsCompleteWhenBothAreDone() {
        val mask = Weekdays.bit(DayOfWeek.TUESDAY) or Weekdays.bit(DayOfWeek.THURSDAY)
        val tasks = listOf(task(1, TaskRecurrence.WEEKDAGEN, weekdays = mask))
        val logs = done(1, monday.plusDays(1), monday.plusDays(3))
        // Vrijdag: de rest van de week staat niets meer, dus de week is al voltooid.
        val streak = TaskRules.streak(tasks, logs, monday.plusDays(4).atTime(LocalTime.NOON))
        assertEquals(1, streak.weeks)
        assertEquals(1, streak.bestWeeks)
    }

    @Test
    fun weekIsNotCompleteWhileTasksLaterInTheWeekAreOpen() {
        val mask = Weekdays.bit(DayOfWeek.MONDAY) or Weekdays.bit(DayOfWeek.SUNDAY)
        val tasks = listOf(task(1, TaskRecurrence.WEEKDAGEN, weekdays = mask))
        val streak = TaskRules.streak(tasks, done(1, monday), monday.plusDays(2).atTime(LocalTime.NOON))
        assertEquals(0, streak.weeks)
        assertEquals(1, streak.weekDone)
        assertEquals(2, streak.weekTotal)
    }

    @Test
    fun missedTaskOnMondayMeansNoWeekBadge() {
        val mask = Weekdays.bit(DayOfWeek.MONDAY) or Weekdays.bit(DayOfWeek.SUNDAY)
        val tasks = listOf(task(1, TaskRecurrence.WEEKDAGEN, weekdays = mask))
        val streak = TaskRules.streak(tasks, done(1, monday.plusDays(6)), monday.plusDays(6).atTime(LocalTime.NOON))
        assertEquals(0, streak.weeks)
        assertEquals(0, streak.bestWeeks)
        assertEquals(monday.plusDays(1), streak.runStart)
    }

    @Test
    fun weekWithoutTasksDoesNotBreakTheStreak() {
        // Week 1 en week 3 een taak op maandag, week 2 niets.
        val tasks = listOf(
            task(1, TaskRecurrence.EENMALIG, startDay = monday),
            task(2, TaskRecurrence.EENMALIG, startDay = monday.plusDays(14)),
        )
        val logs = done(1, monday) + done(2, monday.plusDays(14))
        val streak = TaskRules.streak(tasks, logs, monday.plusDays(15).atTime(LocalTime.NOON))
        assertEquals(2, streak.weeks)
    }

    @Test
    fun fullMonthGivesTheMonthBadgeAndSilverStar() {
        // Elke dag een taak vanaf 1 januari, alles gedaan t/m 31 januari.
        val tasks = listOf(task(1, TaskRecurrence.DAGELIJKS))
        val logs = done(1, *(0L..30L).map { start.plusDays(it) }.toTypedArray())
        val streak = TaskRules.streak(tasks, logs, LocalDate.of(2026, 2, 1).atTime(8, 0))
        assertEquals(1, streak.months)
        // 1 jan (do) t/m 4 jan telt als week, daarna 5-11, 12-18, 19-25; 26 jan - 1 feb loopt nog.
        assertEquals(4, streak.weeks)
        assertTrue(TaskStreaks.BADGES[1].earned(streak))
        assertFalse(TaskStreaks.BADGES[2].earned(streak))
        val changes = Stars.update(streak.asStarStreak(), null, LocalDate.of(2026, 2, 1), StarTrack.TAKEN)
        assertEquals(StarTier.ZILVER, changes.last().tier)
        assertEquals(StarTrack.TAKEN, changes.last().track)
    }

    @Test
    fun noTasksMeansNoStreak() {
        val streak = TaskRules.streak(emptyList(), emptyList(), start.atTime(LocalTime.NOON))
        assertEquals(0, streak.weeks)
        assertNull(streak.runStart)
    }

    @Test
    fun celebratesEachNewWeekAndMonthOnce() {
        val streak = TaskStreak(weeks = 2, months = 0, bestWeeks = 2, bestMonths = 0, runStart = start)
        assertEquals("2 weken op rij compleet!", TaskStreaks.milestoneToCelebrate(streak, 1, 0)?.title)
        assertNull(TaskStreaks.milestoneToCelebrate(streak, 2, 0))
        val month = streak.copy(weeks = 4, months = 1)
        assertEquals("Een maand compleet!", TaskStreaks.milestoneToCelebrate(month, 4, 0)?.title)
    }

    @Test
    fun reminderAtTaskTimeThenEveryHourUntilMidnight() {
        val t = task(1, TaskRecurrence.DAGELIJKS).copy(minuteOfDay = 12 * 60)
        val day = start.plusDays(1)
        assertEquals(day.atTime(12, 0), TaskReminderScheduler.nextTrigger(t, false, day.atTime(9, 0)))
        assertEquals(day.atTime(13, 0), TaskReminderScheduler.nextTrigger(t, false, day.atTime(12, 0, 30)))
        assertEquals(day.atTime(23, 0), TaskReminderScheduler.nextTrigger(t, false, day.atTime(22, 10)))
        // Na de laatste herinnering van de dag, of als hij al gedaan is: morgen op het tijdstip.
        assertEquals(day.plusDays(1).atTime(12, 0), TaskReminderScheduler.nextTrigger(t, false, day.atTime(23, 30)))
        assertEquals(day.plusDays(1).atTime(12, 0), TaskReminderScheduler.nextTrigger(t, true, day.atTime(9, 0)))
    }

    @Test
    fun reminderSkipsDaysWithoutTheTaskAndStopsForPastOneOffTasks() {
        val mask = Weekdays.bit(DayOfWeek.MONDAY)
        val weekly = task(1, TaskRecurrence.WEEKDAGEN, weekdays = mask).copy(minuteOfDay = 8 * 60)
        assertEquals(monday.plusDays(7).atTime(8, 0), TaskReminderScheduler.nextTrigger(weekly, true, monday.atTime(9, 0)))
        val once = task(2, TaskRecurrence.EENMALIG, startDay = monday)
        assertNull(TaskReminderScheduler.nextTrigger(once, true, monday.atTime(13, 0)))
    }

    @Test
    fun newTaskDefaultsToNoon() {
        assertEquals(12 * 60, TaskItem(name = "x", startDay = 0).minuteOfDay)
    }

    @Test
    fun nextOccurrenceFindsTheNextDay() {
        val t = task(1, TaskRecurrence.INTERVAL, intervalDays = 5)
        assertEquals(start.plusDays(5), TaskRules.nextOccurrence(t, start.plusDays(1)))
        assertNull(TaskRules.nextOccurrence(task(2, TaskRecurrence.EENMALIG), start.plusDays(1)))
    }
}
