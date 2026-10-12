package it.tesi.caa

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Da dove prendere un'immagine (galleria e fotocamera sono gestite dall'Activity). */
enum class ImageSource { GALLERY, CAMERA }

/**
 * Modifica di argomenti e tessere. A sinistra gli argomenti (con "Parole base"),
 * a destra le tessere dell'argomento scelto. Tocca una tessera per modificarla.
 */
@Composable
fun EditorScreen(
    board: BoardStore,
    p: Prefs,
    voices: VoiceStore,
    requestImage: (ImageSource, (String) -> Unit) -> Unit,
    openVoice: (Card) -> Unit,
    onBack: () -> Unit,
) {
    var topicId by remember { mutableStateOf(board.topics.firstOrNull()?.id ?: CORE_TOPIC) }
    var editCard by remember { mutableStateOf<Card?>(null) }
    var editCardIsNew by remember { mutableStateOf(false) }
    var editTopic by remember { mutableStateOf<Topic?>(null) }
    var editTopicIsNew by remember { mutableStateOf(false) }
    var confirmDeleteTopic by remember { mutableStateOf<Topic?>(null) }
    var askBatch by remember { mutableStateOf(false) }
    var batchCards by remember { mutableStateOf<List<Card>?>(null) }
    var mergeResult by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar("Modifica tessere e argomenti", onBack) {
            OutlinedButton(onClick = { mergeResult = board.mergeRepertoire() }) { Text("📥 Repertorio completo") }
            Button(onClick = { askBatch = true }) { Text("🔎 Pittogrammi automatici") }
        }
        Row(Modifier.fillMaxSize()) {
            // ---------- Argomenti ----------
            Column(Modifier.width(250.dp).fillMaxHeight().background(Panel).padding(6.dp)) {
                Button(onClick = {
                    editTopic = Topic(newId("t"), "", "📁"); editTopicIsNew = true
                }, modifier = Modifier.fillMaxWidth()) { Text("+ Nuovo argomento") }
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "__core") {
                        TopicTile("⭐", null, "Parole base", topicId == CORE_TOPIC, false, Modifier.fillMaxWidth()) { topicId = CORE_TOPIC }
                    }
                    items(board.topics, key = { it.id }) { t ->
                        Column {
                            TopicTile(t.emoji, t.imagePath, t.name, t.id == topicId, false, Modifier.fillMaxWidth()) { topicId = t.id }
                            if (t.id == topicId) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SmallBtn("✏") { editTopic = t; editTopicIsNew = false }
                                    SmallBtn("▲") { board.moveTopic(t.id, -1) }
                                    SmallBtn("▼") { board.moveTopic(t.id, 1) }
                                    SmallBtn("🗑") { confirmDeleteTopic = t }
                                }
                            }
                        }
                    }
                }
            }
            // ---------- Tessere ----------
            Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                Text(
                    "${board.topicName(topicId)} · ${board.cardsOf(topicId).size} tessere · tocca per modificare",
                    color = Muted, modifier = Modifier.padding(4.dp)
                )
                LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), modifier = Modifier.fillMaxSize()) {
                    item(key = "__new") {
                        Box(
                            Modifier.aspectRatio(1f).padding(3.dp).clip(RoundedCornerShape(14.dp)).background(Panel)
                                .clickable {
                                    editCard = Card(newId("c"), topicId, ""); editCardIsNew = true
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("+\nNuova\ntessera", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Accent) }
                    }
                    items(board.cardsOf(topicId), key = { it.id }) { c ->
                        Box {
                            CardTile(c, true, p.uppercase, Modifier.aspectRatio(1f), onClick = { editCard = c; editCardIsNew = false })
                            if (voices.has(c.id)) Text("🔊", modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                    }
                }
            }
        }
    }

    editCard?.let { c ->
        CardEditDialog(
            initial = c,
            isNew = editCardIsNew,
            topics = board.topics,
            requestImage = requestImage,
            openVoice = openVoice,
            onSave = { board.upsertCard(it); editCard = null },
            onDelete = { board.deleteCard(c.id); voices.delete(c.id); editCard = null },
            onMove = { d -> board.moveCard(c.id, d) },
            onDismiss = { editCard = null }
        )
    }
    editTopic?.let { t ->
        TopicEditDialog(
            initial = t,
            isNew = editTopicIsNew,
            requestImage = requestImage,
            onSave = { board.upsertTopic(it); topicId = it.id; editTopic = null },
            onDismiss = { editTopic = null }
        )
    }
    if (askBatch) {
        val inTopic = board.cardsOf(topicId).filter { it.imagePath == null }
        val all = board.cards.filter { it.imagePath == null }
        AlertDialog(
            onDismissRequest = { askBatch = false },
            title = { Text("Pittogrammi ARASAAC automatici") },
            text = {
                Text(
                    "Per ogni tessera senza immagine cerco il pittogramma ARASAAC con la stessa parola e uso il primo risultato. " +
                            "Serve internet. Controlla poi le tessere: alcune parole possono avere pittogrammi non adatti.\n\n" +
                            "In \"${board.topicName(topicId)}\": ${inTopic.size} tessere · in tutto: ${all.size} tessere."
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = inTopic.isNotEmpty(), onClick = { askBatch = false; batchCards = inTopic }) { Text("Questo argomento") }
                    Button(enabled = all.isNotEmpty(), onClick = { askBatch = false; batchCards = all }) { Text("Tutte") }
                }
            },
            dismissButton = { TextButton(onClick = { askBatch = false }) { Text("Annulla") } }
        )
    }
    batchCards?.let { list -> BatchArasaacDialog(list, board) { batchCards = null } }
    mergeResult?.let { n ->
        AlertDialog(
            onDismissRequest = { mergeResult = null },
            title = { Text("Repertorio") },
            text = { Text(if (n == 0) "Hai già tutte le tessere del repertorio." else "Aggiunte $n tessere mancanti dal repertorio.") },
            confirmButton = { TextButton(onClick = { mergeResult = null }) { Text("OK") } }
        )
    }
    confirmDeleteTopic?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmDeleteTopic = null },
            title = { Text("Eliminare \"${t.name}\"?") },
            text = { Text("Verranno eliminate anche le ${board.cardsOf(t.id).size} tessere dell'argomento.") },
            confirmButton = {
                TextButton(onClick = { board.deleteTopic(t.id); topicId = CORE_TOPIC; confirmDeleteTopic = null }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteTopic = null }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun SmallBtn(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(40.dp)) { Text(text) }
}

/** Anteprima dell'immagine scelta (foto/pittogramma) o dell'emoji. */
@Composable
private fun ImagePreview(imagePath: String?, emoji: String, bg: Color) {
    val img = rememberImage(imagePath)
    Box(Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, contentScale = ContentScale.Fit, modifier = Modifier.size(108.dp))
        else Text(emoji.ifBlank { "?" }, fontSize = 64.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditDialog(
    initial: Card,
    isNew: Boolean,
    topics: List<Topic>,
    requestImage: (ImageSource, (String) -> Unit) -> Unit,
    openVoice: (Card) -> Unit,
    onSave: (Card) -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by remember { mutableStateOf(initial) }
    var showArasaac by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val topicOptions = listOf(CORE_TOPIC) + topics.map { it.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nuova tessera" else "Modifica tessera") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImagePreview(d.imagePath, d.emoji, d.wordClass.color)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { showArasaac = true }) { Text("🔎 Pittogramma ARASAAC") }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { requestImage(ImageSource.GALLERY) { d = d.copy(imagePath = it) } }) { Text("🖼 Galleria") }
                            OutlinedButton(onClick = { requestImage(ImageSource.CAMERA) { d = d.copy(imagePath = it) } }) { Text("📷 Foto") }
                        }
                        if (d.imagePath != null) TextButton(onClick = { d = d.copy(imagePath = null) }) { Text("✖ Togli immagine (usa emoji)") }
                    }
                }
                OutlinedTextField(
                    value = d.label, onValueChange = { d = d.copy(label = it) }, singleLine = true,
                    label = { Text("Parola sulla tessera (es. mela)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = d.phrase, onValueChange = { d = d.copy(phrase = it) }, singleLine = true,
                    label = { Text("Frase da pronunciare da sola (facoltativa, es. Voglio la mela)") }, modifier = Modifier.fillMaxWidth()
                )
                if (d.imagePath == null) {
                    OutlinedTextField(
                        value = d.emoji, onValueChange = { d = d.copy(emoji = it.take(8)) }, singleLine = true,
                        label = { Text("Emoji (se non c'è immagine)") }, modifier = Modifier.fillMaxWidth()
                    )
                }
                SectionLabel("Categoria (colore)")
                ChoiceRow(WordClass.entries, d.wordClass, { it.title }) { d = d.copy(wordClass = it) }
                SectionLabel("Argomento")
                ChoiceRow(topicOptions, d.topicId, { id -> if (id == CORE_TOPIC) "⭐ Parole base" else topics.firstOrNull { it.id == id }?.name ?: id }) {
                    d = d.copy(topicId = it)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = d.label.isNotBlank(), onClick = { onSave(d); openVoice(d) }) { Text("🎙 Registra voce") }
                    if (!isNew) {
                        OutlinedButton(onClick = { onMove(-1) }) { Text("◀ Prima") }
                        OutlinedButton(onClick = { onMove(1) }) { Text("Dopo ▶") }
                        TextButton(onClick = { confirmDelete = true }) { Text("🗑 Elimina", color = Color(0xFFC62828)) }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = d.label.isNotBlank(), onClick = { onSave(d.copy(label = d.label.trim(), phrase = d.phrase.trim())) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )

    if (showArasaac) {
        ArasaacDialog(
            initialQuery = d.label,
            onPicked = { path -> d = d.copy(imagePath = path); showArasaac = false },
            onDismiss = { showArasaac = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare la tessera \"${d.label}\"?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicEditDialog(
    initial: Topic,
    isNew: Boolean,
    requestImage: (ImageSource, (String) -> Unit) -> Unit,
    onSave: (Topic) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by remember { mutableStateOf(initial) }
    var showArasaac by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nuovo argomento" else "Modifica argomento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImagePreview(d.imagePath, d.emoji, Panel)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { showArasaac = true }) { Text("🔎 ARASAAC") }
                        OutlinedButton(onClick = { requestImage(ImageSource.GALLERY) { d = d.copy(imagePath = it) } }) { Text("🖼 Galleria") }
                        if (d.imagePath != null) TextButton(onClick = { d = d.copy(imagePath = null) }) { Text("✖ Usa emoji") }
                    }
                }
                OutlinedTextField(
                    value = d.name, onValueChange = { d = d.copy(name = it) }, singleLine = true,
                    label = { Text("Nome argomento (es. Scuola)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = d.emoji, onValueChange = { d = d.copy(emoji = it.take(8)) }, singleLine = true,
                    label = { Text("Emoji") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(enabled = d.name.isNotBlank(), onClick = { onSave(d.copy(name = d.name.trim())) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
    if (showArasaac) {
        ArasaacDialog(
            initialQuery = d.name,
            onPicked = { path -> d = d.copy(imagePath = path); showArasaac = false },
            onDismiss = { showArasaac = false }
        )
    }
}

/** Ricerca pittogrammi ARASAAC (serve la connessione internet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArasaacDialog(initialQuery: String, onPicked: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<Int>>(emptyList()) }
    val thumbs = remember { mutableStateMapOf<Int, ImageBitmap>() }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var searchToken by remember { mutableStateOf(0) }

    fun search() {
        if (query.isBlank()) return
        searchToken++
        loading = true; message = ""; results = emptyList(); thumbs.clear()
        scope.launch {
            val r = Arasaac.search(query)
            loading = false
            results = r
            if (r.isEmpty()) message = "Nessun pittogramma trovato (o connessione assente)."
        }
    }

    LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) search() }
    LaunchedEffect(results) {
        for (id in results) {
            if (!thumbs.containsKey(id)) Arasaac.thumbnail(id)?.let { thumbs[id] = it }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pittogrammi ARASAAC") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text("Cerca (es. mangiare)") }, modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { search() }, modifier = Modifier.padding(start = 6.dp)) { Text("Cerca") }
                }
                if (loading) CircularProgressIndicator()
                if (message.isNotEmpty()) Text(message, color = Muted)
                LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), modifier = Modifier.height(320.dp)) {
                    items(results, key = { it }) { id ->
                        val bmp = thumbs[id]
                        Box(
                            Modifier.aspectRatio(1f).padding(3.dp).clip(RoundedCornerShape(10.dp)).background(Panel)
                                .clickable {
                                    loading = true
                                    scope.launch {
                                        val path = Arasaac.download(context, id)
                                        loading = false
                                        if (path != null) onPicked(path) else message = "Download non riuscito."
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) Image(bmp, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                            else Text("…", color = Muted)
                        }
                    }
                }
                Text(Arasaac.CREDITS, fontSize = 10.sp, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}

/** Registrazione della voce di una tessera. */
@Composable
fun VoiceDialog(card: Card, voices: VoiceStore, onDismiss: () -> Unit) {
    var recording by remember { mutableStateOf(false) }
    var has by remember { mutableStateOf(voices.has(card.id)) }
    var error by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { voices.stopRecording(); voices.stopPlayback() } }

    AlertDialog(
        onDismissRequest = { if (!recording) onDismiss() },
        title = { Text("Voce per \"${card.label}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CardTile(card, true, false, Modifier.size(110.dp))
                Text("Registra la parola o la frase (es. la voce di un familiare). Sostituisce la sintesi vocale per questa tessera.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!recording) {
                        Button(onClick = { error = !voices.startRecording(card.id); recording = !error }) { Text("⏺ Registra") }
                    } else {
                        Button(onClick = { voices.stopRecording(); recording = false; has = voices.has(card.id) }) { Text("⏹ Ferma") }
                    }
                    OutlinedButton(enabled = has && !recording, onClick = { voices.play(card.id) }) { Text("▶ Ascolta") }
                    OutlinedButton(enabled = has && !recording, onClick = { voices.delete(card.id); has = false }) { Text("🗑") }
                }
                if (recording) Text("● Registrazione in corso…", color = Color(0xFFD32F2F))
                if (error) Text("Impossibile registrare: controlla il permesso del microfono.")
            }
        },
        confirmButton = { TextButton(enabled = !recording, onClick = onDismiss) { Text("Chiudi") } }
    )
}

/** Scarica in sequenza i pittogrammi ARASAAC per un elenco di tessere. */
@Composable
fun BatchArasaacDialog(cards: List<Card>, board: BoardStore, onClose: () -> Unit) {
    val context = LocalContext.current
    var done by remember { mutableStateOf(0) }
    var found by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        for (c in cards) {
            val query = c.label.removeSuffix("?").trim()
            val ids = if (query.isBlank()) emptyList() else Arasaac.search(query)
            val path = ids.firstOrNull()?.let { Arasaac.download(context, it) }
            if (path != null) {
                board.cards.firstOrNull { it.id == c.id }?.let { board.upsertCard(it.copy(imagePath = path)) }
                found++
            }
            done++
        }
        finished = true
    }
    AlertDialog(
        onDismissRequest = { if (finished) onClose() },
        title = { Text(if (finished) "Completato" else "Scarico i pittogrammi…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!finished) CircularProgressIndicator()
                Text("$done / ${cards.size} tessere · pittogrammi trovati: $found")
                if (finished && found < cards.size) Text("Le tessere senza risultato restano con l'emoji.", color = Muted)
                Text(Arasaac.CREDITS, fontSize = 10.sp, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(if (finished) "OK" else "Interrompi") } }
    )
}
