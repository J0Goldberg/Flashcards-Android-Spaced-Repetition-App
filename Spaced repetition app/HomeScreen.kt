package com.flashcards.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStudy: (deckId: Long, deckName: String) -> Unit,
    onStats: () -> Unit,
    onEditor: (deckId: Long?) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val totalDue = state.decks.sumOf { it.stats.due }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Flashcards", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        if (totalDue > 0) {
                            Text(
                                "$totalDue card${if (totalDue != 1) "s" else ""} due",
                                fontSize = 12.sp,
                                color = Color(0xFFE24B4A)
                            )
                        } else {
                            Text("All caught up!", fontSize = 12.sp, color = Color(0xFF0F6E56))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onStats) {
                        Icon(Icons.Default.BarChart, contentDescription = "Stats")
                    }
                    IconButton(onClick = { onEditor(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "New deck")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.decks, key = { it.deck.id }) { row ->
                DeckCard(
                    row     = row,
                    onStudy = { onStudy(row.deck.id, row.deck.name) },
                    onEdit  = { onEditor(row.deck.id) }
                )
            }
        }
    }
}

@Composable
private fun DeckCard(row: DeckRow, onStudy: () -> Unit, onEdit: () -> Unit) {
    val deckColor = runCatching { Color(row.deck.colorHex.toColorInt()) }
        .getOrDefault(MaterialTheme.colorScheme.primary)

    Card(
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(deckColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("📚", fontSize = 20.sp)
            }

            Spacer(Modifier.width(14.dp))

            // Name + stats
            Column(Modifier.weight(1f)) {
                Text(row.deck.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatChip("${row.stats.total} cards")
                    StatChip(
                        "${row.stats.due} due",
                        color = if (row.stats.due > 0) Color(0xFFE24B4A) else null
                    )
                    StatChip("${row.stats.mastered} mastered")
                }
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.width(4.dp))

            Button(
                onClick  = onStudy,
                colors   = ButtonDefaults.buttonColors(containerColor = deckColor),
                shape    = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Study", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StatChip(text: String, color: Color? = null) {
    Text(
        text     = text,
        fontSize = 12.sp,
        color    = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    )
}
