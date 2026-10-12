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

    val compact = isCompact()
    val cols = if (compact) p.phoneColumns else p.columns

    val stripView: @Composable (Modifier, Int) -> Unit = { m, tile ->
        Row(
            m.clip(RoundedCornerShape(14.dp)).background(Color.White)
                .border(1.dp, Color(0x33000000), RoundedCornerShape(14.dp))
                .clickable { speakStrip() }
                .horizontalScroll(rememberScrollState()).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (strip.isEmpty()) Text("  Tocca le tessere per comporre una frase", color = Muted, fontSize = if (compact) 15.sp else 18.sp)
            strip.forEach { c -> CardTile(c, p.showLabels, p.uppercase, Modifier.size(tile.dp)) }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // ---------- Barra frase + comandi ----------
        if (compact) {
            Column(Modifier.fillMaxWidth().background(Panel).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p.sentenceMode) stripView(Modifier.fillMaxWidth().height(84.dp), 74)
                Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.sentenceMode) {
                        ActionButton("🔊", Color(0xFF66BB6A), Modifier.weight(1f).fillMaxHeight()) { speakStrip() }
                        ActionButton("⌫", Color(0xFFFFB74D), Modifier.weight(1f).fillMaxHeight()) { if (strip.isNotEmpty()) strip.removeAt(strip.size - 1) }
                        ActionButton("✖", Color(0xFFEF9A9A), Modifier.weight(1f).fillMaxHeight()) {
                            if (strip.isNotEmpty()) { log.log(Ev.CLEAR, null, "", "", strip.size); strip.clear() }
                        }
                    }
                    ActionButton("📖", Color(0xFFFFE0B2), Modifier.weight(1f).fillMaxHeight()) { onOpenStories() }
                    MenuButton(p.childLock, log.current != null, Modifier.weight(1f).fillMaxHeight(), onOpenMenu)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(if (p.sentenceMode) 116.dp else 72.dp).background(Panel).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (p.sentenceMode) {
                    stripView(Modifier.weight(1f).fillMaxHeight(), 96)
                    ActionButton("🔊", Color(0xFF66BB6A), Modifier.size(84.dp, 96.dp)) { speakStrip() }
                    ActionButton("⌫", Color(0xFFFFB74D), Modifier.size(84.dp, 96.dp)) { if (strip.isNotEmpty()) strip.removeAt(strip.size - 1) }
                    ActionButton("✖", Color(0xFFEF9A9A), Modifier.size(84.dp, 96.dp)) {
                        if (strip.isNotEmpty()) { log.log(Ev.CLEAR, null, "", "", strip.size); strip.clear() }
                    }
                } else {
                    Text("Tocca una tessera per parlare", color = Muted, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                }
                ActionButton("📖", Color(0xFFFFE0B2), Modifier.size(84.dp, 96.dp)) { onOpenStories() }
                MenuButton(p.childLock, log.current != null, Modifier.size(64.dp, 96.dp), onOpenMenu)
            }
        }

        // ---------- Parole base ----------
        val core = board.cardsOf(CORE_TOPIC)
        if (p.showCore && core.isNotEmpty()) {
            val sz = if (compact) 78 else 100
            LazyRow(Modifier.fillMaxWidth().height((sz + 4).dp).padding(horizontal = 4.dp)) {
                items(core, key = { it.id }) { c ->
                    CardTile(c, p.showLabels, p.uppercase, Modifier.size(sz.dp), onClick = { tap(c) })
                }
            }
        }

        // ---------- Argomenti + tessere ----------
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val sideList = maxWidth > maxHeight && maxWidth >= 600.dp
            val cards = board.cardsOf(topicId)
            val grid: @Composable (Modifier) -> Unit = { m ->
                if (cards.isEmpty()) {
                    Box(m, contentAlignment = Alignment.Center) { Text("Nessuna tessera in questo argomento", color = Muted) }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(cols), modifier = m.padding(4.dp)) {
                        items(cards, key = { it.id }) { c ->
                            CardTile(c, p.showLabels, p.uppercase, Modifier.aspectRatio(1f), onClick = { tap(c) })
                        }
                    }
                }
            }
            if (sideList) {
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
                    LazyRow(Modifier.fillMaxWidth().background(Panel).padding(2.dp)) {
                        items(board.topics, key = { it.id }) { t ->
                            TopicTile(t.emoji, t.imagePath, t.name, t.id == topicId, p.uppercase, small = true) { topicId = t.id }
                        }
                        item(key = "__core") {
                            TopicTile("⭐", null, "Parole base", topicId == CORE_TOPIC, p.uppercase, small = true) { topicId = CORE_TOPIC }
                        }
                    }
                    grid(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ActionButton(symbol: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(symbol, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink) }
}

/** Accesso all'Area terapista: con la protezione bambino serve una pressione lunga. */
@Composable
private fun MenuButton(locked: Boolean, recording: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val m = modifier.clip(RoundedCornerShape(14.dp)).background(Color.White)
    Box(
        if (locked) m.pointerInput(Unit) { detectTapGestures(onLongPress = { onOpen() }) } else m.clickable(onClick = onOpen),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚙", fontSize = 28.sp, color = if (locked) Color(0x66000000) else Ink)
            if (recording) Text("● REC", fontSize = 10.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
        }
    }
}
