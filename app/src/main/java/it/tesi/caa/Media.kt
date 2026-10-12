package it.tesi.caa

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// ======================= IMMAGINI =======================

/** Cache delle immagini già caricate (evita di ridecodificarle scorrendo la griglia). */
object ImageCache {
    private val map = LinkedHashMap<String, ImageBitmap>()
    fun get(path: String): ImageBitmap? {
        map[path]?.let { return it }
        val bmp = runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() ?: return null
        if (map.size > 300) map.remove(map.keys.first())
        map[path] = bmp
        return bmp
    }
}

@Composable
fun rememberImage(path: String?): ImageBitmap? = remember(path) { path?.let { ImageCache.get(it) } }

fun imagesDir(context: Context): File = File(context.filesDir, "images").apply { mkdirs() }

// ======================= ARASAAC =======================

/**
 * Pittogrammi ARASAAC (Governo di Aragona, autore Sergio Palao, licenza CC BY-NC-SA).
 * Ricerca in italiano e download del pittogramma scelto.
 */
object Arasaac {
    const val CREDITS = "Autore pittogrammi: Sergio Palao. Origine: ARASAAC (https://arasaac.org). " +
            "Licenza: CC BY-NC-SA. Proprietà: Governo di Aragona (Spagna)."

    fun thumbUrl(id: Int) = "https://static.arasaac.org/pictograms/$id/${id}_300.png"
    private fun fullUrl(id: Int) = "https://static.arasaac.org/pictograms/$id/${id}_500.png"

    /** Restituisce gli id dei pittogrammi (lista vuota se nessun risultato). */
    suspend fun search(query: String): List<Int> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim().lowercase(Locale.ITALIAN), "UTF-8").replace("+", "%20")
        val text = httpText("https://api.arasaac.org/api/pictograms/it/search/$q") ?: return@withContext emptyList()
        runCatching {
            val a = JSONArray(text)
            List(a.length()) { a.getJSONObject(it).getInt("_id") }.take(40)
        }.getOrDefault(emptyList())
    }

    suspend fun thumbnail(id: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        httpBytes(thumbUrl(id))?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }
    }

    /** Scarica il pittogramma nella memoria dell'app e restituisce il percorso. */
    suspend fun download(context: Context, id: Int): String? = withContext(Dispatchers.IO) {
        val bytes = httpBytes(fullUrl(id)) ?: return@withContext null
        val f = File(imagesDir(context), "arasaac_$id.png")
        runCatching { f.writeBytes(bytes); f.absolutePath }.getOrNull()
    }

    private fun httpBytes(url: String): ByteArray? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        try {
            if (c.responseCode != 200) null else c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }.getOrNull()

    private fun httpText(url: String): String? = httpBytes(url)?.toString(Charsets.UTF_8)
}

// ======================= VOCI REGISTRATE =======================

/** Una registrazione per tessera (es. la voce di un familiare). */
class VoiceStore(private val context: Context) {
    private val dir = File(context.filesDir, "voices").apply { mkdirs() }
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    private fun file(id: String) = File(dir, "$id.m4a")
    fun has(id: String) = file(id).exists()

    fun startRecording(id: String): Boolean {
        stopPlayback()
        return try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96000)
            r.setOutputFile(file(id).absolutePath)
            r.prepare()
            r.start()
            recorder = r
            true
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            false
        }
    }

    fun stopRecording() {
        val r = recorder ?: return
        runCatching { r.stop() }
        r.release()
        recorder = null
    }

    fun play(id: String): Boolean {
        if (!has(id)) return false
        stopPlayback()
        return try {
            val p = MediaPlayer()
            p.setDataSource(file(id).absolutePath)
            p.setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
            p.prepare()
            p.start()
            player = p
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stopPlayback() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    fun delete(id: String) { file(id).delete() }
}

// ======================= BACKUP =======================

/** Backup completo (profili, tessere, foto, voci, registri) in un file .zip. */
object Backup {
    private val folders = listOf("files", "shared_prefs")

    fun create(ctx: Context): File {
        val dataDir = File(ctx.applicationInfo.dataDir)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALY).format(Date())
        val out = File(File(ctx.cacheDir, "export").apply { mkdirs() }, "backup_blabla_$stamp.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            for (sub in folders) {
                val root = File(dataDir, sub)
                if (!root.exists()) continue
                root.walkTopDown().filter { it.isFile }.forEach { f ->
                    zip.putNextEntry(ZipEntry(sub + "/" + f.relativeTo(root).path))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return out
    }

    fun restore(ctx: Context, uri: Uri): Int {
        val dataDir = File(ctx.applicationInfo.dataDir).canonicalFile
        var count = 0
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    if (!e.isDirectory && folders.any { name.startsWith("$it/") }) {
                        val target = File(dataDir, name).canonicalFile
                        if (target.path.startsWith(dataDir.path + File.separator)) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            count++
                        }
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        }
        return count
    }
}
