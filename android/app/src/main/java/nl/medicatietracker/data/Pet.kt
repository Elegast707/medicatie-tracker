package nl.medicatietracker.data

import androidx.annotation.DrawableRes
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.medicatietracker.R
import kotlin.math.roundToInt

enum class AccessorySlot(val label: String) { HOOFD("Hoofd"), OGEN("Ogen"), NEK("Nek"), HAND("Hand") }

/** Eten voor het blobje. Basiseten verdien je door af te vinken; de rest koop je met muntjes. */
enum class Food(val label: String, val price: Int?, @DrawableRes val drawable: Int) {
    BROOD("Brood", null, R.drawable.pet_food_brood),
    DRANKJE("Drankje", null, R.drawable.pet_food_drankje),
    WORTEL("Wortel", null, R.drawable.pet_food_wortel),
    PIZZA("Pizza", 6, R.drawable.pet_food_pizza),
    PATAT("Patat", 5, R.drawable.pet_food_patat),
    MACARONI("Macaroni", 6, R.drawable.pet_food_macaroni),
    PANNENKOEK("Pannenkoek", 4, R.drawable.pet_food_pannenkoek),
    ;

    companion object {
        val BASIC = entries.filter { it.price == null }
        val SHOP = entries.filter { it.price != null }
    }
}

enum class Accessory(val label: String, val slot: AccessorySlot, val price: Int, @DrawableRes val drawable: Int) {
    PET("Pet", AccessorySlot.HOOFD, 5, R.drawable.pet_acc_pet),
    MUTS("Muts", AccessorySlot.HOOFD, 5, R.drawable.pet_acc_muts),
    FEESTMUTS("Feestmuts", AccessorySlot.HOOFD, 10, R.drawable.pet_acc_feestmuts),
    HOGE_HOED("Hoge hoed", AccessorySlot.HOOFD, 20, R.drawable.pet_acc_hoge_hoed),
    RONDE_BRIL("Ronde bril", AccessorySlot.OGEN, 5, R.drawable.pet_acc_ronde_bril),
    NERDBRIL("Nerdbril", AccessorySlot.OGEN, 10, R.drawable.pet_acc_nerdbril),
    ZONNEBRIL("Zonnebril", AccessorySlot.OGEN, 15, R.drawable.pet_acc_zonnebril),
    HARTJESBRIL("Hartjesbril", AccessorySlot.OGEN, 25, R.drawable.pet_acc_hartjesbril),
    RODE_SJAAL("Rode sjaal", AccessorySlot.NEK, 5, R.drawable.pet_acc_rode_sjaal),
    STREEPJESSJAAL("Streepjessjaal", AccessorySlot.NEK, 10, R.drawable.pet_acc_streepjessjaal),
    GOUDEN_POLLEPEL("Gouden pollepel van PKU research", AccessorySlot.HAND, 100, R.drawable.pet_acc_gouden_pollepel),
    ;

    companion object {
        fun byId(id: String?): Accessory? = entries.firstOrNull { it.name == id }
    }
}

enum class PetMood(val label: String) {
    BLIJ("is blij"),
    TEVREDEN("is tevreden"),
    VERDRIETIG("is verdrietig"),
    HONGER("heeft honger!"),
}

/** Wat er gebeurt als je het blobje aait. */
enum class PetResult { GEAAID, AL_GEAAID, WIL_ETEN }

object PetRules {
    const val START_COINS = 20
    private const val HOUR = 3_600_000L

    /** Na zoveel uur zonder eten krijgt het blobje honger (en wordt hij rood). */
    const val HUNGRY_AFTER_HOURS = 24
    const val CONTENT_AFTER_HOURS = 12

    fun hoursSinceMeal(lastFedAt: Long, now: Long): Double = (now - lastFedAt).coerceAtLeast(0) / HOUR.toDouble()

    fun isHungry(lastFedAt: Long, now: Long): Boolean = hoursSinceMeal(lastFedAt, now) >= HUNGRY_AFTER_HOURS

    /** Honger gaat voor; verder bepaalt de blijheidsbalk hoe het blobje zich voelt. */
    fun mood(lastFedAt: Long, happiness: Int, now: Long): PetMood = when {
        isHungry(lastFedAt, now) -> PetMood.HONGER
        happiness >= HAPPY_FROM -> PetMood.BLIJ
        happiness >= CONTENT_FROM -> PetMood.TEVREDEN
        else -> PetMood.VERDRIETIG
    }

    /** Moet gelijk blijven aan de standaardwaarde van [PetState.happiness] in de database. */
    const val START_HAPPINESS = 80
    const val HAPPY_FROM = 60
    const val CONTENT_FROM = 30

    /** Blijheid die er per uur af gaat: langzaam met een volle buik, snel met honger. */
    const val DECAY_FED = 1.0
    const val DECAY_PECKISH = 3.0
    const val DECAY_HUNGRY = 8.0

    /** Aaien geeft blijheid, maar maar één keer per [PET_COOLDOWN_MS]. */
    const val PET_BONUS = 10
    const val PET_COOLDOWN_MS = 10 * 60_000L

    /** Met honger kun je hem door aaien niet blijer maken dan dit; dan wil hij eerst eten. */
    const val HUNGRY_HAPPINESS_CAP = 50

    /** Eten maakt ook een beetje blij. */
    const val FEED_BONUS = 15

    /**
     * Hoeveel blijheid er tussen [from] en [to] af gaat. Hoe langer geleden de laatste maaltijd,
     * hoe sneller het gaat: tot 12 uur [DECAY_FED], tot 24 uur [DECAY_PECKISH], daarna [DECAY_HUNGRY].
     */
    fun happinessLoss(lastFedAt: Long, from: Long, to: Long): Double {
        if (to <= from) return 0.0
        val peckish = lastFedAt + CONTENT_AFTER_HOURS * HOUR
        val hungry = lastFedAt + HUNGRY_AFTER_HOURS * HOUR
        fun overlap(start: Long, end: Long): Long = (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0)
        return (
            overlap(Long.MIN_VALUE, peckish) * DECAY_FED +
                overlap(peckish, hungry) * DECAY_PECKISH +
                overlap(hungry, Long.MAX_VALUE) * DECAY_HUNGRY
            ) / HOUR
    }

    /** De blijheid op [now], uitgaande van [stored] op moment [storedAt]. Van 0 tot 100. */
    fun happiness(stored: Int, storedAt: Long, lastFedAt: Long, now: Long): Int =
        (stored - happinessLoss(lastFedAt, storedAt, now)).roundToInt().coerceIn(0, 100)

    /** Hoeveel blijheid aaien nu oplevert; 0 als hij net al geaaid is of honger heeft en al op het maximum zit. */
    fun petGain(happiness: Int, hungry: Boolean, lastPettedAt: Long, now: Long): Int {
        if (now - lastPettedAt in 0 until PET_COOLDOWN_MS) return 0
        val cap = if (hungry) HUNGRY_HAPPINESS_CAP else 100
        return (minOf(happiness + PET_BONUS, cap) - happiness).coerceAtLeast(0)
    }

    /** Hoe vol de buik nog is, van 100 (net gegeten) tot 0 (een dag niets gehad). */
    fun bellyPercent(lastFedAt: Long, now: Long): Int =
        (100 - hoursSinceMeal(lastFedAt, now) / HUNGRY_AFTER_HOURS * 100).toInt().coerceIn(0, 100)

    /** Muntjes voor een verdiende ster; hoe hoger de ster, hoe meer muntjes. */
    fun coinsForStar(tier: StarTier): Int = when (tier) {
        StarTier.BRONS -> 10
        StarTier.ZILVER -> 25
        StarTier.GOUD -> 50
        StarTier.BLAUW -> 200
    }
}

@Entity(tableName = "pet")
data class PetState(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    /** Niet meer gebruikt; blijft bestaan zodat de database niet hoeft te veranderen. */
    val fullness: Int = 100,
    /** Moment van de laatste maaltijd (bij een nieuw blobje: het moment van aanmaken). */
    @ColumnInfo(name = "fullnessAt") val lastFedAt: Long = System.currentTimeMillis(),
    val coins: Int = PetRules.START_COINS,
    val head: String? = null,
    val eyes: String? = null,
    val neck: String? = null,
    val hand: String? = null,
    /** Blijheid (0-100) zoals die was op [happinessAt]; daarna zakt hij met de tijd, zie [PetRules.happiness]. */
    @ColumnInfo(defaultValue = "80") val happiness: Int = PetRules.START_HAPPINESS,
    @ColumnInfo(defaultValue = "0") val happinessAt: Long = lastFedAt,
    /** Laatste keer dat het blobje blijheid kreeg van aaien. */
    @ColumnInfo(defaultValue = "0") val pettedAt: Long = 0,
) {
    fun happinessNow(now: Long = System.currentTimeMillis()): Int =
        PetRules.happiness(happiness, happinessAt, lastFedAt, now)

    fun mood(now: Long = System.currentTimeMillis()): PetMood = PetRules.mood(lastFedAt, happinessNow(now), now)

    /** Zet de blijheid van nu vast (plus [bonus]), zodat een nieuwe maaltijd niet met terugwerkende kracht telt. */
    fun withHappiness(now: Long, bonus: Int = 0): PetState =
        copy(happiness = (happinessNow(now) + bonus).coerceIn(0, 100), happinessAt = now)

    fun equipped(slot: AccessorySlot): Accessory? = Accessory.byId(
        when (slot) {
            AccessorySlot.HOOFD -> head
            AccessorySlot.OGEN -> eyes
            AccessorySlot.NEK -> neck
            AccessorySlot.HAND -> hand
        },
    )

    fun withEquipped(slot: AccessorySlot, accessory: Accessory?): PetState = when (slot) {
        AccessorySlot.HOOFD -> copy(head = accessory?.name)
        AccessorySlot.OGEN -> copy(eyes = accessory?.name)
        AccessorySlot.NEK -> copy(neck = accessory?.name)
        AccessorySlot.HAND -> copy(hand = accessory?.name)
    }

    val displayName: String get() = name.ifBlank { "Blobje" }
}

@Entity(tableName = "pet_food")
data class FoodStock(@PrimaryKey val foodId: String, val count: Int)

@Entity(tableName = "pet_owned")
data class OwnedAccessory(@PrimaryKey val accessoryId: String, val boughtAt: Long)

/** Houdt bij voor welk afgevinkt moment al eten is gegeven, zodat je niet kunt "farmen". */
@Entity(tableName = "food_rewards", primaryKeys = ["itemId", "epochDay"])
data class FoodReward(val itemId: Long, val epochDay: Long, val foodId: String)

@Dao
interface PetDao {
    @Query("SELECT * FROM pet WHERE id = 1")
    fun observePet(): Flow<PetState?>

    @Query("SELECT * FROM pet WHERE id = 1")
    suspend fun pet(): PetState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePet(pet: PetState)

    @Query("SELECT * FROM pet_food WHERE count > 0")
    fun observeFood(): Flow<List<FoodStock>>

    @Query("SELECT * FROM pet_food WHERE foodId = :foodId")
    suspend fun food(foodId: String): FoodStock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFood(stock: FoodStock)

    @Query("SELECT * FROM pet_owned")
    fun observeOwned(): Flow<List<OwnedAccessory>>

    @Query("SELECT COUNT(*) FROM pet_owned WHERE accessoryId = :accessoryId")
    suspend fun ownedCount(accessoryId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addOwned(owned: OwnedAccessory)

    /** Geeft -1 terug als er voor dit moment al eten is gegeven. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFoodReward(reward: FoodReward): Long
}

enum class FeedResult { GEGETEN, GEEN_ETEN }

class PetRepository(private val db: AppDatabase) {
    private val dao = db.petDao()

    val pet: Flow<PetState?> = dao.observePet()
    val food: Flow<Map<Food, Int>> = dao.observeFood().map { stocks ->
        stocks.mapNotNull { stock -> Food.entries.firstOrNull { it.name == stock.foodId }?.let { it to stock.count } }.toMap()
    }
    val owned: Flow<Set<Accessory>> = dao.observeOwned().map { list -> list.mapNotNull { Accessory.byId(it.accessoryId) }.toSet() }

    suspend fun ensurePet(): PetState = dao.pet() ?: PetState().also { dao.savePet(it) }

    suspend fun rename(name: String) = db.withTransaction {
        dao.savePet(ensurePet().copy(name = name.trim().take(MAX_NAME_LENGTH)))
    }

    /** Geeft willekeurig basiseten voor een afgevinkt moment, maar maar één keer per item per dag. */
    suspend fun grantFoodFor(itemId: Long, epochDay: Long): Food? = db.withTransaction {
        val food = Food.BASIC.random()
        if (dao.addFoodReward(FoodReward(itemId, epochDay, food.name)) == -1L) return@withTransaction null
        addFood(food, 1)
        food
    }

    suspend fun addCoins(amount: Int) = db.withTransaction {
        val pet = ensurePet()
        dao.savePet(pet.copy(coins = pet.coins + amount))
    }

    suspend fun feed(food: Food): FeedResult = db.withTransaction {
        val stock = dao.food(food.name)?.count ?: 0
        if (stock <= 0) return@withTransaction FeedResult.GEEN_ETEN
        dao.saveFood(FoodStock(food.name, stock - 1))
        val now = System.currentTimeMillis()
        dao.savePet(ensurePet().withHappiness(now, PetRules.FEED_BONUS).copy(lastFedAt = now))
        FeedResult.GEGETEN
    }

    /** Aait het blobje. Dat maakt hem blijer, maar niet vaker dan eens per tien minuten. */
    suspend fun stroke(): PetResult = db.withTransaction {
        val now = System.currentTimeMillis()
        val pet = ensurePet()
        val happiness = pet.happinessNow(now)
        val hungry = PetRules.isHungry(pet.lastFedAt, now)
        if (now - pet.pettedAt in 0 until PetRules.PET_COOLDOWN_MS) return@withTransaction PetResult.AL_GEAAID
        val gain = PetRules.petGain(happiness, hungry, pet.pettedAt, now)
        if (hungry && gain == 0) return@withTransaction PetResult.WIL_ETEN
        dao.savePet(pet.withHappiness(now, gain).copy(pettedAt = now))
        PetResult.GEAAID
    }

    suspend fun buyFood(food: Food): Boolean = db.withTransaction {
        val price = food.price ?: return@withTransaction false
        val pet = ensurePet()
        if (pet.coins < price) return@withTransaction false
        dao.savePet(pet.copy(coins = pet.coins - price))
        addFood(food, 1)
        true
    }

    /** Koopt een accessoire en zet het meteen op. */
    suspend fun buyAccessory(accessory: Accessory): Boolean = db.withTransaction {
        val pet = ensurePet()
        if (dao.ownedCount(accessory.name) > 0 || pet.coins < accessory.price) return@withTransaction false
        dao.addOwned(OwnedAccessory(accessory.name, System.currentTimeMillis()))
        dao.savePet(pet.copy(coins = pet.coins - accessory.price).withEquipped(accessory.slot, accessory))
        true
    }

    /** Zet een accessoire op, of af als het al op stond. */
    suspend fun toggleEquipped(accessory: Accessory) = db.withTransaction {
        if (dao.ownedCount(accessory.name) == 0) return@withTransaction
        val pet = ensurePet()
        val next = if (pet.equipped(accessory.slot) == accessory) null else accessory
        dao.savePet(pet.withEquipped(accessory.slot, next))
    }

    private suspend fun addFood(food: Food, amount: Int) {
        val current = dao.food(food.name)?.count ?: 0
        dao.saveFood(FoodStock(food.name, current + amount))
    }

    companion object {
        const val MAX_NAME_LENGTH = 16
    }
}
