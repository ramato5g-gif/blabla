package it.tesi.caa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val ABC = listOf("ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ'?", "ÀÈÉÌÒÙ")
private val QWERTY = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM'?", "ÀÈÉÌÒÙ")

/**
 * Tastiera CAA: tasti grandi, carattere ad alta leggibilità, stampato maiuscolo,
 * suggerimenti di completamento (con il pittogramma della tessera, se esiste).
 * Ogni parola completata si aggiunge alla frase in alto.
 *
 * @param vocabulary tessere da cui suggerire le parole
 * @param history parole già scritte (suggerite per prime)
 */
@Composable
fun KeyboardPanel(
    p: Prefs,
    vocabulary: List<Card>,
    history: List<String>,
    onWord: (word: String, card: Card?) -> Unit,
    onSpeak: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf("") }
    val compact = isCompact()
    fun show(s: String) = if (p.keyboardUpper) s.uppercase(Locale.ITALIAN) else s.lowercase(Locale.ITALIAN)

    // Parola da tessera (stesso testo) oppure parola libera
    fun commit(word: String) {
        val w = word.trim().lowercase(Locale.ITALIAN)
        if (w.isEmpty()) return
        val card = vocabulary.firstOrNull { it.label.lowercase(Locale.ITALIAN) == w }
        onWord(w, card)
        current = ""
    }

    val suggestions: List<Pair<String, Card?>> = remember(current, vocabulary, history) {
        val pre = current.lowercase(Locale.ITALIAN)
        if (pre.isEmpty()) emptyList()
        else {
            val fromHistory = history.groupingBy { it }.eachCount().entries
                .filter { it.key.startsWith(pre) && it.key != pre }
                .sortedByDescending { it.value }.map { it.key }
            val fromCards = vocabulary.map { it.label.lowercase(Locale.ITALIAN) }
                .filter { it.startsWith(pre) && it != pre }
                .distinct().sortedBy { it.length }
            (fromHistory + fromCards).distinct().take(8).map { w ->
                w to vocabulary.firstOrNull { it.label.lowercase(Locale.ITALIAN) == w }
            }
        }
    }

    val keyH = if (compact) 50.dp else 62.dp
    val keySp = if (compact) 22.sp else 28.sp

    Column(modifier.background(Panel).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // ---------- Parola in scrittura ----------
        Row(Modifier.fillMaxWidth().height(if (compact) 56.dp else 68.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .border(2.dp, Accent, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    if (current.isEmpty()) "Scrivi una parola…" else show(current) + "▏",
                    fontFamily = Legible, fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 26.sp else 34.sp, color = if (current.isEmpty()) Muted else Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            KeyBox("🔊", Color(0xFFA5D6A7), Modifier.size(if (compact) 56.dp else 68.dp)) { if (current.isNotBlank()) onSpeak(current) }
            KeyBox("✔", Color(0xFF90CAF9), Modifier.size(if (compact) 56.dp else 68.dp)) { commit(current) }
        }

        // ---------- Suggerimenti ----------
        LazyRow(Modifier.fillMaxWidth().height(if (compact) 64.dp else 76.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (suggestions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Text("  I suggerimenti compaiono mentre scrivi", color = Muted, fontFamily = Legible)
                    }
                }
            }
            items(suggestions, key = { it.first }) { (w, card) ->
                Row(
                    Modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp))
                        .background(card?.wordClass?.color ?: Color.White)
                        .border(1.dp, Color(0x33000000), RoundedCornerShape(12.dp))
                        .clickable { commit(w) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (card != null && (card.imagePath != null || card.emoji.isNotBlank())) {
                        CardTile(card, false, p.keyboardUpper, Modifier.size(if (compact) 52.dp else 64.dp))
                    }
                    Text(
                        show(w), fontFamily = Legible, fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 20.sp else 24.sp, color = Ink, modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        // ---------- Tasti ----------
        val rows = if (p.keyboardAbc) ABC else QWERTY
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(keyH), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { ch ->
                    KeyBox(show(ch.toString()), Color.White, Modifier.weight(1f).fillMaxHeight(), keySp) { current += ch.lowercase(Locale.ITALIAN) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(keyH), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyBox("⌫", Color(0xFFFFCC80), Modifier.weight(1.2f).fillMaxHeight(), keySp) {
                if (current.isNotEmpty()) current = current.dropLast(1)
            }
            KeyBox("SPAZIO", Color.White, Modifier.weight(3f).fillMaxHeight(), if (compact) 16.sp else 20.sp) { commit(current) }
            KeyBox("⌨✖", Color(0xFFEF9A9A), Modifier.weight(1.2f).fillMaxHeight(), if (compact) 16.sp else 20.sp) { onClose() }
        }
    }
}

@Composable
private fun KeyBox(text: String, color: Color, modifier: Modifier, size: androidx.compose.ui.unit.TextUnit = 26.sp, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(color)
            .border(1.dp, Color(0x40000000), RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Legible, fontWeight = FontWeight.Bold, fontSize = size, color = Ink, textAlign = TextAlign.Center)
    }
}
