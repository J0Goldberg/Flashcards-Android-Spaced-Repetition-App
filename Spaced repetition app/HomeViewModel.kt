package com.flashcards.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.data.model.DeckEntity
import com.flashcards.data.repository.FlashcardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FlashcardsRepository
) : ViewModel() {

    private val _deckStats = MutableStateFlow<Map<Long, Repository.DeckStats>>(emptyMap())

    val uiState: StateFlow<HomeUiState> = repository.observeDecks()
        .onEach { decks -> refreshStats(decks) }
        .combine(_deckStats) { decks, stats ->
            HomeUiState(
                decks = decks.map { deck ->
                    DeckRow(deck = deck, stats = stats[deck.id] ?: emptyStats())
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun emptyStats() = FlashcardsRepository.DeckStats(0, 0, 0, 0f)

    private fun refreshStats(decks: List<DeckEntity>) {
        viewModelScope.launch {
            val map = decks.associate { it.id to repository.getDeckStats(it.id) }
            _deckStats.value = map
        }
    }

    fun deleteDeck(deck: DeckEntity) {
        viewModelScope.launch { repository.deleteDeck(deck) }
    }
}

// Alias to avoid import collision
private typealias Repository = FlashcardsRepository

data class HomeUiState(val decks: List<DeckRow> = emptyList())

data class DeckRow(
    val deck: DeckEntity,
    val stats: FlashcardsRepository.DeckStats
)
