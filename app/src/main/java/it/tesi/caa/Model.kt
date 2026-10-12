package it.tesi.caa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Categorie grammaticali con i colori della chiave di Fitzgerald modificata,
 * la convenzione più diffusa nelle tabelle di CAA.
 */
enum class WordClass(val title: String, val color: Color) {
    PERSONE("Persone", Color(0xFFFFE082)),
    AZIONI("Azioni", Color(0xFFA5D6A7)),
    DESCRIZIONI("Descrizioni", Color(0xFF90CAF9)),
    COSE("Cose", Color(0xFFFFCC80)),
    SOCIALE("Sociale", Color(0xFFF8BBD0)),
    DOMANDE("Domande", Color(0xFFCE93D8)),
    ALTRO("Altro", Color(0xFFE0E0E0)),
}

/** Argomento speciale: le "parole base" (vocabolario nucleare) sempre visibili. */
const val CORE_TOPIC = "core"

data class Topic(
    val id: String,
    val name: String,
    val emoji: String,
    val imagePath: String? = null,
)

data class Card(
    val id: String,
    val topicId: String,
    val label: String,
    val phrase: String = "",
    val emoji: String = "",
    val imagePath: String? = null,
    val wordClass: WordClass = WordClass.COSE,
) {
    /** Testo pronunciato quando la tessera si usa da sola. */
    val spoken: String get() = phrase.ifBlank { label }
}

fun newId(prefix: String): String = prefix + System.currentTimeMillis().toString(36) + (100..999).random()

private fun Topic.toJson() = JSONObject().put("id", id).put("name", name).put("emoji", emoji).put("img", imagePath ?: "")
private fun Card.toJson() = JSONObject().put("id", id).put("topic", topicId).put("label", label).put("phrase", phrase)
    .put("emoji", emoji).put("img", imagePath ?: "").put("class", wordClass.name)

private fun topicFrom(o: JSONObject) = Topic(
    o.getString("id"), o.getString("name"), o.optString("emoji", ""),
    o.optString("img", "").ifBlank { null }
)

private fun cardFrom(o: JSONObject) = Card(
    o.getString("id"), o.getString("topic"), o.getString("label"), o.optString("phrase", ""),
    o.optString("emoji", ""), o.optString("img", "").ifBlank { null },
    WordClass.entries.firstOrNull { it.name == o.optString("class") } ?: WordClass.COSE
)

/** Tessere e argomenti di un profilo, salvati in board.json. */
class BoardStore(dir: File) {
    private val file = File(dir, "board.json")
    var topics by mutableStateOf<List<Topic>>(emptyList())
        private set
    var cards by mutableStateOf<List<Card>>(emptyList())
        private set

    init {
        val loaded = runCatching {
            val o = JSONObject(file.readText())
            val t = o.getJSONArray("topics").let { a -> List(a.length()) { topicFrom(a.getJSONObject(it)) } }
            val c = o.getJSONArray("cards").let { a -> List(a.length()) { cardFrom(a.getJSONObject(it)) } }
            t to c
        }.getOrNull()
        if (loaded != null) {
            topics = loaded.first; cards = loaded.second
        } else {
            topics = Repertoire.topics; cards = Repertoire.cards
            save()
        }
    }

    fun cardsOf(topicId: String): List<Card> = cards.filter { it.topicId == topicId }
    fun topicName(id: String): String = if (id == CORE_TOPIC) "Parole base" else topics.firstOrNull { it.id == id }?.name ?: ""

    fun upsertTopic(t: Topic) {
        topics = if (topics.any { it.id == t.id }) topics.map { if (it.id == t.id) t else it } else topics + t
        save()
    }

    fun deleteTopic(id: String) {
        topics = topics.filterNot { it.id == id }
        cards = cards.filterNot { it.topicId == id }
        save()
    }

    fun moveTopic(id: String, delta: Int) {
        val i = topics.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in topics.indices) return
        val l = topics.toMutableList()
        val tmp = l[i]; l[i] = l[j]; l[j] = tmp
        topics = l; save()
    }

    fun upsertCard(c: Card) {
        cards = if (cards.any { it.id == c.id }) cards.map { if (it.id == c.id) c else it } else cards + c
        save()
    }

    fun deleteCard(id: String) {
        cards = cards.filterNot { it.id == id }
        save()
    }

    /** Sposta la tessera prima/dopo all'interno del suo argomento. */
    fun moveCard(id: String, delta: Int) {
        val c = cards.firstOrNull { it.id == id } ?: return
        val same = cards.indices.filter { cards[it].topicId == c.topicId }
        val pos = same.indexOfFirst { cards[it].id == id }
        val np = pos + delta
        if (pos < 0 || np !in same.indices) return
        val l = cards.toMutableList()
        val a = same[pos]; val b = same[np]
        val tmp = l[a]; l[a] = l[b]; l[b] = tmp
        cards = l; save()
    }

    /** Aggiunge argomenti e tessere del repertorio che mancano. Restituisce quante tessere ha aggiunto. */
    fun mergeRepertoire(): Int {
        val topicIds = topics.map { it.id }.toSet()
        val cardIds = cards.map { it.id }.toSet()
        val newTopics = Repertoire.topics.filter { it.id !in topicIds }
        val newCards = Repertoire.cards.filter { it.id !in cardIds }
        topics = topics + newTopics
        cards = cards + newCards
        save()
        return newCards.size
    }

    private fun save() {
        val o = JSONObject()
        o.put("topics", JSONArray().apply { topics.forEach { put(it.toJson()) } })
        o.put("cards", JSONArray().apply { cards.forEach { put(it.toJson()) } })
        file.writeText(o.toString())
    }
}

