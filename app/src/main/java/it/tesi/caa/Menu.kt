package it.tesi.caa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Area terapista: tutto ciò che il bambino non deve toccare. */
@Composable
fun TherapistMenu(
    profileName: String,
    session: Session?,
    pinned: Boolean,
    onCommunicator: () -> Unit,
    onEditor: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onStories: () -> Unit,
    onProfiles: () -> Unit,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onBackup: () -> Unit,
    onTogglePin: () -> Unit,
    onInfo: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Area terapista", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onProfiles) { Text("👤 $profileName") }
        }
        // Sessione
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (session != null) Color(0xFFFFEBEE) else Panel).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (session != null) "● Sessione in corso: ${session.name}" else "Nessuna sessione in corso",
                    fontWeight = FontWeight.Bold, color = if (session != null) Color(0xFFC62828) else Ink
                )
                Text(
                    if (session != null) "Iniziata il ${fmtShort(session.start)}. Ogni tessera e frase viene associata a questa sessione."
                    else "Avvia una sessione per raggruppare i dati di un incontro (utile per confrontare gli incontri nella tesi).",
                    color = Muted, fontSize = 13.sp
                )
            }
            if (session != null) Button(onClick = onEndSession) { Text("■ Termina") }
            else Button(onClick = onStartSession) { Text("▶ Avvia sessione") }
        }
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MenuTile("💬", "Comunicatore", Color(0xFF1565C0), Modifier.weight(1f), onCommunicator)
            MenuTile("✏️", "Tessere e argomenti", Color(0xFF2E7D32), Modifier.weight(1f), onEditor)
            MenuTile("📖", "Storie", Color(0xFFEF6C00), Modifier.weight(1f), onStories)
            MenuTile("📊", "Statistiche", Color(0xFF6A1B9A), Modifier.weight(1f), onStats)
            MenuTile("⚙️", "Impostazioni", Color(0xFF455A64), Modifier.weight(1f), onSettings)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBackup) { Text("💾 Backup / ripristino") }
            OutlinedButton(onClick = onTogglePin) { Text(if (pinned) "🔓 Sblocca app" else "📌 Blocca app sullo schermo") }
            OutlinedButton(onClick = onInfo) { Text("ℹ️ Informazioni e crediti") }
        }
    }
}

@Composable
private fun MenuTile(emoji: String, title: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(color).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 44.sp)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun SettingsScreen(store: SettingsStore, onTestVoice: () -> Unit, onBack: () -> Unit) {
    val p = store.p
    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar("Impostazioni", onBack) { OutlinedButton(onClick = onTestVoice) { Text("🔊 Prova voce") } }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Tessere")
            Text("Colonne della griglia: ${p.columns}", color = Ink)
            Slider(p.columns.toFloat(), { store.update(p.copy(columns = it.roundToInt())) }, valueRange = 2f..10f, steps = 7)
            SwitchRow("Mostra la parola sotto l'immagine", p.showLabels) { store.update(p.copy(showLabels = it)) }
            SwitchRow("Testo in MAIUSCOLO (stampato maiuscolo)", p.uppercase) { store.update(p.copy(uppercase = it)) }
            SwitchRow("Mostra la riga delle parole base", p.showCore) { store.update(p.copy(showCore = it)) }

            SectionLabel("Comunicazione")
            SwitchRow("Componi frasi (le tessere si accodano nella barra in alto)", p.sentenceMode) { store.update(p.copy(sentenceMode = it)) }
            SwitchRow("Pronuncia la tessera al tocco", p.speakOnTap) { store.update(p.copy(speakOnTap = it)) }
            Text("Velocità voce: %.2f".format(p.ttsRate), color = Ink)
            Slider(p.ttsRate, { store.update(p.copy(ttsRate = it)) }, valueRange = 0.5f..1.5f)
            Text("Tono voce: %.2f".format(p.ttsPitch), color = Ink)
            Slider(p.ttsPitch, { store.update(p.copy(ttsPitch = it)) }, valueRange = 0.5f..1.6f)

            SectionLabel("Accessibilità e protezione")
            Text(
                "Ignora tocchi ravvicinati: " + (if (p.debounceMs == 0) "no" else "${p.debounceMs} ms") +
                        "  (utile con tremori o tocchi ripetuti involontari)", color = Ink
            )
            ChoiceRow(listOf(0, 300, 600, 1000, 1500), p.debounceMs, { if (it == 0) "No" else "$it ms" }) { store.update(p.copy(debounceMs = it)) }
            SwitchRow("Protezione bambino: Area terapista con pressione lunga su ⚙", p.childLock) { store.update(p.copy(childLock = it)) }
        }
    }
}

@Composable
fun InfoScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar("Informazioni e crediti", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bla Bla", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                "Comunicatore di Comunicazione Aumentativa Alternativa con tessere e argomenti personalizzabili, " +
                        "barra di composizione della frase, vocabolario nucleare (parole base), colori della chiave di Fitzgerald, " +
                        "sintesi vocale e voci registrate, raccolta dati d'uso per sessione.", color = Ink
            )
            SectionLabel("Pittogrammi")
            Text(Arasaac.CREDITS, color = Ink)
            Text(
                "I pittogrammi ARASAAC sono distribuiti con licenza Creative Commons BY-NC-SA: uso non commerciale, " +
                        "citando la fonte e condividendo allo stesso modo. Va riportato questo credito anche nella tesi.", color = Muted
            )
            SectionLabel("Dati e privacy")
            Text(
                "Tutti i dati (tessere, foto, voci, registri d'uso) restano solo su questo dispositivo. " +
                        "Nei profili usa iniziali o codici, non nomi completi. Le esportazioni CSV vanno trattate come dati sanitari " +
                        "e usate solo con il consenso informato previsto dal protocollo di tesi.", color = Ink
            )
            SectionLabel("Dati registrati")
            Text(
                "Per ogni tessera: data e ora, sessione, tessera, argomento, categoria grammaticale. Per ogni frase: testo e " +
                        "numero di tessere. Le statistiche calcolano tessere toccate, tessere diverse (varietà lessicale), frasi, " +
                        "lunghezza media della frase in tessere e distribuzione per categoria e argomento.", color = Ink
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSessionDialog(defaultName: String, onStart: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova sessione") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nome (es. Incontro 3)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note iniziali (obiettivi, contesto)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onStart(name.trim(), note.trim()) }) { Text("▶ Avvia") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialog(store: ProfileStore, onSelect: (Profile) -> Unit, onDismiss: () -> Unit) {
    var profiles by remember { mutableStateOf(store.all()) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Profile?>(null) }
    val currentId = store.current().id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profili") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ogni profilo ha tessere, impostazioni, sessioni e statistiche proprie. Usa iniziali o un codice.", color = Muted)
                profiles.forEach { pr ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (pr.id == currentId) Color(0xFFC8E6C9) else Panel).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(pr.name, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                        if (pr.id != currentId) Button(onClick = { onSelect(pr) }) { Text("Usa") }
                        OutlinedButton(onClick = { renaming = pr; renameText = pr.name }) { Text("✏") }
                        if (pr.id != currentId && profiles.size > 1) OutlinedButton(onClick = { toDelete = pr }) { Text("🗑") }
                    }
                }
                renaming?.let { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(renameText, { renameText = it }, singleLine = true, label = { Text("Nuovo nome") }, modifier = Modifier.weight(1f))
                        TextButton(enabled = renameText.isNotBlank(), onClick = {
                            store.rename(r.id, renameText.trim()); profiles = store.all(); renaming = null
                        }) { Text("OK") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("Nuovo profilo") }, modifier = Modifier.weight(1f))
                    TextButton(enabled = newName.isNotBlank(), onClick = {
                        store.add(newName.trim()); profiles = store.all(); newName = ""
                    }) { Text("Aggiungi") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare il profilo \"${d.name}\"?") },
            text = { Text("Verranno eliminati tessere, foto, sessioni e dati di questo profilo.") },
            confirmButton = { TextButton(onClick = { store.delete(d.id); profiles = store.all(); toDelete = null }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla") } }
        )
    }
}
