package com.flashcards.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flashcards.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        CardStateEntity::class,
        ReviewEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FlashcardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun cardStateDao(): CardStateDao
    abstract fun reviewEventDao(): ReviewEventDao

    companion object {
        @Volatile private var INSTANCE: FlashcardsDatabase? = null

        fun getInstance(context: Context): FlashcardsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlashcardsDatabase::class.java,
                    "flashcards.db"
                )
                    .addCallback(SeedCallback())
                    .build()
                    .also { INSTANCE = it }
            }
    }

    /** Seeds three starter decks on first run. */
    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Seeding runs after the DB is open; use the singleton safely.
            CoroutineScope(Dispatchers.IO).launch {
                val instance = INSTANCE ?: return@launch
                seed(instance)
            }
        }

        private suspend fun seed(db: FlashcardsDatabase) {
            val deckDao  = db.deckDao()
            val cardDao  = db.cardDao()

            data class Seed(val name: String, val color: String, val pairs: List<Pair<String, String>>)

            val seeds = listOf(
                Seed("Spanish Basics", "#534AB7", listOf(
                    "Hello" to "Hola",
                    "Thank you" to "Gracias",
                    "Good morning" to "Buenos días",
                    "How are you?" to "¿Cómo estás?",
                    "Please" to "Por favor"
                )),
                Seed("World Capitals", "#0F6E56", listOf(
                    "Japan" to "Tokyo",
                    "Brazil" to "Brasília",
                    "Egypt" to "Cairo",
                    "Australia" to "Canberra"
                )),
                Seed("JavaScript Concepts", "#993C1D", listOf(
                    "What is a closure?" to "A function that retains access to its outer scope even after the outer function has returned.",
                    "What is hoisting?" to "JS moves variable and function declarations to the top of their scope before execution.",
                    "What is the event loop?" to "A mechanism that continuously checks the call stack and processes tasks from the message queue."
                ))
            )

            seeds.forEach { seed ->
                val deckId = deckDao.upsert(DeckEntity(name = seed.name, colorHex = seed.color))
                cardDao.upsertAll(seed.pairs.map { (front, back) ->
                    CardEntity(deckId = deckId, front = front, back = back)
                })
            }
        }
    }
}
