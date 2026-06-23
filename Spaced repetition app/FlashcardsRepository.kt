package com.flashcards.data.repository

import com.flashcards.data.local.*
import com.flashcards.data.model.*
import com.flashcards.domain.algorithm.CbrAlgorithm
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlashcardsRepository @Inject constructor(
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val cardStateDao: CardStateDao,
    private val reviewEventDao: ReviewEventDao
) {

    // ── Decks ────────────────────────────────────────────────────────────────

    fun observeDecks(): Flow<List<DeckEntity>> = deckDao.observeAll()

    suspend fun saveDeck(deck: DeckEntity): Long = deckDao.upsert(deck)

    suspend fun deleteDeck(deck: DeckEntity) = deckDao.delete(deck)

    // ── Cards ────────────────────────────────────────────────────────────────

    fun observeCards(deckId: Long): Flow<List<CardEntity>> =
        cardDao.observeByDeck(deckId)

    suspend fun saveCard(card: CardEntity): Long = cardDao.upsert(card)

    suspend fun deleteCard(card: CardEntity) = cardDao.delete(card)

    // ── Study queue ──────────────────────────────────────────────────────────

    /**
     * Builds a study queue for a deck:
     *  1. All cards that are due now (nextDueMs <= now)
     *  2. New cards (never reviewed) up to a cap of 10
     * Returns them shuffled.
     */
    suspend fun buildQueue(deckId: Long): List<CardEntity> {
        val cards  = cardDao.getByDeck(deckId)
        val states = cardStateDao.getByDeck(deckId).associateBy { it.cardId }

        val due = cards.filter { c ->
            val s = states[c.id]
            s == null || CbrAlgorithm.isDue(s.nextDueMs)
        }

        // Ensure new cards that have no state yet are included (capped at 10)
        val newCards = cards.filter { states[c.id] == null }.take(10)
        val queue = (due + newCards).distinctBy { it.id }

        return queue.shuffled()
    }

    // ── Reviewing ────────────────────────────────────────────────────────────

    /**
     * Records a review for a card:
     *  - Runs CBR to produce a new schedule
     *  - Upserts the CardStateEntity
     *  - Appends a ReviewEventEntity
     */
    suspend fun recordReview(card: CardEntity, rating: Int) {
        val existing = cardStateDao.getByCard(card.id)
            ?: CardStateEntity(cardId = card.id)

        val result = CbrAlgorithm.schedule(
            rating           = rating,
            currentInterval  = existing.intervalDays,
            repetitions      = existing.repetitions,
            nextDueMs        = existing.nextDueMs
        )

        cardStateDao.upsert(
            existing.copy(
                confidence      = rating,
                intervalDays    = result.newInterval,
                repetitions     = result.newRepetitions,
                nextDueMs       = result.nextDueMs,
                lastReviewedMs  = System.currentTimeMillis()
            )
        )

        reviewEventDao.insert(
            ReviewEventEntity(cardId = card.id, rating = rating)
        )
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    suspend fun getDeckStats(deckId: Long): DeckStats {
        val cards      = cardDao.getByDeck(deckId)
        val states     = cardStateDao.getByDeck(deckId).associateBy { it.cardId }
        val dueCount   = states.values.count { CbrAlgorithm.isDue(it.nextDueMs) } +
                         cards.count { states[it.id] == null }  // new cards also count as due
        val mastered   = states.values.count { it.confidence >= 4 && it.repetitions >= 3 }
        val avgConf    = cardStateDao.avgConfidence(deckId) ?: 0f
        return DeckStats(
            total    = cards.size,
            due      = dueCount,
            mastered = mastered,
            avgConfidence = avgConf
        )
    }

    suspend fun getGlobalStats(): GlobalStats {
        val total   = reviewEventDao.totalReviews()
        val sevenDayMs = System.currentTimeMillis() - 7 * 86_400_000L
        val lastWeek   = reviewEventDao.reviewsSince(sevenDayMs)
        val thirtyDayMs = System.currentTimeMillis() - 30 * 86_400_000L
        val dailyCounts = reviewEventDao.dailyCounts(thirtyDayMs)
        return GlobalStats(totalReviews = total, reviewsLastWeek = lastWeek, dailyCounts = dailyCounts)
    }

    data class DeckStats(
        val total: Int,
        val due: Int,
        val mastered: Int,
        val avgConfidence: Float
    )

    data class GlobalStats(
        val totalReviews: Int,
        val reviewsLastWeek: Int,
        val dailyCounts: List<ReviewEventDao.DailyCount>
    )
}
