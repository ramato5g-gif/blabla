package it.tesi.caa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import java.util.Calendar
import java.util.Locale

/** Filtro del periodo per le statistiche. */
private sealed class Period(val title: String) {
    object All : Period("Tutto")
    object Today : Period("Oggi")
    object Week : Period("7 giorni")
    object Month : Period("30 giorni")
    data class Of(val session: Session) : Period(session.name)
}

/**
 * Statistiche d'uso per la tesi: tessere toccate, varietà lessicale, frasi,
 * lunghezza media della frase (in tessere), categorie grammaticali, argomenti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    log: UsageLog,
    profileName: String,
    onExport: (fileName: String, csv: String) -> Unit,
    onBack: () -> Unit,
) {
    var period by remember { mutableStateOf<Period>(Period.All) }
    var editNote by remember { mutableStateOf<Session?>(null) }
    var noteText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val rev = log.revision
    val all = remember(rev) { log.events() }
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    val filtered = when (val pp = period) {
        Period.All -> all
        Period.Today -> {
            val midnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            all.filter { it.ts >= midnight }
        }
        Period.Week -> all.filter { it.ts >= now - 7 * day }
        Period.Month -> all.filter { it.ts >= now - 30 * day }
        is Period.Of -> all.filter { it.session == pp.session.id }
    }
    val st = computeStats(filtered)
    val safeName = profileName.replace(Regex("[^A-Za-z0-9]"), "_")

    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar("Statistiche · $profileName", onBack) {
            OutlinedButton(onClick = { onExport("eventi_${safeName}.csv", eventsToCsv(profileName, filtered, log.sessions)) }) { Text("📤 Eventi CSV") }
            Button(onClick = { onExport("sessioni_${safeName}.csv", sessionsToCsv(profileName, all, log.sessions)) }) { Text("📤 Sessioni CSV") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Periodo")
            val options = listOf(Period.All, Period.Today, Period.Week, Period.Month) + log.sessions.reversed().map { Period.Of(it) }
            ChoiceRow(options, period, { it.title }) { period = it }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Tessere toccate", "${st.cardTaps}", Modifier.weight(1f))
                StatBox("Tessere diverse", "${st.distinctCards}", Modifier.weight(1f))
                StatBox("Frasi pronunciate", "${st.sentences}", Modifier.weight(1f))
                StatBox("Lunghezza media frase", String.format(Locale.ITALY, "%.1f", st.mlu), Modifier.weight(1f))
                StatBox("Frase più lunga", "${st.maxLen}", Modifier.weight(1f))
            }
            Text(
                "Lunghezza media frase = numero medio di tessere per frase pronunciata (indice di complessità, analogo all'LME in tessere).",
                fontSize = 12.sp, color = Muted
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Categorie grammaticali")
                    val maxC = st.byClass.maxOfOrNull { it.second } ?: 0
                    st.byClass.forEach { (cls, n) ->
                        val wc = WordClass.entries.firstOrNull { it.name == cls }
                        BarRow(wc?.title ?: "—", n, maxC, wc?.color ?: Color.LightGray)
                    }
                    SectionLabel("Argomenti")
                    val maxT = st.byTopic.maxOfOrNull { it.second } ?: 0
                    st.byTopic.forEach { (t, n) -> BarRow(t.ifBlank { "—" }, n, maxT, Color(0xFF90CAF9)) }
                }
                Column(Modifier.weight(1f)) {
                    SectionLabel("Tessere più usate")
                    val maxK = st.topCards.maxOfOrNull { it.second } ?: 0
                    st.topCards.forEach { (l, n) -> BarRow(l, n, maxK, Color(0xFFA5D6A7)) }
                }
            }

            SectionLabel("Ultime frasi")
            if (st.lastSentences.isEmpty()) Text("Nessuna frase nel periodo.", color = Muted)
            st.lastSentences.forEach { Text("• $it", color = Ink) }

            SectionLabel("Sessioni (${log.sessions.size})")
            log.sessions.reversed().forEach { s ->
                val ss = computeStats(all.filter { it.session == s.id })
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.name}  ·  ${fmtShort(s.start)}" + (if (s.end == null) "  ● in corso" else ""), fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            "Tessere ${ss.cardTaps} · diverse ${ss.distinctCards} · frasi ${ss.sentences} · lungh. media ${String.format(Locale.ITALY, "%.1f", ss.mlu)}",
                            color = Muted
                        )
                        if (s.note.isNotBlank()) Text("Note: ${s.note}", color = Muted, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = { editNote = s; noteText = s.note }) { Text("📝 Note") }
                    OutlinedButton(onClick = { log.deleteSession(s.id) }) { Text("🗑") }
                }
            }
            TextButton(onClick = { confirmClear = true }) { Text("Cancella tutti i dati registrati di questo profilo", color = Color(0xFFC62828)) }
        }
    }

    editNote?.let { s ->
        AlertDialog(
            onDismissRequest = { editNote = null },
            title = { Text("Note: ${s.name}") },
            text = {
                OutlinedTextField(
                    value = noteText, onValueChange = { noteText = it },
                    label = { Text("Osservazioni del logopedista") }, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { log.updateNote(s.id, noteText); editNote = null }) { Text("Salva") } },
            dismissButton = { TextButton(onClick = { editNote = null }) { Text("Annulla") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Cancellare tutti i dati?") },
            text = { Text("Eventi e sessioni di questo profilo verranno eliminati. Le tessere restano. Fai prima un backup se ti servono.") },
            confirmButton = { TextButton(onClick = { log.clearAll(); confirmClear = false; period = Period.All }) { Text("Cancella") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } }
        )
    }
}
