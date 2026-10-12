package it.tesi.caa

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

// ======================= MODELLO =======================

/** Un elemento della pagina: "c:<id tessera>" oppure "w:<parola senza simbolo>". */
data class StoryPage(val text: String, val tokens: List<String>, val imagePath: String? = null)

data class Story(val id: String, val title: String, val emoji: String, val pages: List<StoryPage>)

/**
 * Trasforma un testo in una sequenza di simboli: cerca le tessere con la stessa parola
 * (anche espressioni fino a 4 parole, es. "lavare i denti"). Le parole senza tessera
 * restano come testo.
 */
fun symbolize(text: String, cards: List<Card>): List<String> {
    val words = text.lowercase(Locale.ITALIAN)
        .replace("’", "' ")
        .replace("'", "' ")
        .replace(Regex("[^\\p{L}\\p{N}' ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    val index = HashMap<String, Card>()
    cards.sortedBy { if (it.topicId == CORE_TOPIC) 0 else 1 }.forEach { c ->
        val k = c.label.lowercase(Locale.ITALIAN).trim()
        index.putIfAbsent(k, c)
        index.putIfAbsent(k.removeSuffix("?"), c)
    }
    val out = mutableListOf<String>()
    var i = 0
    while (i < words.size) {
        var matched = false
        for (len in minOf(4, words.size - i) downTo 1) {
            val key = words.subList(i, i + len).joinToString(" ")
            val c = index[key]
            if (c != null) { out += "c:${c.id}"; i += len; matched = true; break }
        }
        if (!matched) { out += "w:${words[i]}"; i++ }
    }
    return out
}

/** Storie del profilo, salvate in stories.json. */
class StoryStore(dir: File, board: BoardStore) {
    private val file = File(dir, "stories.json")
    var stories by mutableStateOf<List<Story>>(emptyList())
        private set

    init {
        val loaded = runCatching {
            val a = JSONArray(file.readText())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val pa = o.getJSONArray("pages")
                Story(
                    o.getString("id"), o.getString("title"), o.optString("emoji", "📖"),
                    List(pa.length()) { j ->
                        val po = pa.getJSONObject(j)
                        val ta = po.getJSONArray("tokens")
                        StoryPage(po.getString("text"), List(ta.length()) { ta.getString(it) }, po.optString("img", "").ifBlank { null })
                    }
                )
            }
        }.getOrNull()
        if (loaded != null) stories = loaded
        else {
            stories = examples(board.cards)
            save()
        }
    }

    fun upsert(s: Story) {
        stories = if (stories.any { it.id == s.id }) stories.map { if (it.id == s.id) s else it } else stories + s
        save()
    }

    fun delete(id: String) { stories = stories.filterNot { it.id == id }; save() }

    private fun save() {
        val a = JSONArray()
        stories.forEach { s ->
            a.put(
                JSONObject().put("id", s.id).put("title", s.title).put("emoji", s.emoji).put(
                    "pages", JSONArray().apply {
                        s.pages.forEach { p ->
                            put(JSONObject().put("text", p.text).put("tokens", JSONArray(p.tokens)).put("img", p.imagePath ?: ""))
                        }
                    }
                )
            )
        }
        file.writeText(a.toString())
    }

    private fun examples(cards: List<Card>): List<Story> {
        fun story(id: String, title: String, emoji: String, vararg texts: String) =
            Story(id, title, emoji, texts.map { StoryPage(it, symbolize(it, cards)) })
        return listOf(
            story(
                "es_denti", "Mi lavo i denti", "🪥",
                "Prendo lo spazzolino.", "Metto il dentifricio.", "Lavare i denti sopra e sotto.",
                "Sciacquo la bocca con acqua.", "Ho finito! Bravo!"
            ),
            story(
                "es_dottore", "Vado dal dottore", "🩺",
                "Oggi vado dal dottore con la mamma.", "Aspetto un po'. Posso giocare o leggere un libro.",
                "Il dottore guarda la bocca e le orecchie.", "Se ho paura posso dire: aiuto, basta.",
                "Alla fine torno a casa contento."
            ),
            story(
                "es_giornata", "La mia giornata", "🌅",
                "La mattina mi sveglio e faccio colazione.", "Poi vado a scuola con lo zaino.",
                "Il pomeriggio gioco con gli amici.", "La sera ceno con la mamma e il papà.",
                "Poi vado a dormire. Buonanotte!"
            ),
        )
    }
}

// ======================= UI COMUNE =======================

/** Elemento della storia: tessera (simbolo + parola) o parola semplice. */
@Composable
fun StoryToken(token: String, board: BoardStore, p: Prefs, size: Int, highlighted: Boolean, onClick: (() -> Unit)? = null) {
    val m = Modifier
        .size(size.dp)
        .then(if (highlighted) Modifier.border(5.dp, Color(0xFFFF6F00), RoundedCornerShape(16.dp)) else Modifier)
    val card = if (token.startsWith("c:")) board.cards.firstOrNull { it.id == token.substring(2) } else null
    if (card != null) {
        CardTile(card, true, p.uppercase, m, onClick = onClick)
    } else {
        val word = if (token.startsWith("c:")) "?" else token.removePrefix("w:")
        Box(
            m.padding(3.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                .border(1.5.dp, Color(0x33000000), RoundedCornerShape(14.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (p.uppercase) word.uppercase() else word, fontSize = (size * 0.18f).sp,
                fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center
            )
        }
    }
}

fun tokenWord(token: String, board: BoardStore): String =
    if (token.startsWith("c:")) board.cards.firstOrNull { it.id == token.substring(2) }?.label ?: "" else token.removePrefix("w:")

// ======================= LIBRERIA (bambino) =======================

@Composable
fun StoryLibraryScreen(store: StoryStore, onOpen: (Story) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar("📖 Storie", onBack)
        if (store.stories.isEmpty()) {
            Text("Nessuna storia. Creale dall'Area terapista → Storie.", color = Muted, modifier = Modifier.padding(24.dp))
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(if (isCompact()) 150.dp else 220.dp), modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(store.stories, key = { it.id }) { s ->
                val img = rememberImage(s.pages.firstOrNull { it.imagePath != null }?.imagePath)
                Column(
                    Modifier.padding(8.dp).aspectRatio(0.9f).clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFF3E0))
                        .border(2.dp, Color(0xFFFFB74D), RoundedCornerShape(20.dp)).clickable { onOpen(s) }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(90.dp).clip(RoundedCornerShape(14.dp)))
                    else Text(s.emoji.ifBlank { "📖" }, fontSize = 60.sp)
                    Text(s.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
                    Text("${s.pages.size} pagine", color = Muted)
                }
            }
        }
    }
}

// ======================= LETTORE =======================

/**
 * Lettura della storia, una pagina alla volta: immagine, simboli e testo.
 * 🔊 legge la frase intera; 👆 legge parola per parola illuminando ogni simbolo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryReaderScreen(
    story: Story,
    board: BoardStore,
    p: Prefs,
    say: (Card?, String) -> Unit,
    speakSequence: (List<String>, (Int) -> Unit) -> Unit,
    stopSpeaking: () -> Unit,
    onClose: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    var highlight by remember { mutableIntStateOf(-1) }
    val compact = isCompact()
    DisposableEffect(Unit) { onDispose { stopSpeaking() } }
    if (story.pages.isEmpty()) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            TopBar(story.title, onClose)
            Text("Questa storia non ha pagine.", color = Muted, modifier = Modifier.padding(24.dp))
        }
        return
    }
    val pg = story.pages[page.coerceIn(0, story.pages.size - 1)]
    LaunchedEffect(page) { highlight = -1; stopSpeaking() }

    Column(Modifier.fillMaxSize().background(Color(0xFFFFFBF2))) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFFFE0B2)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReaderButton("✖", Color.White, Modifier.size(64.dp, 56.dp)) { onClose() }
            Text(
                "${story.emoji} ${story.title}", fontSize = if (compact) 18.sp else 24.sp, maxLines = 2, fontWeight = FontWeight.Bold, color = Ink,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Text("${page + 1}/${story.pages.size}", fontSize = 18.sp, color = Muted)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
            val tokenSize = if (compact) ((maxWidth.value - 8f) / 3f).coerceIn(80f, 120f).toInt() else ((maxWidth.value - 40f) / 6f).coerceIn(90f, 150f).toInt()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val img = rememberImage(pg.imagePath)
                if (img != null) {
                    Image(
                        img, null, contentScale = ContentScale.Fit,
                        modifier = Modifier.height(if (compact) 180.dp else 260.dp).clip(RoundedCornerShape(18.dp))
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    pg.tokens.forEachIndexed { i, t ->
                        StoryToken(t, board, p, tokenSize, highlight == i) {
                            val c = if (t.startsWith("c:")) board.cards.firstOrNull { it.id == t.substring(2) } else null
                            say(c, tokenWord(t, board))
                        }
                    }
                }
                Text(
                    if (p.uppercase) pg.text.uppercase() else pg.text,
                    fontSize = if (compact) 22.sp else 30.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFFFE0B2)).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            val bm = if (compact) Modifier.weight(1f).height(64.dp) else Modifier.size(110.dp, 80.dp)
            ReaderButton("◀", Color.White, bm, enabled = page > 0) { page-- }
            ReaderButton("🔊", Color(0xFF81C784), bm) { say(null, pg.text) }
            ReaderButton("👆", Color(0xFF90CAF9), bm) {
                speakSequence(pg.tokens.map { tokenWord(it, board) }) { i -> highlight = i }
            }
            ReaderButton("▶", Color.White, bm, enabled = page < story.pages.size - 1) { page++ }
        }
    }
}

@Composable
private fun ReaderButton(symbol: String, color: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(if (enabled) color else Color(0xFFEEEEEE))
            .border(1.dp, Color(0x33000000), RoundedCornerShape(18.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { Text(symbol, fontSize = 36.sp, color = if (enabled) Ink else Color(0xFFBDBDBD)) }
}

// ======================= EDITOR STORIE =======================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StoryEditorScreen(
    store: StoryStore,
    board: BoardStore,
    p: Prefs,
    requestImage: (ImageSource, (String) -> Unit) -> Unit,
    onPreview: (Story) -> Unit,
    onExportPdf: (Story) -> Unit,
    onBack: () -> Unit,
) {
    var selectedId by remember { mutableStateOf(store.stories.firstOrNull()?.id) }
    var editPage by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val story = store.stories.firstOrNull { it.id == selectedId }
    val compact = isCompact()
    val newStory = {
        val s = Story(newId("st"), "Nuova storia", "📖", listOf(StoryPage("", emptyList())))
        store.upsert(s); selectedId = s.id
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar("Storie", onBack) {
            if (compact) OutlinedButton(onClick = newStory) { Text("+ Nuova storia") }
            if (story != null) OutlinedButton(onClick = { onPreview(story) }) { Text("▶ Prova lettura") }
            if (story != null) Button(onClick = { onExportPdf(story) }) { Text("📄 Scarica PDF") }
        }
        if (compact) {
            LazyRow(Modifier.fillMaxWidth().background(Panel).padding(2.dp)) {
                items(store.stories, key = { it.id }) { s ->
                    TopicTile(s.emoji, null, s.title, s.id == selectedId, false, small = true) { selectedId = s.id }
                }
            }
        }
        Row(Modifier.fillMaxSize()) {
            if (!compact) Column(Modifier.width(250.dp).fillMaxHeight().background(Panel).padding(6.dp)) {
                Button(onClick = newStory, modifier = Modifier.fillMaxWidth()) { Text("+ Nuova storia") }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(store.stories, key = { it.id }) { s ->
                        TopicTile(s.emoji, null, s.title, s.id == selectedId, false, Modifier.fillMaxWidth()) { selectedId = s.id }
                    }
                }
            }
            if (story == null) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("Crea o scegli una storia", color = Muted)
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = story.title, onValueChange = { store.upsert(story.copy(title = it)) }, singleLine = true,
                            label = { Text("Titolo") }, modifier = Modifier.width(260.dp)
                        )
                        OutlinedTextField(
                            value = story.emoji, onValueChange = { store.upsert(story.copy(emoji = it.take(8))) }, singleLine = true,
                            label = { Text("Emoji") }, modifier = Modifier.width(110.dp)
                        )
                        TextButton(onClick = { confirmDelete = true }) { Text("🗑 Elimina storia", color = Color(0xFFC62828)) }
                    }
                    story.pages.forEachIndexed { i, pg ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Pagina ${i + 1}", fontWeight = FontWeight.Bold, color = Ink)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { editPage = i }) { Text("✏ Modifica") }
                                OutlinedButton(onClick = { store.upsert(story.copy(pages = swap(story.pages, i, i - 1))) }) { Text("▲") }
                                OutlinedButton(onClick = { store.upsert(story.copy(pages = swap(story.pages, i, i + 1))) }) { Text("▼") }
                                OutlinedButton(onClick = { store.upsert(story.copy(pages = story.pages.filterIndexed { j, _ -> j != i })) }) { Text("🗑") }
                            }
                            Text(pg.text.ifBlank { "(pagina vuota: tocca Modifica)" }, color = if (pg.text.isBlank()) Muted else Ink, fontSize = 18.sp)
                            FlowRow {
                                pg.tokens.forEach { t -> StoryToken(t, board, p, if (compact) 60 else 70, false) }
                            }
                        }
                    }
                    Button(onClick = { store.upsert(story.copy(pages = story.pages + StoryPage("", emptyList()))) }) { Text("+ Aggiungi pagina") }
                }
            }
        }
    }

    val ep = editPage
    if (story != null && ep != null && ep in story.pages.indices) {
        PageEditDialog(
            initial = story.pages[ep],
            board = board,
            p = p,
            requestImage = requestImage,
            onSave = { np -> store.upsert(story.copy(pages = story.pages.mapIndexed { j, old -> if (j == ep) np else old })); editPage = null },
            onDismiss = { editPage = null }
        )
    }
    if (confirmDelete && story != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare \"${story.title}\"?") },
            confirmButton = {
                TextButton(onClick = { store.delete(story.id); selectedId = store.stories.firstOrNull()?.id; confirmDelete = false }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
        )
    }
}

private fun <T> swap(list: List<T>, i: Int, j: Int): List<T> {
    if (i !in list.indices || j !in list.indices) return list
    val l = list.toMutableList()
    val tmp = l[i]; l[i] = l[j]; l[j] = tmp
    return l
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PageEditDialog(
    initial: StoryPage,
    board: BoardStore,
    p: Prefs,
    requestImage: (ImageSource, (String) -> Unit) -> Unit,
    onSave: (StoryPage) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by remember { mutableStateOf(initial) }
    var showArasaac by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pagina della storia") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = d.text, onValueChange = { d = d.copy(text = it) },
                    label = { Text("Testo della pagina (es. Oggi vado al parco con il papà.)") },
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { d = d.copy(tokens = symbolize(d.text, board.cards)) }) { Text("✨ Simboli dal testo") }
                    OutlinedButton(onClick = { showPicker = true }) { Text("+ Tessera") }
                    if (d.tokens.isNotEmpty()) TextButton(onClick = { d = d.copy(tokens = emptyList()) }) { Text("Svuota") }
                }
                Text("Tocca un simbolo per toglierlo.", color = Muted, fontSize = 12.sp)
                FlowRow {
                    d.tokens.forEachIndexed { i, t ->
                        StoryToken(t, board, p, 80, false) { d = d.copy(tokens = d.tokens.filterIndexed { j, _ -> j != i }) }
                    }
                }
                SectionLabel("Immagine della pagina (facoltativa)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val img = rememberImage(d.imagePath)
                    Box(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).background(Panel), contentAlignment = Alignment.Center) {
                        if (img != null) Image(img, null, contentScale = ContentScale.Fit, modifier = Modifier.size(100.dp))
                        else Text("—", color = Muted)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { requestImage(ImageSource.GALLERY) { d = d.copy(imagePath = it) } }) { Text("🖼 Galleria") }
                            OutlinedButton(onClick = { requestImage(ImageSource.CAMERA) { d = d.copy(imagePath = it) } }) { Text("📷 Foto") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { showArasaac = true }) { Text("🔎 ARASAAC") }
                            if (d.imagePath != null) TextButton(onClick = { d = d.copy(imagePath = null) }) { Text("✖ Togli") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val tokens = if (d.tokens.isEmpty() && d.text.isNotBlank()) symbolize(d.text, board.cards) else d.tokens
                onSave(d.copy(text = d.text.trim(), tokens = tokens))
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
    if (showArasaac) {
        ArasaacDialog(
            initialQuery = "",
            onPicked = { path -> d = d.copy(imagePath = path); showArasaac = false },
            onDismiss = { showArasaac = false }
        )
    }
    if (showPicker) {
        CardPickerDialog(board, p, onPick = { c -> d = d.copy(tokens = d.tokens + "c:${c.id}"); showPicker = false }, onDismiss = { showPicker = false })
    }
}

/** Ricerca di una tessera esistente per aggiungerla alla pagina. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPickerDialog(board: BoardStore, p: Prefs, onPick: (Card) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val results = remember(q, board.cards) {
        val k = q.trim().lowercase(Locale.ITALIAN)
        if (k.isEmpty()) board.cards.take(60) else board.cards.filter { it.label.lowercase(Locale.ITALIAN).contains(k) }.take(60)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scegli una tessera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("Cerca parola") }, modifier = Modifier.fillMaxWidth())
                LazyVerticalGrid(columns = GridCells.Adaptive(90.dp), modifier = Modifier.height(340.dp)) {
                    items(results, key = { it.id }) { c ->
                        CardTile(c, true, p.uppercase, Modifier.aspectRatio(1f), onClick = { onPick(c) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}
