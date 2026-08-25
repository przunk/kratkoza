package com.przunk.kratkoza.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's local store (technical_requirements.md section 6).
 *
 * Version 1 already carries the columns Daily and public ranking will need — game type, ranked
 * eligibility, score, XP, ruleset version. They are cheap to carry and expensive to retrofit: a
 * migration cannot invent the game type of a row written before the column existed.
 */
@Database(
    entities = [PuzzleEntity::class, SavedGameEntity::class, GameHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KratkozaDatabase : RoomDatabase() {

    abstract fun puzzleDao(): PuzzleDao

    abstract fun savedGameDao(): SavedGameDao

    abstract fun gameHistoryDao(): GameHistoryDao

    companion object {
        private const val NAME = "kratkoza.db"

        fun create(context: Context): KratkozaDatabase =
            Room.databaseBuilder(context.applicationContext, KratkozaDatabase::class.java, NAME)
                // Foreign keys are declared on the entities; SQLite ignores them unless asked.
                .build()
    }
}
