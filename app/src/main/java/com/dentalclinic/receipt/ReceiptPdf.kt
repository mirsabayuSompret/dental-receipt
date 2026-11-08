package com.dentalclinic.receipt

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
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

data class ReceiptItem(val name: String, val price: Long)

object ReceiptPdf {

    fun rupiah(v: Long): String =
        "Rp" + NumberFormat.getIntegerInstance(Locale("id", "ID")).format(v)

    fun save(ctx: Context, patient: String, items: List<ReceiptItem>): Uri {
        val doc = build(patient, items)
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

    private fun build(patient: String, items: List<ReceiptItem>): PdfDocument {
        val doc = PdfDocument()
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 20, 40); textSize = 10f }
        val small = Paint(dark).apply { textSize = 8f }
        val bold = Paint(dark).apply { typeface = Typeface.DEFAULT_BOLD }
        val center = Paint(dark).apply { textAlign = Paint.Align.CENTER }
        val pillText = Paint(bold).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY }
        val line = Paint().apply { color = Color.rgb(20, 20, 40); strokeWidth = 0.8f }
        val grey = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(225, 228, 235) }
        val titlePaint = Paint(bold).apply { textSize = 15f; color = NAVY }

        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val invNo = "INV/" + SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date())
        val total = items.sumOf { it.price }
        val rowH = 16f
        val firstRowY = 168f
        val perPage = 7
        val pages = items.chunked(perPage).ifEmpty { listOf(emptyList()) }

        val colBiaya = 300f; val colUnit = 365f; val colDisc = 425f; val colJumlah = 520f

        fun drawPill(c: Canvas, cx: Float, cy: Float, w: Float, label: String, tp: Paint = pillText) {
            c.drawRoundRect(cx - w / 2, cy - 10f, cx + w / 2, cy + 10f, 10f, 10f, pill)
            c.drawText(label, cx, cy + 3.5f, tp)
        }

        pages.forEachIndexed { pi, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pi + 1).create())
            val c = page.canvas

            // Title (wrapped on two lines)
            c.drawText("Praktek Dokter Gigi", 30f, 55f, titlePaint)
            c.drawText("drg. Qisty Aulia", 30f, 73f, titlePaint)
            c.drawLine(30f, 82f, 210f, 82f, line)

            // Receipt title tag
            c.drawRoundRect(400f, 20f, 575f, 52f, 12f, 12f, pill)
            c.drawText("BUKTI PEMBAYARAN", 487.5f, 40f, pillText)

            // Header fields
            c.drawText("D/M/Y", 240f, 70f, small)
            c.drawText(date, 240f, 84f, dark)
            c.drawLine(240f, 88f, 370f, 88f, line)
            c.drawText("Nama:", 240f, 104f, small)
            c.drawText(patient.take(30), 240f, 118f, dark)
            c.drawLine(240f, 122f, 400f, 122f, line)
            c.drawText("No.:", 410f, 70f, small)
            c.drawText(invNo, 410f, 84f, dark)
            c.drawLine(410f, 88f, 575f, 88f, line)
            c.drawText("Dokter:", 410f, 104f, small)
            c.drawText("drg. Qisty Aulia", 410f, 118f, dark)
            c.drawLine(410f, 122f, 575f, 122f, line)

            // Column headers
            drawPill(c, 100f, 148f, 110f, "TINDAKAN / OBAT")
            drawPill(c, colBiaya, 148f, 56f, "BIAYA")
            drawPill(c, colUnit, 148f, 44f, "UNIT")
            drawPill(c, colDisc, 148f, 64f, "DISCOUNT")
            drawPill(c, colJumlah, 148f, 62f, "JUMLAH")

            // Rows
            chunk.forEachIndexed { i, it ->
                val y = firstRowY + 10f + i * rowH
                c.drawText(it.name.ifEmpty { "-" }.take(34), 36f, y, dark)
                c.drawText(rupiah(it.price), colBiaya, y, center)
                c.drawText("1", colUnit, y, center)
                c.drawText("Rp0", colDisc, y, center)
                c.drawText(rupiah(it.price), colJumlah, y, center)
            }

            // Next visit, diagnosis, notes
            c.drawText("NEXT VISIT:", 30f, 300f, bold)
            c.drawLine(30f, 306f, 230f, 306f, line)
            c.drawRoundRect(30f, 316f, 330f, 385f, 10f, 10f, grey)
            c.drawText("DIAGNOSIS", 40f, 332f, bold)
            c.drawText("Note: *DP Berlaku maksimal 3 bulan", 30f, 399f, small)
            c.drawText("*Uang yang sudah dibayarkan tidak dapat dikembalikan", 52f, 408f, small)

            if (pi == pages.lastIndex) {
                c.drawLine(380f, 285f, 575f, 285f, line)
                c.drawText("TOTAL:", 380f, 300f, bold)
                c.drawText(rupiah(total), 400f, 325f, Paint(bold).apply { textSize = 13f })
                c.drawLine(380f, 332f, 575f, 332f, line)
                c.drawLine(400f, 380f, 560f, 380f, line)
                c.drawText("KASIR (CASHIER)", 400f, 390f, small)
            } else {
                c.drawText("Bersambung...", 575f, 300f, Paint(small).apply { textAlign = Paint.Align.RIGHT })
            }
            doc.finishPage(page)
        }
        return doc
    }
}
