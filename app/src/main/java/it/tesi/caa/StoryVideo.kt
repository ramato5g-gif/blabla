package it.tesi.caa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Crea un breve video MP4 (1280×720) dalla storia: copertina + una scena per pagina,
 * con la voce che legge il testo e i simboli che si illuminano mentre vengono letti.
 * Tutto sul dispositivo, senza internet (MediaCodec H.264 + AAC).
 */
object StoryVideo {
    private const val W = 1280
    private const val H = 720
    private const val FPS = 8
    private const val SR = 44100
    private const val LEAD_US = 500_000L   // pausa prima della voce
    private const val TAIL_US = 900_000L   // pausa dopo la voce
    private const val MIN_US = 3_000_000L  // durata minima di una scena

    private class Scene(val cover: Boolean, val text: String, val tokens: List<String>, val imagePath: String?)
    private class Yuv(val y: ByteArray, val u: ByteArray, val v: ByteArray)
    private class Sample(val data: ByteArray, val ptsUs: Long, val flags: Int)

    /**
     * @param synth sintetizza un testo in un file WAV (true se riuscito)
     * @param progress messaggio di avanzamento per l'interfaccia
     */
    suspend fun create(
        ctx: Context,
        story: Story,
        board: BoardStore,
        uppercase: Boolean,
        synth: suspend (String, File) -> Boolean,
        progress: (String) -> Unit,
    ): File = withContext(Dispatchers.Default) {
        val work = File(ctx.cacheDir, "video").apply { deleteRecursively(); mkdirs() }
        val scenes = listOf(Scene(true, story.title, emptyList(), story.pages.firstOrNull { it.imagePath != null }?.imagePath)) +
                story.pages.map { Scene(false, it.text, it.tokens, it.imagePath) }

        // ---------- 1. Voce di ogni scena ----------
        val voices = ArrayList<ShortArray>()
        for ((i, sc) in scenes.withIndex()) {
            progress("Registro la voce ${i + 1}/${scenes.size}…")
            val f = File(work, "voce_$i.wav")
            val ok = sc.text.isNotBlank() && synth(sc.text, f)
            voices += if (ok) readWav(f) else ShortArray(0)
        }
        val voiceUs = voices.map { it.size * 1_000_000L / SR }
        val sceneUs = voiceUs.map { max(LEAD_US + it + TAIL_US, MIN_US) }
        val totalUs = sceneUs.sum()

        // ---------- 2. Traccia audio completa ----------
        val audio = ShortArray((totalUs * SR / 1_000_000L).toInt() + SR)
        var startUs = 0L
        for (i in scenes.indices) {
            val off = ((startUs + LEAD_US) * SR / 1_000_000L).toInt()
            voices[i].copyInto(audio, off, 0, min(voices[i].size, audio.size - off))
            startUs += sceneUs[i]
        }

        // ---------- 3. Video ----------
        val typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.atkinson_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val renderer = Renderer(board, uppercase, typeface, story.title, scenes.size - 1)
        val sceneStartFrame = IntArray(scenes.size + 1)
        for (i in scenes.indices) sceneStartFrame[i + 1] = sceneStartFrame[i] + ceil(sceneUs[i] * FPS / 1_000_000.0).toInt()
        val totalFrames = sceneStartFrame[scenes.size]

        var lastKey = ""
        var lastYuv: Yuv? = null
        val (videoFormat, videoSamples) = encodeVideo(totalFrames) { frame ->
            var s = 0
            while (s < scenes.size - 1 && frame >= sceneStartFrame[s + 1]) s++
            val tUs = (frame - sceneStartFrame[s]) * 1_000_000L / FPS
            val sc = scenes[s]
            val n = sc.tokens.size
            val hl = if (n > 0 && voiceUs[s] > 0 && tUs >= LEAD_US && tUs < LEAD_US + voiceUs[s])
                (((tUs - LEAD_US).toDouble() / voiceUs[s]) * n).toInt().coerceIn(0, n - 1) else -1
            val key = "$s:$hl"
            if (key != lastKey || lastYuv == null) {
                if (frame % 4 == 0 || key != lastKey) progress("Creo il video… ${frame * 100 / max(1, totalFrames)}%")
                lastYuv = toYuv(renderer.render(sc, s, hl))
                lastKey = key
            }
            lastYuv!!
        }

        // ---------- 4. Audio AAC ----------
        progress("Codifico l'audio…")
        val (audioFormat, audioSamples) = encodeAudio(audio)

        // ---------- 5. Unione in MP4 ----------
        progress("Salvo il video…")
        val safe = story.title.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "storia" }
        val out = File(File(ctx.cacheDir, "export").apply { mkdirs() }, "storia_$safe.mp4")
        out.delete()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vt = muxer.addTrack(videoFormat)
        val at = muxer.addTrack(audioFormat)
        muxer.start()
        val info = MediaCodec.BufferInfo()
        var vi = 0
        var ai = 0
        while (vi < videoSamples.size || ai < audioSamples.size) {
            val useVideo = ai >= audioSamples.size || (vi < videoSamples.size && videoSamples[vi].ptsUs <= audioSamples[ai].ptsUs)
            val smp = if (useVideo) videoSamples[vi++] else audioSamples[ai++]
            info.set(0, smp.data.size, smp.ptsUs, smp.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
            muxer.writeSampleData(if (useVideo) vt else at, ByteBuffer.wrap(smp.data), info)
        }
        muxer.stop()
        muxer.release()
        work.deleteRecursively()
        out
    }

    // ======================= DISEGNO DELLE SCENE =======================

    private class Renderer(
        val board: BoardStore,
        val uppercase: Boolean,
        tf: Typeface,
        val title: String,
        val pages: Int,
    ) {
        val bmp: Bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { color = 0xFFFFF8E1.toInt() }
        val band = Paint().apply { color = 0xFFFFE0B2.toInt() }
        val titleP = Paint().apply { typeface = tf; textSize = 34f; color = 0xFF1B1B1B.toInt(); isAntiAlias = true }
        val pageP = Paint().apply { typeface = tf; textSize = 28f; color = 0xFF757575.toInt(); isAntiAlias = true; textAlign = Paint.Align.RIGHT }
        val coverTitle = Paint().apply { typeface = tf; textSize = 72f; color = 0xFF1B1B1B.toInt(); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val coverEmoji = Paint().apply { textSize = 220f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val textP = Paint().apply { typeface = tf; textSize = 46f; color = 0xFF1B1B1B.toInt(); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val label = Paint().apply { typeface = tf; textSize = 22f; color = 0xFF1B1B1B.toInt(); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val wordP = Paint().apply { typeface = tf; textSize = 30f; color = 0xFF1B1B1B.toInt(); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val emojiP = Paint().apply { textSize = 80f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val fill = Paint().apply { isAntiAlias = true }
        val border = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x55000000; isAntiAlias = true }
        val glow = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 9f; color = 0xFFFF6F00.toInt(); isAntiAlias = true }
        private val cache = HashMap<String, Bitmap?>()

        fun up(s: String) = if (uppercase) s.uppercase(Locale.ITALIAN) else s

        fun image(path: String?, maxSide: Int): Bitmap? {
            if (path == null) return null
            return cache.getOrPut("$path@$maxSide") {
                runCatching {
                    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, b)
                    var s = 1
                    while (max(b.outWidth, b.outHeight) / s > maxSide) s *= 2
                    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
                }.getOrNull()
            }
        }

        fun fit(b: Bitmap, box: RectF) {
            val sc = min(box.width() / b.width, box.height() / b.height)
            val w = b.width * sc
            val h = b.height * sc
            val l = box.left + (box.width() - w) / 2
            val t = box.top + (box.height() - h) / 2
            c.drawBitmap(b, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        fun render(sc: Scene, index: Int, highlight: Int): Bitmap {
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
            if (sc.cover) {
                val img = image(sc.imagePath, 900)
                if (img != null) fit(img, RectF(240f, 60f, W - 240f, 470f))
                else c.drawText("📖", W / 2f, 400f, coverEmoji)
                c.drawText(up(title), W / 2f, 580f, coverTitle)
                return bmp
            }
            c.drawRect(0f, 0f, W.toFloat(), 64f, band)
            c.drawText(up(title), 30f, 44f, titleP)
            c.drawText("$index / $pages", W - 30f, 44f, pageP)

            // Testo in basso
            val lines = wrap(up(sc.text), textP, W - 120f).take(2)
            val textTop = H - 30f - lines.size * 56f
            lines.forEachIndexed { i, l -> c.drawText(l, W / 2f, textTop + 46f + i * 56f, textP) }

            // Immagine a sinistra (se c'è) e simboli
            val area = RectF(30f, 84f, W - 30f, textTop - 16f)
            val img = image(sc.imagePath, 900)
            val symArea = if (img != null) {
                fit(img, RectF(area.left, area.top, area.left + area.width() * 0.45f, area.bottom))
                RectF(area.left + area.width() * 0.47f, area.top, area.right, area.bottom)
            } else area
            drawTokens(sc.tokens, symArea, highlight)
            return bmp
        }

        private fun drawTokens(tokens: List<String>, box: RectF, highlight: Int) {
            if (tokens.isEmpty()) return
            val gap = 14f
            // Dimensione della tessera: la più grande che fa entrare tutti i simboli
            var tw = 190f
            var perRow = 1
            var rows = 1
            while (true) {
                val th = tw * 1.17f
                perRow = max(1, ((box.width() + gap) / (tw + gap)).toInt())
                rows = ceil(tokens.size / perRow.toDouble()).toInt()
                if (rows * (th + gap) - gap <= box.height() || tw <= 90f) break
                tw -= 10f
            }
            val th = tw * 1.17f
            val totalH = rows * (th + gap) - gap
            var y = box.top + max(0f, (box.height() - totalH) / 2)
            tokens.chunked(perRow).forEachIndexed { r, row ->
                val rowW = row.size * tw + (row.size - 1) * gap
                var x = box.left + (box.width() - rowW) / 2
                row.forEachIndexed { k, t ->
                    val idx = r * perRow + k
                    val rect = RectF(x, y, x + tw, y + th)
                    val card = if (t.startsWith("c:")) board.cards.firstOrNull { it.id == t.substring(2) } else null
                    fill.color = card?.wordClass?.color?.toArgb() ?: 0xFFFFFFFF.toInt()
                    c.drawRoundRect(rect, 16f, 16f, fill)
                    c.drawRoundRect(rect, 16f, 16f, if (idx == highlight) glow else border)
                    val word = up(tokenWord(t, board))
                    val lp = Paint(label).apply { textSize = (tw * 0.13f).coerceIn(16f, 28f) }
                    if (card != null && (card.imagePath != null || card.emoji.isNotBlank())) {
                        val im = image(card.imagePath, 400)
                        if (im != null) fit(im, RectF(x + 12, y + 10, x + tw - 12, y + th - 40))
                        else {
                            val ep = Paint(emojiP).apply { textSize = tw * 0.45f }
                            c.drawText(card.emoji, x + tw / 2, y + th * 0.62f, ep)
                        }
                        c.drawText(ellipsize(word, lp, tw - 10), x + tw / 2, y + th - 14f, lp)
                    } else {
                        val wp = Paint(wordP).apply { textSize = (tw * 0.18f).coerceIn(18f, 40f) }
                        c.drawText(ellipsize(word, wp, tw - 10), x + tw / 2, y + th / 2 + 10f, wp)
                    }
                    x += tw + gap
                }
                y += th + gap
            }
        }

        private fun wrap(s: String, p: Paint, maxW: Float): List<String> {
            val out = mutableListOf<String>()
            var cur = ""
            for (w in s.replace('\n', ' ').split(' ').filter { it.isNotEmpty() }) {
                val test = if (cur.isEmpty()) w else "$cur $w"
                if (p.measureText(test) > maxW && cur.isNotEmpty()) { out += cur; cur = w } else cur = test
            }
            if (cur.isNotEmpty()) out += cur
            return out
        }

        private fun ellipsize(s: String, p: Paint, maxW: Float): String {
            if (p.measureText(s) <= maxW) return s
            var t = s
            while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
            return "$t…"
        }
    }

    // ======================= CONVERSIONE E CODIFICA =======================

    private fun toYuv(b: Bitmap): Yuv {
        val px = IntArray(W * H)
        b.getPixels(px, 0, W, 0, 0, W, H)
        val y = ByteArray(W * H)
        val u = ByteArray(W * H / 4)
        val v = ByteArray(W * H / 4)
        for (j in 0 until H) {
            val row = j * W
            for (i in 0 until W) {
                val c = px[row + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val bl = c and 0xFF
                y[row + i] = (((66 * r + 129 * g + 25 * bl + 128) shr 8) + 16).toByte()
                if ((j and 1) == 0 && (i and 1) == 0) {
                    val k = (j shr 1) * (W shr 1) + (i shr 1)
                    u[k] = (((-38 * r - 74 * g + 112 * bl + 128) shr 8) + 128).toByte()
                    v[k] = (((112 * r - 94 * g - 18 * bl + 128) shr 8) + 128).toByte()
                }
            }
        }
        return Yuv(y, u, v)
    }

    private fun fillImage(img: Image, yuv: Yuv) {
        val planes = img.planes
        val yp = planes[0]
        val yb = yp.buffer
        for (r in 0 until H) {
            yb.position(r * yp.rowStride)
            yb.put(yuv.y, r * W, W)
        }
        val cw = W / 2
        for ((plane, src) in listOf(planes[1] to yuv.u, planes[2] to yuv.v)) {
            val b = plane.buffer
            val rs = plane.rowStride
            val ps = plane.pixelStride
            for (r in 0 until H / 2) {
                if (ps == 1) {
                    b.position(r * rs)
                    b.put(src, r * cw, cw)
                } else {
                    val base = r * rs
                    for (c in 0 until cw) b.put(base + c * ps, src[r * cw + c])
                }
            }
        }
    }

    private fun encodeVideo(totalFrames: Int, frameAt: (Int) -> Yuv): Pair<MediaFormat, List<Sample>> {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val samples = ArrayList<Sample>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var frame = 0
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val pts = frame * 1_000_000L / FPS
                        if (frame >= totalFrames) {
                            codec.queueInputBuffer(idx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val img = codec.getInputImage(idx) ?: error("Il codificatore video del dispositivo non è compatibile")
                            fillImage(img, frameAt(frame))
                            codec.queueInputBuffer(idx, 0, W * H * 3 / 2, pts, 0)
                            frame++
                        }
                    }
                }
                outputDone = drain(codec, info, samples) { outFormat = it }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return (outFormat ?: error("Formato video non disponibile")) to samples
    }

    private fun encodeAudio(pcm: ShortArray): Pair<MediaFormat, List<Sample>> {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SR, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val samples = ArrayList<Sample>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var pos = 0
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        buf.clear()
                        val pts = pos * 1_000_000L / SR
                        val n = min(buf.capacity() / 2, pcm.size - pos)
                        if (n <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val bb = buf.order(ByteOrder.LITTLE_ENDIAN)
                            for (k in 0 until n) bb.putShort(pcm[pos + k])
                            codec.queueInputBuffer(idx, 0, n * 2, pts, 0)
                            pos += n
                        }
                    }
                }
                outputDone = drain(codec, info, samples) { outFormat = it }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return (outFormat ?: error("Formato audio non disponibile")) to samples
    }

    /** Raccoglie l'output del codificatore; true quando è arrivata la fine dello stream. */
    private fun drain(codec: MediaCodec, info: MediaCodec.BufferInfo, out: MutableList<Sample>, onFormat: (MediaFormat) -> Unit): Boolean {
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(codec.outputFormat)
                idx >= 0 -> {
                    val buf = codec.getOutputBuffer(idx)
                    if (buf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val data = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.get(data)
                        out += Sample(data, info.presentationTimeUs, info.flags)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true
                }
            }
        }
    }

    /** Legge un WAV PCM 16 bit, lo porta a mono 44,1 kHz. */
    private fun readWav(f: File): ShortArray {
        val bytes = runCatching { f.readBytes() }.getOrNull() ?: return ShortArray(0)
        if (bytes.size < 44) return ShortArray(0)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var channels = 1
        var rate = 22050
        var bits = 16
        var dataOff = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val len = bb.getInt(pos + 4)
            if (id == "fmt ") {
                channels = bb.getShort(pos + 10).toInt().coerceAtLeast(1)
                rate = bb.getInt(pos + 12)
                bits = bb.getShort(pos + 22).toInt()
            } else if (id == "data") {
                dataOff = pos + 8
                dataLen = if (len <= 0 || dataOff + len > bytes.size) bytes.size - dataOff else len
                break
            }
            if (len < 0) break
            pos += 8 + len + (len and 1)
        }
        if (dataOff < 0 || bits != 16 || rate <= 0) return ShortArray(0)
        val frames = dataLen / (2 * channels)
        val mono = ShortArray(frames) { i ->
            var s = 0
            for (ch in 0 until channels) s += bb.getShort(dataOff + (i * channels + ch) * 2)
            (s / channels).toShort()
        }
        if (rate == SR) return mono
        // Ricampionamento lineare a 44,1 kHz
        val outLen = (frames.toLong() * SR / rate).toInt()
        return ShortArray(outLen) { i ->
            val srcPos = i.toDouble() * rate / SR
            val a = srcPos.toInt().coerceAtMost(frames - 1)
            val b = (a + 1).coerceAtMost(frames - 1)
            val fr = srcPos - a
            (mono[a] * (1 - fr) + mono[b] * fr).toInt().toShort()
        }
    }
}
