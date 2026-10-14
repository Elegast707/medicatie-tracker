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
    fun fullnessDecaysAndClamps() {
        assertEquals(80, PetRules.fullnessAt(80, 0, 0))
        assertEquals(72, PetRules.fullnessAt(80, 0, 2 * hour))
        assertEquals(0, PetRules.fullnessAt(80, 0, 48 * hour))
        // Een klok die terugspringt maakt hem niet voller.
        assertEquals(80, PetRules.fullnessAt(80, 10 * hour, 0))
    }

    @Test
    fun moodFollowsFullness() {
        assertEquals(PetMood.BLIJ, PetRules.mood(60))
        assertEquals(PetMood.TEVREDEN, PetRules.mood(59))
        assertEquals(PetMood.TEVREDEN, PetRules.mood(30))
        assertEquals(PetMood.VERDRIETIG, PetRules.mood(29))
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
        val pet = PetState(fullnessAt = 0)
            .withEquipped(AccessorySlot.HOOFD, Accessory.PET)
            .withEquipped(AccessorySlot.HOOFD, Accessory.HOGE_HOED)
            .withEquipped(AccessorySlot.HAND, Accessory.GOUDEN_POLLEPEL)
        assertEquals(Accessory.HOGE_HOED, pet.equipped(AccessorySlot.HOOFD))
        assertEquals(Accessory.GOUDEN_POLLEPEL, pet.equipped(AccessorySlot.HAND))
        assertNull(pet.equipped(AccessorySlot.OGEN))
        assertNull(pet.withEquipped(AccessorySlot.HAND, null).equipped(AccessorySlot.HAND))
    }
}
