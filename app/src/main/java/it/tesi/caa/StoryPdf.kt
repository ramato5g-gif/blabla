package it.tesi.caa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Esporta una storia in PDF (A4, da stampare o condividere):
 * copertina + una pagina per ogni pagina della storia, con immagine, simboli e testo.
 */
object StoryPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private const val TILE_W = 112f
    private const val TILE_H = 132f
    private const val GAP = 10f

    fun create(ctx: Context, story: Story, board: BoardStore, uppercase: Boolean): File {
        val doc = PdfDocument()
        var pageNo = 0
        val usesArasaac = story.pages.any { p ->
            p.imagePath?.contains("arasaac_") == true ||
                    p.tokens.any { t -> t.startsWith("c:") && board.cards.firstOrNull { it.id == t.substring(2) }?.imagePath?.contains("arasaac_") == true }
        }
        fun up(s: String) = if (uppercase) s.uppercase(Locale.ITALIAN) else s

        val title = Paint().apply { textSize = 34f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val big = Paint().apply { textSize = 130f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val text = Paint().apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; color = 0xFF1B1B1B.toInt() }
        val grey = Paint().apply { textSize = 9f; isAntiAlias = true; color = 0xFF757575.toInt() }
        val label = Paint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; textAlign = Paint.Align.CENTER; color = 0xFF1B1B1B.toInt() }
        val emojiPaint = Paint().apply { textSize = 58f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val border = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = 0x55000000; isAntiAlias = true }
        val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

        fun footer(c: Canvas) {
            c.drawText("${story.title} — pag. $pageNo", M, H - 22f, grey)
            if (usesArasaac) {
                c.drawText("Pittogrammi: Sergio Palao, ARASAAC (arasaac.org), CC BY-NC-SA, Governo di Aragona", M, H - 10f, grey)
            }
        }

        // ---------- Copertina ----------
        pageNo++
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c = page.canvas
        val coverImg = story.pages.firstOrNull { it.imagePath != null }?.imagePath?.let { load(it, 900) }
        if (coverImg != null) drawFit(c, coverImg, RectF(M + 60, 150f, W - M - 60, 520f))
        else c.drawText(story.emoji.ifBlank { "📖" }, W / 2f, 420f, big)
        c.drawText(up(story.title), W / 2f, 600f, title)
        val sub = Paint(grey).apply { textSize = 14f; textAlign = Paint.Align.CENTER }
        c.drawText("Storia in simboli · ${story.pages.size} pagine", W / 2f, 635f, sub)
        c.drawText(SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(Date()), W / 2f, 655f, sub)
        footer(c)
        doc.finishPage(page)

        // ---------- Pagine ----------
        for ((i, pg) in story.pages.withIndex()) {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            var y = M
            val hdr = Paint(grey).apply { textSize = 12f }
            c.drawText("${i + 1} / ${story.pages.size}", M, y + 6f, hdr)
            y += 20f

            pg.imagePath?.let { load(it, 900) }?.let { bmp ->
                val r = drawFit(c, bmp, RectF(M, y, W - M, y + 250f))
                y = r.bottom + 16f
            }

            // Simboli
            val perRow = ((W - 2 * M + GAP) / (TILE_W + GAP)).toInt().coerceAtLeast(1)
            val rows = pg.tokens.chunked(perRow)
            for (row in rows) {
                if (y + TILE_H > H - 150f) { // spazio finito: nuova pagina
                    footer(c); doc.finishPage(page)
                    pageNo++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                    c = page.canvas
                    y = M
                }
                val rowW = row.size * TILE_W + (row.size - 1) * GAP
                var x = (W - rowW) / 2f
                for (t in row) {
                    val rect = RectF(x, y, x + TILE_W, y + TILE_H)
                    val card = if (t.startsWith("c:")) board.cards.firstOrNull { it.id == t.substring(2) } else null
                    fill.color = card?.wordClass?.color?.toArgb() ?: 0xFFFFFFFF.toInt()
                    c.drawRoundRect(rect, 12f, 12f, fill)
                    c.drawRoundRect(rect, 12f, 12f, border)
                    val word = up(tokenWord(t, board))
                    if (card != null) {
                        val img = card.imagePath?.let { load(it, 300) }
                        if (img != null) drawFit(c, img, RectF(x + 10, y + 8, x + TILE_W - 10, y + TILE_H - 30))
                        else c.drawText(card.emoji.ifBlank { "▫" }, x + TILE_W / 2, y + 80f, emojiPaint)
                        c.drawText(ellipsize(word, label, TILE_W - 8), x + TILE_W / 2, y + TILE_H - 10f, label)
                    } else {
                        val wp = Paint(label).apply { textSize = 18f }
                        c.drawText(ellipsize(word, wp, TILE_W - 8), x + TILE_W / 2, y + TILE_H / 2 + 6f, wp)
                    }
                    x += TILE_W + GAP
                }
                y += TILE_H + GAP
            }

            // Testo
            y += 14f
            for (line in wrap(up(pg.text), text, W - 2 * M)) {
                if (y > H - 50f) break
                c.drawText(line, M, y + 22f, text)
                y += 30f
            }
            footer(c)
            doc.finishPage(page)
        }

        val safe = story.title.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "storia" }
        val out = File(File(ctx.cacheDir, "export").apply { mkdirs() }, "storia_$safe.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    /** Carica un'immagine ridotta (lato massimo circa [maxSide] px). */
    private fun load(path: String, maxSide: Int): Bitmap? = runCatching {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, b)
        var s = 1
        while (maxOf(b.outWidth, b.outHeight) / s > maxSide) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()

    /** Disegna l'immagine adattata (senza deformarla) e centrata nel rettangolo. */
    private fun drawFit(c: Canvas, bmp: Bitmap, box: RectF): RectF {
        val scale = minOf(box.width() / bmp.width, box.height() / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val l = box.left + (box.width() - w) / 2
        val dst = RectF(l, box.top, l + w, box.top + h)
        c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return dst
    }

    private fun wrap(s: String, p: Paint, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        var cur = ""
        for (word in s.replace('\n', ' ').split(' ').filter { it.isNotEmpty() }) {
            val test = if (cur.isEmpty()) word else "$cur $word"
            if (p.measureText(test) > maxW && cur.isNotEmpty()) { out += cur; cur = word } else cur = test
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
