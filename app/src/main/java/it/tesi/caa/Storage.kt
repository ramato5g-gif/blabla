package it.tesi.caa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Profile(val id: String, val name: String)

/** Profili: ogni utente ha tessere, impostazioni, registro d'uso e sessioni propri. */
class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun all(): List<Profile> {
        val list = runCatching {
            val a = JSONArray(prefs.getString("list", "[]"))
            List(a.length()) { i -> a.getJSONObject(i).let { Profile(it.getString("id"), it.getString("name")) } }
        }.getOrDefault(emptyList())
        return list.ifEmpty { listOf(Profile("default", "Utente 1")) }
    }

    var currentId: String
        get() = prefs.getString("current", "default") ?: "default"
        set(v) { prefs.edit().putString("current", v).commit() }

    fun current(): Profile = all().let { l -> l.firstOrNull { it.id == currentId } ?: l.first() }

    fun add(name: String): Profile {
        val p = Profile("p${System.currentTimeMillis()}", name)
        save(all() + p)
        return p
    }

    fun rename(id: String, name: String) = save(all().map { if (it.id == id) it.copy(name = name) else it })

    fun delete(id: String) {
        val rest = all().filterNot { it.id == id }
        if (rest.isEmpty()) return
        save(rest)
        dirFor(context, id).deleteRecursively()
    }

    private fun save(list: List<Profile>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString("list", a.toString()).commit()
    }

    companion object {
        fun dirFor(context: Context, id: String): File = File(context.filesDir, "profiles/$id").apply { mkdirs() }
    }
}

/** Impostazioni del profilo. */
data class Prefs(
    val columns: Int = 5,
    val showLabels: Boolean = true,
    val uppercase: Boolean = false,
    val sentenceMode: Boolean = true,
    val speakOnTap: Boolean = true,
    val showCore: Boolean = true,
    val ttsRate: Float = 0.9f,
    val ttsPitch: Float = 1.0f,
    val childLock: Boolean = false,
    val debounceMs: Int = 0,
)

class SettingsStore(dir: File) {
    private val file = File(dir, "settings.json")
    var p by mutableStateOf(load())
        private set

    private fun load(): Prefs = runCatching {
        val o = JSONObject(file.readText())
        val d = Prefs()
        Prefs(
            columns = o.optInt("columns", d.columns),
            showLabels = o.optBoolean("labels", d.showLabels),
            uppercase = o.optBoolean("upper", d.uppercase),
            sentenceMode = o.optBoolean("sentence", d.sentenceMode),
            speakOnTap = o.optBoolean("speakTap", d.speakOnTap),
            showCore = o.optBoolean("core", d.showCore),
            ttsRate = o.optDouble("rate", d.ttsRate.toDouble()).toFloat(),
            ttsPitch = o.optDouble("pitch", d.ttsPitch.toDouble()).toFloat(),
            childLock = o.optBoolean("lock", d.childLock),
            debounceMs = o.optInt("debounce", d.debounceMs),
        )
    }.getOrDefault(Prefs())

    fun update(n: Prefs) {
        p = n
        val o = JSONObject()
            .put("columns", n.columns).put("labels", n.showLabels).put("upper", n.uppercase)
            .put("sentence", n.sentenceMode).put("speakTap", n.speakOnTap).put("core", n.showCore)
            .put("rate", n.ttsRate.toDouble()).put("pitch", n.ttsPitch.toDouble())
            .put("lock", n.childLock).put("debounce", n.debounceMs)
        file.writeText(o.toString())
    }
}
