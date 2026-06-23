package com.flashcards.ui.study

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flashcards.domain.algorithm.CbrAlgorithm

// ── Confidence colour palette (mirrors Brainscape) ───────────────────────────

private val confidenceColors = mapOf(
    1 to Color(0xFFE24B4A),
    2 to Color(0xFFEF9F27),
    3 to Color(0xFFEF9F27),
    4 to Color(0xFF1D9E75),
    5 to Color(0xFF0F6E56)
)
private val confidenceBg = mapOf(
    1 to Color(0xFFFCEBEB),
    2 to Color(0xFFFAEEDA),
    3 to Color(0xFFFAEEDA),
    4 to Color(0xFFE1F5EE),
    5 to Color(0xFFE1F5EE)
)

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    deckName: String,
    onBack: () -> Unit,
    viewModel: StudyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deckName, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                StudyUiState.Loading    -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                StudyUiState.NothingDue -> NothingDueContent(onBack)
                is StudyUiState.Reviewing -> ReviewContent(
                    state   = s,
                    onFlip  = viewModel::flip,
                    onGrade = viewModel::grade
                )
                is StudyUiState.SessionComplete -> DoneContent(
                    reviewed = s.reviewed,
                    onBack   = onBack
                )
            }
        }
    }
}

// ── Review content ───────────────────────────────────────────────────────────

@Composable
private fun ReviewContent(
    state: StudyUiState.Reviewing,
    onFlip: () -> Unit,
    onGrade: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress bar + counter
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.weight(1f).height(5.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${state.reviewed}/${state.total}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))

        // Flash card
        FlipCard(
            front    = state.currentCard.front,
            back     = state.currentCard.back,
            flipped  = state.flipped,
            onClick  = { if (!state.flipped) onFlip() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(Modifier.height(20.dp))

        // Controls
        if (!state.flipped) {
            Button(
                onClick  = onFlip,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text("Show answer", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Think about your answer before revealing it",
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            ConfidenceRater(onGrade = onGrade)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Flip card ────────────────────────────────────────────────────────────────

@Composable
private fun FlipCard(
    front: String,
    back: String,
    flipped: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "card_flip"
    )

    Card(
        modifier  = modifier
            .graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }
            .clickable(onClick = onClick),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (rotation <= 90f) {
                // Front
                CardFace(label = "QUESTION", content = front)
            } else {
                // Back (rendered mirrored back into normal orientation)
                Box(Modifier.graphicsLayer { rotationY = 180f }.fillMaxSize()) {
                    CardFace(label = "ANSWER", content = back)
                }
            }
        }
    }
}

@Composable
private fun CardFace(label: String, content: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text     = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color    = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(vertical = 8.dp, horizontal = 16.dp)
        )
        Box(
            Modifier.weight(1f).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text      = content,
                fontSize  = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )
        }
    }
}

// ── Confidence rater ─────────────────────────────────────────────────────────

@Composable
private fun ConfidenceRater(onGrade: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "How confident did you feel?",
            fontSize = 13.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            (1..5).forEach { rating ->
                ConfidenceButton(
                    rating   = rating,
                    modifier = Modifier.weight(1f),
                    onClick  = { onGrade(rating) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "1 = review again soon  ·  5 = won't see for a long time",
            fontSize  = 11.sp,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConfidenceButton(rating: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color  = confidenceColors[rating] ?: Color.Gray
    val bgColor = confidenceBg[rating] ?: Color.White
    val label  = listOf("NOT\nAT ALL", "BARELY", "SOME-\nWHAT", "MOSTLY", "PER-\nFECT")[rating - 1]

    OutlinedButton(
        onClick  = onClick,
        modifier = modifier.height(72.dp),
        shape    = RoundedCornerShape(10.dp),
        border   = BorderStroke(1.dp, color),
        colors   = ButtonDefaults.outlinedButtonColors(
            containerColor = bgColor,
            contentColor   = color
        ),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "$rating",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Medium,
                color      = color
            )
            Text(
                text      = label,
                fontSize  = 8.sp,
                fontWeight = FontWeight.Medium,
                color     = color,
                textAlign = TextAlign.Center,
                lineHeight = 10.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}

// ── Done / nothing-due screens ───────────────────────────────────────────────

@Composable
private fun DoneContent(reviewed: Int, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✓", fontSize = 52.sp, color = Color(0xFF0F6E56))
        Spacer(Modifier.height(16.dp))
        Text("Session complete!", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "You reviewed $reviewed card${if (reviewed != 1) "s" else ""}.",
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Cards are scheduled based on your confidence — come back tomorrow for the next batch.",
            fontSize  = 13.sp,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Back to decks")
        }
    }
}

@Composable
private fun NothingDueContent(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎉", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text("All caught up!", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "No cards are due right now. Check back later.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Back to decks")
        }
    }
}
