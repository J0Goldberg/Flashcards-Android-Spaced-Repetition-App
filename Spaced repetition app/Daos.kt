package com.flashcards.data.local

import androidx.room.*
import com.flashcards.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Query("SELECT * FROM decks ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getById(id: Long): DeckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deck: DeckEntity): Long

    @Delete
    suspend fun delete(deck: DeckEntity)
}

@Dao
interface CardDao {

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAtMs ASC")
    fun observeByDeck(deckId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAtMs ASC")
    suspend fun getByDeck(deckId: Long): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cards: List<CardEntity>)

    @Delete
    suspend fun delete(card: CardEntity)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteByDeck(deckId: Long)
}

@Dao
interface CardStateDao {

    @Query("SELECT * FROM card_states WHERE cardId = :cardId")
    suspend fun getByCard(cardId: Long): CardStateEntity?

    /**
     * Returns all card states for a deck, joined with cards.
     * Used to build the due queue.
     */
    @Query("""
        SELECT cs.* FROM card_states cs
        INNER JOIN cards c ON cs.cardId = c.id
        WHERE c.deckId = :deckId
    """)
    suspend fun getByDeck(deckId: Long): List<CardStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CardStateEntity)

    /** Count of cards in a deck whose nextDueMs <= now. */
    @Query("""
        SELECT COUNT(*) FROM card_states cs
        INNER JOIN cards c ON cs.cardId = c.id
        WHERE c.deckId = :deckId AND cs.nextDueMs <= :nowMs
    """)
    suspend fun countDue(deckId: Long, nowMs: Long = System.currentTimeMillis()): Int

    /** Average confidence for a deck (0 if never reviewed). */
    @Query("""
        SELECT AVG(cs.confidence) FROM card_states cs
        INNER JOIN cards c ON cs.cardId = c.id
        WHERE c.deckId = :deckId AND cs.confidence > 0
    """)
    suspend fun avgConfidence(deckId: Long): Float?
}

@Dao
interface ReviewEventDao {

    @Insert
    suspend fun insert(event: ReviewEventEntity)

    @Query("SELECT COUNT(*) FROM review_events")
    suspend fun totalReviews(): Int

    @Query("SELECT COUNT(*) FROM review_events WHERE reviewedAtMs >= :fromMs")
    suspend fun reviewsSince(fromMs: Long): Int

    /** Per-day review counts for the last N days (for streak / heatmap). */
    @Query("""
        SELECT (reviewedAtMs / 86400000) AS dayBucket, COUNT(*) AS count
        FROM review_events
        WHERE reviewedAtMs >= :fromMs
        GROUP BY dayBucket
        ORDER BY dayBucket ASC
    """)
    suspend fun dailyCounts(fromMs: Long): List<DailyCount>

    data class DailyCount(val dayBucket: Long, val count: Int)
}
