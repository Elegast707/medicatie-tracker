package nl.medicatietracker

import nl.medicatietracker.data.Accessory
import nl.medicatietracker.data.AccessorySlot
import nl.medicatietracker.data.Food
import nl.medicatietracker.data.PetMood
import nl.medicatietracker.data.PetRules
import nl.medicatietracker.data.PetState
import nl.medicatietracker.data.StarTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetRulesTest {
    private val hour = 3_600_000L

    @Test
    fun hungerGoesBeforeHappiness() {
        assertEquals(PetMood.BLIJ, PetRules.mood(0, 80, 0))
        assertEquals(PetMood.TEVREDEN, PetRules.mood(0, 59, 11 * hour))
        assertEquals(PetMood.VERDRIETIG, PetRules.mood(0, 29, 23 * hour))
        assertEquals(PetMood.HONGER, PetRules.mood(0, 100, 24 * hour))
        assertEquals(PetMood.HONGER, PetRules.mood(0, 0, 72 * hour))
        // Een klok die terugspringt geeft geen honger.
        assertEquals(PetMood.BLIJ, PetRules.mood(10 * hour, 100, 0))
    }

    @Test
    fun happinessEmptiesInADayAndFasterWhenHungry() {
        // Van vol naar leeg in 24 uur.
        assertEquals(50, PetRules.happiness(100, 0, 0, 12 * hour))
        assertEquals(0, PetRules.happiness(100, 0, 0, 24 * hour))
        // Met honger (na 24 uur zonder eten) gaat het twee keer zo snel: 4 uur gewoon, 2 uur met honger.
        assertEquals(67, PetRules.happiness(100, 20 * hour, 0, 26 * hour))
        // Alleen de tijd na het opslaan telt, en terug in de tijd verandert niets.
        assertEquals(80, PetRules.happiness(80, 20 * hour, 0, 5 * hour))
    }

    @Test
    fun pettingAlwaysWorksButHungerCapsIt() {
        assertEquals(PetRules.PET_BONUS, PetRules.petGain(50, false))
        assertEquals(5, PetRules.petGain(95, false))
        assertEquals(0, PetRules.petGain(100, false))
        // Met honger wordt hij door aaien niet blijer dan de helft.
        assertEquals(PetRules.HUNGRY_HAPPINESS_CAP - 45, PetRules.petGain(45, true))
        assertEquals(0, PetRules.petGain(70, true))
    }

    @Test
    fun eatingLocksInHappinessBeforeTheNewMeal() {
        val pet = PetState(lastFedAt = 0, happiness = 100, happinessAt = 0)
        val fed = pet.withHappiness(30 * hour, PetRules.FEED_BONUS).copy(lastFedAt = 30 * hour)
        assertEquals(PetRules.FEED_BONUS, fed.happinessNow(30 * hour))
        assertEquals(PetMood.VERDRIETIG, fed.mood(30 * hour))
    }

    @Test
    fun bellyEmptiesOverADay() {
        assertEquals(100, PetRules.bellyPercent(0, 0))
        assertEquals(50, PetRules.bellyPercent(0, 12 * hour))
        assertEquals(0, PetRules.bellyPercent(0, 24 * hour))
        assertEquals(0, PetRules.bellyPercent(0, 48 * hour))
    }

    @Test
    fun higherStarsEarnMoreCoins() {
        val coins = StarTier.entries.map { PetRules.coinsForStar(it) }
        assertTrue(coins.all { it > 0 })
        assertEquals(coins.sorted(), coins)
        assertEquals(listOf(10, 25, 50, 200), coins)
    }

    @Test
    fun basicFoodIsFreeAndShopFoodCostsCoins() {
        assertEquals(setOf(Food.BROOD, Food.DRANKJE, Food.WORTEL), Food.BASIC.toSet())
        assertEquals(setOf(Food.PIZZA, Food.PATAT, Food.MACARONI, Food.PANNENKOEK), Food.SHOP.toSet())
    }

    @Test
    fun equipOnePerSlot() {
        val pet = PetState(lastFedAt = 0)
            .withEquipped(AccessorySlot.HOOFD, Accessory.PET)
            .withEquipped(AccessorySlot.HOOFD, Accessory.HOGE_HOED)
            .withEquipped(AccessorySlot.HAND, Accessory.GOUDEN_POLLEPEL)
        assertEquals(Accessory.HOGE_HOED, pet.equipped(AccessorySlot.HOOFD))
        assertEquals(Accessory.GOUDEN_POLLEPEL, pet.equipped(AccessorySlot.HAND))
        assertNull(pet.equipped(AccessorySlot.OGEN))
        assertNull(pet.withEquipped(AccessorySlot.HAND, null).equipped(AccessorySlot.HAND))
    }
}
