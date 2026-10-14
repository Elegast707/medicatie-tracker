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

enum class PetMood(val label: String) { BLIJ("is blij"), TEVREDEN("is tevreden"), HONGER("heeft honger!") }

object PetRules {
    const val START_COINS = 20
    private const val HOUR = 3_600_000L

    /** Na zoveel uur zonder eten krijgt het blobje honger (en wordt hij rood). */
    const val HUNGRY_AFTER_HOURS = 24
    const val CONTENT_AFTER_HOURS = 12

    fun hoursSinceMeal(lastFedAt: Long, now: Long): Double = (now - lastFedAt).coerceAtLeast(0) / HOUR.toDouble()

    fun mood(lastFedAt: Long, now: Long): PetMood {
        val hours = hoursSinceMeal(lastFedAt, now)
        return when {
            hours >= HUNGRY_AFTER_HOURS -> PetMood.HONGER
            hours >= CONTENT_AFTER_HOURS -> PetMood.TEVREDEN
            else -> PetMood.BLIJ
        }
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
) {
    fun mood(now: Long = System.currentTimeMillis()): PetMood = PetRules.mood(lastFedAt, now)

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
        dao.savePet(ensurePet().copy(lastFedAt = System.currentTimeMillis()))
        FeedResult.GEGETEN
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
