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
    fun hungryAfterADayWithoutFood() {
        assertEquals(PetMood.BLIJ, PetRules.mood(0, 0))
        assertEquals(PetMood.BLIJ, PetRules.mood(0, 11 * hour))
        assertEquals(PetMood.TEVREDEN, PetRules.mood(0, 12 * hour))
        assertEquals(PetMood.TEVREDEN, PetRules.mood(0, 24 * hour - 1))
        assertEquals(PetMood.HONGER, PetRules.mood(0, 24 * hour))
        assertEquals(PetMood.HONGER, PetRules.mood(0, 72 * hour))
        // Een klok die terugspringt geeft geen honger.
        assertEquals(PetMood.BLIJ, PetRules.mood(10 * hour, 0))
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
