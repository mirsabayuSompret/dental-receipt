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
    private const val W = 595 // A4 at 72dpi
    private const val H = 842
    private const val M = 40f

    fun rupiah(v: Long): String =
        "Rp" + NumberFormat.getIntegerInstance(Locale("id", "ID")).format(v)

    fun save(ctx: Context, clinic: String, patient: String, items: List<ReceiptItem>): Uri {
        val doc = build(clinic, patient, items)
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

    private fun build(clinic: String, patient: String, items: List<ReceiptItem>): PdfDocument {
        val doc = PdfDocument()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val right = Paint(text).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(bold).apply { textAlign = Paint.Align.RIGHT }
        val line = Paint().apply { color = Color.GRAY; strokeWidth = 1f }
        val priceX = W - M

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c: Canvas = page.canvas

        fun header(): Float {
            var y = 60f
            c.drawText(clinic, W / 2f, y, Paint(bold).apply { textSize = 20f; textAlign = Paint.Align.CENTER })
            y += 22f
            c.drawText("RECEIPT", W / 2f, y, Paint(text).apply { textAlign = Paint.Align.CENTER })
            y += 36f
            val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            c.drawText("Patient: $patient", M, y, text)
            c.drawText("Date: $date", priceX, y, right)
            y += 14f
            c.drawLine(M, y, priceX, y, line)
            y += 20f
            c.drawText("No", M, y, bold)
            c.drawText("Procedure", M + 30f, y, bold)
            c.drawText("Price", priceX, y, rightBold)
            y += 8f
            c.drawLine(M, y, priceX, y, line)
            return y + 20f
        }

        var y = header()
        items.forEachIndexed { i, it ->
            if (y > H - 120f) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
                c = page.canvas
                y = header()
            }
            c.drawText("${i + 1}", M, y, text)
            c.drawText(it.name.ifEmpty { "-" }.take(55), M + 30f, y, text)
            c.drawText(rupiah(it.price), priceX, y, right)
            y += 20f
        }
        y += -6f
        c.drawLine(M, y, priceX, y, line)
        y += 24f
        c.drawText("TOTAL", M + 30f, y, bold)
        c.drawText(rupiah(items.sumOf { it.price }), priceX, y, rightBold)
        y += 60f
        c.drawText("Thank you.", W / 2f, y, Paint(text).apply { textAlign = Paint.Align.CENTER })

        doc.finishPage(page)
        return doc
    }
}
