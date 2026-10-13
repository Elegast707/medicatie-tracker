package nl.medicatietracker.data

import androidx.annotation.DrawableRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import nl.medicatietracker.R
import java.time.LocalDate

/** De ster rechtsboven, op volgorde van laag naar hoog. */
enum class StarTier(val days: Int, val label: String, @DrawableRes val drawable: Int) {
    BRONS(7, "Bronzen ster", R.drawable.ic_star_bronze),
    ZILVER(30, "Zilveren ster", R.drawable.ic_star_silver),
    GOUD(90, "Gouden ster", R.drawable.ic_star_gold),
    BLAUW(365, "Blauwe ster", R.drawable.ic_star_blue),
    ;

    companion object {
        fun forDays(days: Int): StarTier? = entries.lastOrNull { days >= it.days }
    }
}

enum class StarReason { VERVANGEN, GEBROKEN }

/**
 * Een verdiende ster. Zonder [collectedOn] is het de ster die nu rechtsboven staat; daarna hoort
 * hij bij de verzameling.
 */
@Entity(tableName = "stars")
data class StarEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tier: StarTier,
    /** Dag (epochDay) waarop de ster verdiend is. */
    val earnedOn: Long,
    /** Dag (epochDay) waarop de ster vervangen is of de reeks brak. */
    val collectedOn: Long? = null,
    val reason: StarReason? = null,
)

object Stars {
    /**
     * Rijen die opgeslagen moeten worden nu de reeks [streak] is, gegeven de ster die er nu staat.
     * Een ster gaat naar de verzameling als hij vervangen wordt door een hogere of als de reeks breekt.
     * Wordt een niveau overgeslagen (bijvoorbeeld door een eerdere dag aan te passen), dan komt dat
     * niveau ook in de verzameling.
     */
    fun update(streak: StreakInfo, held: StarEntry?, today: LocalDate): List<StarEntry> {
        val day = today.toEpochDay()
        val changes = mutableListOf<StarEntry>()
        var current = held
        if (current != null) {
            // Dezelfde reeks liep al toen de ster verdiend werd, ook als hij achteraf is verlengd.
            val start = streak.currentStart
            if (start == null || start.toEpochDay() > current.earnedOn) {
                changes += current.copy(collectedOn = day, reason = StarReason.GEBROKEN)
                current = null
            }
        }
        val tier = StarTier.forDays(streak.current) ?: return changes
        val from = current?.tier
        if (from != null && tier <= from) return changes

        current?.let { changes += it.copy(collectedOn = day, reason = StarReason.VERVANGEN) }
        StarTier.entries.filter { (from == null || it > from) && it < tier }.forEach {
            changes += StarEntry(tier = it, earnedOn = day, collectedOn = day, reason = StarReason.VERVANGEN)
        }
        changes += StarEntry(tier = tier, earnedOn = day)
        return changes
    }
}
