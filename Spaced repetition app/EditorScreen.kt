package com.flashcards.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashcards.data.local.CsvImporter
import com.flashcards.data.model.CardEntity
import com.flashcards.data.model.DeckEntity
import com.flashcards.data.repository.FlashcardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: FlashcardsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val deckId: Long = savedStateHandle["deckId"] ?: -1L
    private val isNew: Boolean get() = deckId == -1L

    private val _deckName = MutableStateFlow("")
    val deckName: StateFlow<String> = _deckName.asStateFlow()

    private val _cards = MutableStateFlow<List<CardEntry>>(listOf(CardEntry()))
    val cards: StateFlow<List<CardEntry>> = _cards.asStateFlow()

    private val _importBanner = MutableStateFlow<ImportBanner?>(null)
    val importBanner: StateFlow<ImportBanner?> = _importBanner.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        if (!isNew) {
            viewModelScope.launch {
                val deck = repository.observeDecks().first().find { it.id == deckId } ?: return@launch
                _deckName.value = deck.name
                repository.observeCards(deckId).first().let { existing ->
                    if (existing.isNotEmpty())
                        _cards.value = existing.map { CardEntry(id = it.id.toString(), front = it.front, back = it.back) }
                }
            }
        }
    }

    fun onNameChange(name: String) { _deckName.value = name }
    fun addCard() { _cards.value = _cards.value + CardEntry() }
    fun removeCard(entryId: String) {
        if (_cards.value.size <= 1) return
        _cards.value = _cards.value.filter { it.id != entryId }
    }
    fun updateCard(entryId: String, front: String? = null, back: String? = null) {
        _cards.value = _cards.value.map { c ->
            if (c.id == entryId) c.copy(front = front ?: c.front, back = back ?: c.back) else c
        }
    }

    fun importCsv(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            val result = CsvImporter.import(context, uri, deckId = 0L)
            if (!result.success) {
                _importBanner.value = ImportBanner.Error(result.errorMessage ?: "Import failed.")
                return@launch
            }
            if (_deckName.value.isBlank()) {
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst(); cursor.getString(idx)
                } ?: ""
                if (name.isNotBlank())
                    _deckName.value = name.replace(Regex("\\.[^.]+$"), "").replace(Regex("[-_]"), " ")
            }
            val hasContent = _cards.value.any { it.front.isNotBlank() || it.back.isNotBlank() }
            val imported = result.cards.map { CardEntry(front = it.front, back = it.back) }
            _cards.value = if (hasContent)
                _cards.value.filter { it.front.isNotBlank() || it.back.isNotBlank() } + imported
            else imported
            _importBanner.value = ImportBanner.Success(result.cards.size, result.skipped)
        }
    }

    fun dismissBanner() { _importBanner.value = null }

    fun save() {
        val name = _deckName.value.trim()
        val valid = _cards.value.filter { it.front.isNotBlank() && it.back.isNotBlank() }
        if (name.isBlank() || valid.isEmpty()) return
        viewModelScope.launch {
            val colors = listOf("#534AB7","#0F6E56","#993C1D","#185FA5","#854F0B","#993556")
            val savedDeckId = if (isNew) {
                repository.saveDeck(DeckEntity(name = name, colorHex = colors.random()))
            } else {
                val existing = repository.observeDecks().first().find { it.id == deckId }
                repository.saveDeck(existing!!.copy(name = name)); deckId
            }
            valid.forEach { entry ->
                repository.saveCard(CardEntity(deckId = savedDeckId, front = entry.front, back = entry.back))
            }
            _saved.value = true
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            val deck = repository.observeDecks().first().find { it.id == deckId } ?: return@launch
            repository.deleteDeck(deck)
            _saved.value = true
        }
    }
}

data class CardEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val front: String = "",
    val back: String = ""
)

sealed class ImportBanner {
    data class Success(val imported: Int, val skipped: Int) : ImportBanner()
    data class Error(val message: String) : ImportBanner()
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    isNew: Boolean,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val context    = LocalContext.current
    val deckName   by viewModel.deckName.collectAsState()
    val cards      by viewModel.cards.collectAsState()
    val banner     by viewModel.importBanner.collectAsState()
    val saved      by viewModel.saved.collectAsState()
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(saved) { if (saved) onBack() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.importCsv(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New deck" else "Edit deck", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE24B4A))
                        }
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
            item {
                OutlinedTextField(
                    value = deckName, onValueChange = viewModel::onNameChange,
                    label = { Text("Deck name") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            item { CsvPickerButton(onPick = { picker.launch("text/*") }) }
            banner?.let { b -> item { ImportBannerView(banner = b, onDismiss = viewModel::dismissBanner) } }
            item {
                val validCount = cards.count { it.front.isNotBlank() && it.back.isNotBlank() }
                Text("${validCount} card${if (validCount != 1) "s" else ""}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(cards, key = { _, c -> c.id }) { index, card ->
                CardEditorItem(index = index + 1, card = card,
                    onFront = { viewModel.updateCard(card.id, front = it) },
                    onBack  = { viewModel.updateCard(card.id, back = it) },
                    onRemove = { viewModel.removeCard(card.id) },
                    canRemove = cards.size > 1)
            }
            item {
                OutlinedButton(onClick = viewModel::addCard, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Add card")
                }
            }
            item {
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp)) {
                    Text(if (isNew) "Create deck" else "Save changes", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete deck?") },
            text  = { Text("This will permanently delete the deck and all its cards. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { showDelete = false; viewModel.delete() }) { Text("Delete", color = Color(0xFFE24B4A)) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CsvPickerButton(onPick: () -> Unit) {
    OutlinedButton(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        shape    = RoundedCornerShape(12.dp),
        border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        colors   = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Import CSV", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("front,back — one card per row", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ImportBannerView(banner: ImportBanner, onDismiss: () -> Unit) {
    val (fg, message) = when (banner) {
        is ImportBanner.Success -> Pair(Color(0xFF085041), buildString {
            append("Imported ${banner.imported} card${if (banner.imported != 1) "s" else ""}")
            if (banner.skipped > 0) append(", ${banner.skipped} row${if (banner.skipped != 1) "s" else ""} skipped")
        })
        is ImportBanner.Error   -> Pair(Color(0xFFA32D2D), banner.message)
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .border(BorderStroke(0.5.dp, fg.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (banner is ImportBanner.Success) "✓" else "!", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(message, color = fg, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(4.dp)) {
            Text("✕", color = fg, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CardEditorItem(index: Int, card: CardEntry, onFront: (String) -> Unit, onBack: (String) -> Unit, onRemove: () -> Unit, canRemove: Boolean) {
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Card $index", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = card.front, onValueChange = onFront, label = { Text("Front", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = card.back, onValueChange = onBack, label = { Text("Back", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp))
        }
    }
}
