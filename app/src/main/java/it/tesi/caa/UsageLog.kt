package it.tesi.caa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tipi di evento registrati. */
object Ev {
    const val CARD = "TESSERA"     // tessera selezionata
    const val SENTENCE = "FRASE"   // frase composta e pronunciata
    const val CLEAR = "CANCELLA"   // frase cancellata
    const val STORY = "STORIA"     // storia aperta
}

data class LogEvent(
    val ts: Long,
    val session: String,
    val type: String,
    val cardId: String,
    val label: String,
    val topic: String,
    val wordClass: String,
    val text: String,
    val n: Int,
)

data class Session(val id: String, val name: String, val start: Long, val end: Long?, val note: String)

/**
 * Registro d'uso per la raccolta dati (tesi): ogni tessera toccata e ogni frase
 * pronunciata, con data/ora e sessione. Resta solo sul dispositivo.
 */
class UsageLog(dir: File) {
    private val logFile = File(dir, "log.csv")
    private val sessFile = File(dir, "sessions.json")
    var sessions by mutableStateOf<List<Session>>(emptyList())
        private set
    var current by mutableStateOf<Session?>(null)
        private set
    /** Cambia a ogni nuovo evento: le statistiche si aggiornano. */
    var revision by mutableIntStateOf(0)
        private set

    init {
        sessions = runCatching {
            val a = JSONArray(sessFile.readText())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Session(
                    o.getString("id"), o.getString("name"), o.getLong("start"),
                    if (o.isNull("end")) null else o.getLong("end"), o.optString("note", "")
                )
            }
        }.getOrDefault(emptyList())
        current = sessions.lastOrNull { it.end == null }
    }

    fun startSession(name: String, note: String) {
        endSession()
        val s = Session(newId("s"), name.ifBlank { "Sessione ${sessions.size + 1}" }, System.currentTimeMillis(), null, note)
        sessions = sessions + s
        current = s
        saveSessions()
    }

    fun endSession() {
        val c = current ?: return
        val ended = c.copy(end = System.currentTimeMillis())
        sessions = sessions.map { if (it.id == c.id) ended else it }
        current = null
        saveSessions()
    }

    fun updateNote(id: String, note: String) {
        sessions = sessions.map { if (it.id == id) it.copy(note = note) else it }
        if (current?.id == id) current = sessions.first { it.id == id }
        saveSessions()
    }

    fun deleteSession(id: String) {
        sessions = sessions.filterNot { it.id == id }
        if (current?.id == id) current = null
        saveSessions()
    }

    fun log(type: String, card: Card?, topic: String, text: String, n: Int) {
        val fields = listOf(
            System.currentTimeMillis().toString(), current?.id ?: "", type,
            card?.id ?: "", clean(card?.label ?: ""), clean(topic), card?.wordClass?.name ?: "",
            clean(text), n.toString()
        )
        runCatching { logFile.appendText(fields.joinToString(";") + "\n") }
        revision++
    }

    fun events(): List<LogEvent> = runCatching {
        logFile.readLines().mapNotNull { line ->
            val f = line.split(';')
            if (f.size < 9) null
            else LogEvent(f[0].toLongOrNull() ?: 0L, f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8].toIntOrNull() ?: 0)
        }
    }.getOrDefault(emptyList())

    fun clearAll() {
        logFile.delete(); sessFile.delete()
        sessions = emptyList(); current = null; revision++
    }

    private fun clean(s: String) = s.replace(';', ',').replace('\n', ' ')

    private fun saveSessions() {
        val a = JSONArray()
        sessions.forEach {
            a.put(
                JSONObject().put("id", it.id).put("name", it.name).put("start", it.start)
                    .put("end", it.end ?: JSONObject.NULL).put("note", it.note)
            )
        }
        sessFile.writeText(a.toString())
    }
}

// ======================= STATISTICHE =======================

data class Stats(
    val cardTaps: Int,
    val distinctCards: Int,
    val sentences: Int,
    val mlu: Float,
    val maxLen: Int,
    val byClass: List<Pair<String, Int>>,
    val topCards: List<Pair<String, Int>>,
    val byTopic: List<Pair<String, Int>>,
    val lastSentences: List<String>,
)

fun computeStats(ev: List<LogEvent>): Stats {
    val taps = ev.filter { it.type == Ev.CARD }
    val sent = ev.filter { it.type == Ev.SENTENCE }
    return Stats(
        cardTaps = taps.size,
        distinctCards = taps.map { it.cardId }.distinct().size,
        sentences = sent.size,
        mlu = if (sent.isEmpty()) 0f else sent.map { it.n }.average().toFloat(),
        maxLen = sent.maxOfOrNull { it.n } ?: 0,
        byClass = taps.groupingBy { it.wordClass }.eachCount().toList().sortedByDescending { it.second },
        topCards = taps.groupingBy { it.label }.eachCount().toList().sortedByDescending { it.second }.take(15),
        byTopic = taps.groupingBy { it.topic }.eachCount().toList().sortedByDescending { it.second },
        lastSentences = sent.takeLast(20).reversed().map { it.text },
    )
}

private fun fmt(ms: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY).format(Date(ms))
fun fmtShort(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(ms))

/** CSV eventi (separatore ';', si apre in Excel), una riga per evento. */
fun eventsToCsv(profile: String, ev: List<LogEvent>, sessions: List<Session>): String = buildString {
    append('\uFEFF')
    appendLine("profilo;data_ora;sessione;evento;tessera;argomento;categoria;frase;n_tessere")
    val names = sessions.associate { it.id to it.name }
    for (e in ev) {
        val cls = WordClass.entries.firstOrNull { it.name == e.wordClass }?.title ?: ""
        appendLine(listOf(profile, fmt(e.ts), names[e.session] ?: "", e.type, e.label, e.topic, cls, e.text, e.n).joinToString(";"))
    }
}

/** CSV riepilogo per sessione: utile per confrontare le sessioni nel tempo. */
fun sessionsToCsv(profile: String, ev: List<LogEvent>, sessions: List<Session>): String = buildString {
    append('\uFEFF')
    appendLine("profilo;sessione;inizio;fine;durata_min;tessere_toccate;tessere_diverse;frasi;lunghezza_media_frase;frase_piu_lunga;note")
    for (s in sessions) {
        val st = computeStats(ev.filter { it.session == s.id })
        val dur = ((s.end ?: System.currentTimeMillis()) - s.start) / 60000
        appendLine(
            listOf(
                profile, s.name, fmtShort(s.start), s.end?.let { fmtShort(it) } ?: "in corso", dur,
                st.cardTaps, st.distinctCards, st.sentences, String.format(Locale.ITALY, "%.2f", st.mlu), st.maxLen,
                s.note.replace(';', ',').replace('\n', ' ')
            ).joinToString(";")
        )
    }
}
