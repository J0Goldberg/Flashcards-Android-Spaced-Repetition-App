package com.flashcards.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,          // e.g. "#534AB7"
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cards",
    foreignKeys = [ForeignKey(
        entity = DeckEntity::class,
        parentColumns = ["id"],
        childColumns = ["deckId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deckId")]
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * Stores the CBR scheduling state for each card.
 * One row per card; upserted after every review.
 */
@Entity(
    tableName = "card_states",
    foreignKeys = [ForeignKey(
        entity = CardEntity::class,
        parentColumns = ["id"],
        childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("cardId", unique = true)]
)
data class CardStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val confidence: Int = 0,        // last rating 1–5, 0 = never reviewed
    val intervalDays: Int = 1,
    val repetitions: Int = 0,
    val nextDueMs: Long = System.currentTimeMillis(),
    val lastReviewedMs: Long? = null
)

/** Append-only review log for stats / history. */
@Entity(
    tableName = "review_events",
    foreignKeys = [ForeignKey(
        entity = CardEntity::class,
        parentColumns = ["id"],
        childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("cardId")]
)
data class ReviewEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val rating: Int,
    val reviewedAtMs: Long = System.currentTimeMillis()
)
