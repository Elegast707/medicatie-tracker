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
        TaskItem::class, TaskLog::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TrackerDao
    abstract fun petDao(): PetDao
    abstract fun taskDao(): TaskDao

    companion object {
        fun build(context: Context, name: String = "medicatie.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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

        /** Versie 5: een aparte blijheidsbalk voor het blobje, en aaien. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pet` ADD COLUMN `happiness` INTEGER NOT NULL DEFAULT 80")
                db.execSQL("ALTER TABLE `pet` ADD COLUMN `happinessAt` INTEGER NOT NULL DEFAULT 0")
                // De blijheid begint te tellen vanaf de update, niet vanaf 1970.
                db.execSQL("UPDATE `pet` SET `happinessAt` = ?", arrayOf<Any>(System.currentTimeMillis()))
            }
        }

        /** Versie 6: het aparte tabblad Taken, met een eigen reeks en eigen sterren. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `details` TEXT NOT NULL, `recurrence` TEXT NOT NULL, " +
                        "`startDay` INTEGER NOT NULL, `weekdays` INTEGER NOT NULL, `intervalDays` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `deletedAt` INTEGER)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_logs` (`taskId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`doneAt` INTEGER NOT NULL, `taskName` TEXT NOT NULL, PRIMARY KEY(`taskId`, `epochDay`))",
                )
                // Bestaande sterren horen bij de medicatiereeks.
                db.execSQL("ALTER TABLE `stars` ADD COLUMN `track` TEXT NOT NULL DEFAULT 'MEDICATIE'")
            }
        }
    }
}
