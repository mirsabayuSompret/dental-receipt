package com.dentalclinic.receipt

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReceiptItem(val name: String, val diagnosis: String, val price: Long)

object ReceiptPdf {

    fun rupiah(v: Long): String =
        "Rp" + NumberFormat.getIntegerInstance(Locale("id", "ID")).format(v)

    fun save(ctx: Context, patient: String, items: List<ReceiptItem>): Uri {
        val doc = build(ctx, patient, items)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = patient.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "patient" }
        val fileName = "Receipt_${safe}_$stamp.pdf"
        try {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Cannot create file")
                ctx.contentResolver.openOutputStream(uri)!!.use { write(doc, it) }
                uri
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val file = File(dir, fileName)
                file.outputStream().use { write(doc, it) }
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            }
        } finally {
            doc.close()
        }
    }

    private fun write(doc: PdfDocument, out: OutputStream) = doc.writeTo(out)

    private const val W = 595
    private const val H = 420 // landscape A5
    private val NAVY = Color.rgb(31, 58, 110)

    // Uses res/drawable-nodpi/logo.(png|jpg) when present, otherwise draws a vector approximation.
    private fun drawLogo(ctx: Context, c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val id = ctx.resources.getIdentifier("logo", "drawable", ctx.packageName)
        if (id != 0) {
            val bmp = BitmapFactory.decodeResource(ctx.resources, id)
            if (bmp != null) {
                val scale = minOf(w / bmp.width, h / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val dst = RectF(x + (w - dw) / 2, y + (h - dh) / 2, x + (w + dw) / 2, y + (h + dh) / 2)
                c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                return
            }
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val th = h * 0.58f
        val tw = w * 0.36f
        fun tooth(left: Float, top: Float, color: Int) {
            fill.color = color
            c.drawRoundRect(left, top, left + tw, top + th, tw * 0.45f, tw * 0.45f, fill)
            fill.color = Color.rgb(60, 40, 40)
            c.drawCircle(left + tw * 0.3f, top + th * 0.5f, 1.6f, fill)
            c.drawCircle(left + tw * 0.7f, top + th * 0.5f, 1.6f, fill)
            val smile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(60, 40, 40); style = Paint.Style.STROKE; strokeWidth = 1f
            }
            c.drawArc(left + tw * 0.35f, top + th * 0.52f, left + tw * 0.65f, top + th * 0.72f, 0f, 180f, false, smile)
        }
        tooth(x + w * 0.22f, y, Color.rgb(255, 214, 51))
        tooth(x + w * 0.46f, y + h * 0.05f, Color.rgb(255, 122, 160))
        val colors = intArrayOf(
            Color.rgb(0, 168, 232), Color.rgb(255, 122, 160), Color.rgb(255, 193, 7),
            Color.rgb(76, 175, 80), Color.rgb(0, 168, 232), Color.rgb(255, 122, 160)
        )
        val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = h * 0.26f; textAlign = Paint.Align.CENTER
        }
        val step = w / 6f
        "DUDUDU".forEachIndexed { i, ch ->
            letter.color = colors[i]
            c.drawText(ch.toString(), x + step * (i + 0.5f), y + h * 0.85f, letter)
        }
        letter.color = Color.rgb(0, 150, 150); letter.textSize = h * 0.14f
        c.drawText("D E N T A L", x + w / 2, y + h, letter)
    }

    private fun build(ctx: Context, patient: String, items: List<ReceiptItem>): PdfDocument {
        val doc = PdfDocument()
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 20, 40); textSize = 10f }
        val small = Paint(dark).apply { textSize = 8f }
        val bold = Paint(dark).apply { typeface = Typeface.DEFAULT_BOLD }
        val right = Paint(dark).apply { textAlign = Paint.Align.RIGHT }
        val pillText = Paint(bold).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY }
        val line = Paint().apply { color = Color.rgb(20, 20, 40); strokeWidth = 0.8f }
        val titlePaint = Paint(bold).apply { textSize = 14f; color = NAVY }

        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val invNo = "INV/" + SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date())
        val total = items.sumOf { it.price }
        val rowH = 18f
        val firstRowY = 175f
        val pages = items.chunked(6).ifEmpty { listOf(emptyList()) }

        fun drawPill(c: Canvas, cx: Float, cy: Float, w: Float, label: String) {
            c.drawRoundRect(cx - w / 2, cy - 10f, cx + w / 2, cy + 10f, 10f, 10f, pill)
            c.drawText(label, cx, cy + 3.5f, pillText)
        }

        pages.forEachIndexed { pi, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pi + 1).create())
            val c = page.canvas

            drawLogo(ctx, c, 30f, 14f, 80f, 70f)
            c.drawText("Praktek Dokter Gigi", 120f, 44f, titlePaint)
            c.drawText("drg. Qisty Aulia", 120f, 62f, titlePaint)

            c.drawRoundRect(400f, 20f, 575f, 52f, 12f, 12f, pill)
            c.drawText("BUKTI PEMBAYARAN", 487.5f, 40f, pillText)

            c.drawText("D/M/Y", 275f, 70f, small)
            c.drawText(date, 275f, 84f, dark)
            c.drawLine(275f, 88f, 385f, 88f, line)
            c.drawText("Nama:", 275f, 104f, small)
            c.drawText(patient.take(28), 275f, 118f, dark)
            c.drawLine(275f, 122f, 400f, 122f, line)
            c.drawText("No.:", 410f, 70f, small)
            c.drawText(invNo, 410f, 84f, dark)
            c.drawLine(410f, 88f, 575f, 88f, line)
            c.drawText("Dokter:", 410f, 104f, small)
            c.drawText("drg. Qisty Aulia", 410f, 118f, dark)
            c.drawLine(410f, 122f, 575f, 122f, line)

            drawPill(c, 105f, 150f, 130f, "JENIS PERAWATAN")
            drawPill(c, 300f, 150f, 100f, "DIAGNOSA")
            drawPill(c, 520f, 150f, 80f, "HARGA")

            chunk.forEachIndexed { i, it ->
                val y = firstRowY + 10f + i * rowH
                c.drawText(it.name.ifEmpty { "-" }.take(28), 40f, y, dark)
                c.drawText(it.diagnosis.ifEmpty { "-" }.take(38), 215f, y, dark)
                c.drawText(rupiah(it.price), 565f, y, right)
            }

            c.drawText("NEXT VISIT:", 30f, 330f, bold)
            c.drawLine(30f, 336f, 230f, 336f, line)
            c.drawText("Note: *DP Berlaku maksimal 3 bulan", 30f, 399f, small)
            c.drawText("*Uang yang sudah dibayarkan tidak dapat dikembalikan", 52f, 408f, small)

            if (pi == pages.lastIndex) {
                c.drawLine(380f, 300f, 575f, 300f, line)
                c.drawText("TOTAL:", 380f, 315f, bold)
                c.drawText(rupiah(total), 400f, 338f, Paint(bold).apply { textSize = 13f })
                c.drawLine(380f, 345f, 575f, 345f, line)
                c.drawLine(400f, 392f, 560f, 392f, line)
                c.drawText("KASIR (CASHIER)", 400f, 402f, small)
            } else {
                c.drawText("Bersambung...", 575f, 315f, Paint(small).apply { textAlign = Paint.Align.RIGHT })
            }
            doc.finishPage(page)
        }
        return doc
    }
}
