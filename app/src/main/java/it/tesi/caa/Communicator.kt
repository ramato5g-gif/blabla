package it.tesi.caa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Comunicatore: barra della frase in alto, parole base, argomenti e tessere.
 * Ogni tocco e ogni frase vengono registrati per le statistiche.
 */
@Composable
fun CommunicatorScreen(
    board: BoardStore,
    p: Prefs,
    log: UsageLog,
    say: (Card?, String) -> Unit,
    onOpenStories: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val strip = remember { mutableStateListOf<Card>() }
    var topicId by rememberSaveable { mutableStateOf(board.topics.firstOrNull()?.id ?: CORE_TOPIC) }
    var lastTap by remember { mutableLongStateOf(0L) }
    if (board.topics.none { it.id == topicId } && topicId != CORE_TOPIC) topicId = board.topics.firstOrNull()?.id ?: CORE_TOPIC

    fun tap(c: Card) {
        val now = System.currentTimeMillis()
        if (p.debounceMs > 0 && now - lastTap < p.debounceMs) return // tocchi involontari ravvicinati
        lastTap = now
        log.log(Ev.CARD, c, board.topicName(c.topicId), "", 0)
        if (p.sentenceMode) {
            strip += c
            if (p.speakOnTap) say(c, c.label)
        } else {
            say(c, c.spoken)
        }
    }

    fun speakStrip() {
        if (strip.isEmpty()) return
        val text = strip.joinToString(" ") { it.label }
        say(null, text)
        log.log(Ev.SENTENCE, null, "", text, strip.size)
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // ---------- Barra frase + comandi ----------
        Row(
            Modifier.fillMaxWidth().height(if (p.sentenceMode) 116.dp else 64.dp).background(Panel).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (p.sentenceMode) {
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Color.White)
                        .border(1.dp, Color(0x33000000), RoundedCornerShape(14.dp))
                        .clickable { speakStrip() }
                        .horizontalScroll(rememberScrollState()).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (strip.isEmpty()) Text("  Tocca le tessere per comporre una frase", color = Muted, fontSize = 18.sp)
                    strip.forEach { c ->
                        CardTile(c, p.showLabels, p.uppercase, Modifier.size(96.dp))
                    }
                }
                ActionButton("🔊", Color(0xFF66BB6A)) { speakStrip() }
                ActionButton("⌫", Color(0xFFFFB74D)) { if (strip.isNotEmpty()) strip.removeAt(strip.size - 1) }
                ActionButton("✖", Color(0xFFEF9A9A)) {
                    if (strip.isNotEmpty()) { log.log(Ev.CLEAR, null, "", "", strip.size); strip.clear() }
                }
            } else {
                Text("Tocca una tessera per parlare", color = Muted, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
            }
            ActionButton("📖", Color(0xFFFFE0B2)) { onOpenStories() }
            MenuButton(p.childLock, log.current != null, onOpenMenu)
        }

        // ---------- Parole base ----------
        val core = board.cardsOf(CORE_TOPIC)
        if (p.showCore && core.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 4.dp)) {
                items(core, key = { it.id }) { c ->
                    CardTile(c, p.showLabels, p.uppercase, Modifier.size(100.dp), onClick = { tap(c) })
                }
            }
        }

        // ---------- Argomenti + tessere ----------
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val landscape = maxWidth > maxHeight
            val cards = board.cardsOf(topicId)
            val grid: @Composable (Modifier) -> Unit = { m ->
                if (cards.isEmpty()) {
                    Box(m, contentAlignment = Alignment.Center) { Text("Nessuna tessera in questo argomento", color = Muted) }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(p.columns), modifier = m.padding(4.dp)) {
                        items(cards, key = { it.id }) { c ->
                            CardTile(c, p.showLabels, p.uppercase, Modifier.aspectRatio(1f), onClick = { tap(c) })
                        }
                    }
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.width(190.dp).fillMaxHeight().background(Panel).padding(4.dp)) {
                        items(board.topics, key = { it.id }) { t ->
                            TopicTile(t.emoji, t.imagePath, t.name, t.id == topicId, p.uppercase, Modifier.fillMaxWidth()) { topicId = t.id }
                        }
                        item(key = "__core") {
                            TopicTile("⭐", null, "Parole base", topicId == CORE_TOPIC, p.uppercase, Modifier.fillMaxWidth()) { topicId = CORE_TOPIC }
                        }
                    }
                    grid(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyRow(Modifier.fillMaxWidth().background(Panel).padding(4.dp)) {
                        items(board.topics, key = { it.id }) { t ->
                            TopicTile(t.emoji, t.imagePath, t.name, t.id == topicId, p.uppercase) { topicId = t.id }
                        }
                        item(key = "__core") {
                            TopicTile("⭐", null, "Parole base", topicId == CORE_TOPIC, p.uppercase) { topicId = CORE_TOPIC }
                        }
                    }
                    grid(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ActionButton(symbol: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 84.dp, height = 96.dp).clip(RoundedCornerShape(14.dp)).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(symbol, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink) }
}

/** Accesso all'Area terapista: con la protezione bambino serve una pressione lunga. */
@Composable
private fun MenuButton(locked: Boolean, recording: Boolean, onOpen: () -> Unit) {
    val m = Modifier.size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
    Box(
        if (locked) m.pointerInput(Unit) { detectTapGestures(onLongPress = { onOpen() }) } else m.clickable(onClick = onOpen),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚙", fontSize = 30.sp, color = if (locked) Color(0x66000000) else Ink)
            if (recording) Text("● REC", fontSize = 11.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
        }
    }
}
