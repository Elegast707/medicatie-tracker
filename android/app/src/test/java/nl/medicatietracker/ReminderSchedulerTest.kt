package nl.medicatietracker

import nl.medicatietracker.reminders.ReminderScheduler.nextTrigger
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderSchedulerTest {
    private val eight = 8 * 60

    private fun at(hour: Int, minute: Int, day: Int = 12) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test
    fun beforeDueTime_firesAtDueTime() {
        assertEquals(at(8, 0), nextTrigger(eight, 15, loggedToday = false, now = at(7, 0)))
    }

    @Test
    fun loggedToday_firesTomorrow() {
        assertEquals(at(8, 0, day = 13), nextTrigger(eight, 15, loggedToday = true, now = at(7, 0)))
    }

    @Test
    fun overdue_repeatsEveryInterval() {
        assertEquals(at(8, 15), nextTrigger(eight, 15, loggedToday = false, now = atSec(8, 0, 30)))
        assertEquals(at(8, 30), nextTrigger(eight, 15, loggedToday = false, now = atSec(8, 15, 30)))
        assertEquals(at(8, 45), nextTrigger(eight, 15, loggedToday = false, now = at(8, 31)))
    }

    @Test
    fun noRepeat_waitsForTomorrow() {
        assertEquals(at(8, 0, day = 13), nextTrigger(eight, 0, loggedToday = false, now = at(8, 1)))
    }

    @Test
    fun repeatsStopAtMidnight() {
        val lateItem = 23 * 60 + 50
        assertEquals(at(23, 50, day = 13), nextTrigger(lateItem, 15, loggedToday = false, now = at(23, 55)))
    }

    private fun atSec(hour: Int, minute: Int, second: Int): LocalDateTime =
        LocalDateTime.of(2026, 10, 12, hour, minute, second)
}
