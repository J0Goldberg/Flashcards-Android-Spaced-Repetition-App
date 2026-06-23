package com.flashcards.data.local

import android.content.Context
import android.net.Uri
import com.flashcards.data.model.CardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Parses a CSV file (URI from Storage Access Framework) into [CardEntity] objects.
 *
 * Supported formats
 * -----------------
 * Plain:   Hello,Hola
 * Quoted:  "Hello, world",Hola
 * Escaped: "She said ""hi""",Response
 * Header:  front,back  (auto-detected and skipped)
 * Tabs:    Hello\tHola  (tab-separated also accepted)
 */
object CsvImporter {

    data class ImportResult(
        val cards: List<CardEntity>,
        val skipped: Int,
        val errorMessage: String? = null
    ) {
        val success: Boolean get() = errorMessage == null
    }

    suspend fun import(context: Context, uri: Uri, deckId: Long): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ImportResult(emptyList(), 0, "Could not open file.")
                val text = stream.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }
                parseText(text, deckId)
            } catch (e: Exception) {
                ImportResult(emptyList(), 0, "Error reading file: ${e.localizedMessage}")
            }
        }

    fun parseText(text: String, deckId: Long): ImportResult {
        val rows = parseCsv(text)
        if (rows.isEmpty()) return ImportResult(emptyList(), 0, "File is empty.")

        val cards = mutableListOf<CardEntity>()
        var skipped = 0
        val now = System.currentTimeMillis()

        rows.forEachIndexed { index, row ->
            val front = row.getOrNull(0)?.trim() ?: ""
            val back  = row.getOrNull(1)?.trim() ?: ""

            if (index == 0 &&
                front.lowercase() in setOf("front", "question", "term") &&
                back.lowercase()  in setOf("back", "answer", "definition")
            ) return@forEachIndexed

            if (front.isNotEmpty() && back.isNotEmpty()) {
                cards += CardEntity(deckId = deckId, front = front, back = back, createdAtMs = now + index)
            } else {
                skipped++
            }
        }

        return if (cards.isEmpty()) {
            ImportResult(emptyList(), skipped, "No valid cards found. Check that your CSV has front and back columns.")
        } else {
            ImportResult(cards, skipped)
        }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0
        val delimiter = if (text.contains('\t') && !text.contains(',')) '\t' else ','

        while (i < text.length) {
            val ch = text[i]
            val next = if (i + 1 < text.length) text[i + 1] else '\u0000'

            when {
                inQuotes -> when {
                    ch == '"' && next == '"' -> { field.append('"'); i++ }
                    ch == '"'               -> inQuotes = false
                    else                    -> field.append(ch)
                }
                ch == '"'        -> inQuotes = true
                ch == delimiter  -> { row += field.toString(); field = StringBuilder() }
                ch == '\r' && next == '\n' -> {
                    row += field.toString(); field = StringBuilder()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf(); i++
                }
                ch == '\n' || ch == '\r' -> {
                    row += field.toString(); field = StringBuilder()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }
}
