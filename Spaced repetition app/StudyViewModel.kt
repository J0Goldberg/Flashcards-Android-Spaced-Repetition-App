package com.flashcards.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.data.model.CardEntity
import com.flashcards.data.repository.FlashcardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val repository: FlashcardsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle["deckId"])

    // ── UI state ─────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<StudyUiState>(StudyUiState.Loading)
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        loadQueue()
    }

    private fun loadQueue() {
        viewModelScope.launch {
            val queue = repository.buildQueue(deckId)
            _uiState.value = if (queue.isEmpty()) {
                StudyUiState.NothingDue
            } else {
                StudyUiState.Reviewing(
                    queue      = queue,
                    index      = 0,
                    flipped    = false,
                    reviewed   = 0
                )
            }
        }
    }

    fun flip() {
        val current = _uiState.value as? StudyUiState.Reviewing ?: return
        _uiState.value = current.copy(flipped = true)
    }

    fun grade(rating: Int) {
        val current = _uiState.value as? StudyUiState.Reviewing ?: return
        val card = current.queue[current.index]

        viewModelScope.launch {
            repository.recordReview(card, rating)

            val nextIndex = current.index + 1
            _uiState.value = if (nextIndex >= current.queue.size) {
                StudyUiState.SessionComplete(reviewed = current.reviewed + 1)
            } else {
                current.copy(
                    index    = nextIndex,
                    flipped  = false,
                    reviewed = current.reviewed + 1
                )
            }
        }
    }
}

// ── UI state sealed class ────────────────────────────────────────────────────

sealed class StudyUiState {
    object Loading : StudyUiState()
    object NothingDue : StudyUiState()

    data class Reviewing(
        val queue: List<CardEntity>,
        val index: Int,
        val flipped: Boolean,
        val reviewed: Int
    ) : StudyUiState() {
        val currentCard: CardEntity get() = queue[index]
        val total: Int get() = queue.size
        val progress: Float get() = reviewed.toFloat() / total
    }

    data class SessionComplete(val reviewed: Int) : StudyUiState()
}
