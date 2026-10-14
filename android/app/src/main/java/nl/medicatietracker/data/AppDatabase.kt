package nl.medicatietracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackedItem::class, LogEntry::class, StarEntry::class,
        PetState::class, FoodStock::class, OwnedAccessory::class, FoodReward::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TrackerDao
    abstract fun petDao(): PetDao

    companion object {
        fun build(context: Context, name: String = "medicatie.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(SeedCallback)
                .build()

        /** Versie 2: instelling per item of overslaan de reeks breekt. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN skipAllowed INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versie 3: verdiende sterren en de sterrencollectie. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS stars (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tier TEXT NOT NULL, " +
                        "earnedOn INTEGER NOT NULL, collectedOn INTEGER, reason TEXT)",
                )
            }
        }

        /** Versie 4: het blobje (huisdier), zijn eten, accessoires en beloningen. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pet` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`fullness` INTEGER NOT NULL, `fullnessAt` INTEGER NOT NULL, `coins` INTEGER NOT NULL, " +
                        "`head` TEXT, `eyes` TEXT, `neck` TEXT, `hand` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pet_food` (`foodId` TEXT NOT NULL, `count` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`foodId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pet_owned` (`accessoryId` TEXT NOT NULL, `boughtAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`accessoryId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `food_rewards` (`itemId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`foodId` TEXT NOT NULL, PRIMARY KEY(`itemId`, `epochDay`))",
                )
            }
        }
    }

    /** Vult bij de eerste start het schema uit de oude web-app in. */
    private object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val seed = listOf(
                Triple("Lophlex", 8 * 60, 30),
                Triple("Lophlex", 12 * 60, 30),
                Triple("Lophlex", 18 * 60, 30),
                Triple("Calciumtabletten", 20 * 60, 15),
            )
            for ((name, minute, repeat) in seed) {
                db.execSQL(
                    "INSERT INTO items (name, type, details, minuteOfDay, repeatMinutes, active, createdAt) " +
                        "VALUES (?, ?, '', ?, ?, 1, ?)",
                    arrayOf<Any>(name, ItemType.MEDICATIE.name, minute, repeat, now),
                )
            }
        }
    }
}
