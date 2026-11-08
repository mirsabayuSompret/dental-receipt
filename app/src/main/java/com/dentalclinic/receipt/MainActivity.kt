package com.dentalclinic.receipt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private lateinit var tvTotal: TextView
    private lateinit var etPatient: EditText
    private var pendingPrint = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        list = findViewById(R.id.listProcedures)
        tvTotal = findViewById(R.id.tvTotal)
        etPatient = findViewById(R.id.etPatient)

        findViewById<Button>(R.id.btnAdd).setOnClickListener { addRow() }
        findViewById<Button>(R.id.btnPrint).setOnClickListener { print() }
        addRow()
    }

    private fun addRow() {
        val row = LayoutInflater.from(this).inflate(R.layout.item_procedure, list, false)
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateTotal()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        row.findViewById<EditText>(R.id.etPrice).addTextChangedListener(watcher)
        row.findViewById<Button>(R.id.btnRemove).setOnClickListener {
            if (list.childCount > 1) {
                list.removeView(row)
            } else {
                row.findViewById<EditText>(R.id.etProcedure).text.clear()
                row.findViewById<EditText>(R.id.etDiagnosis).text.clear()
                row.findViewById<EditText>(R.id.etPrice).text.clear()
            }
            updateTotal()
        }
        list.addView(row)
        updateTotal()
    }

    private fun items(): List<ReceiptItem> =
        (0 until list.childCount).map { list.getChildAt(it) }.map { row ->
            ReceiptItem(
                row.findViewById<EditText>(R.id.etProcedure).text.toString().trim(),
                row.findViewById<EditText>(R.id.etDiagnosis).text.toString().trim(),
                row.findViewById<EditText>(R.id.etPrice).text.toString().toLongOrNull() ?: 0L
            )
        }

    private fun updateTotal() {
        tvTotal.text = "Total: " + ReceiptPdf.rupiah(items().sumOf { it.price })
    }

    private fun print() {
        val patient = etPatient.text.toString().trim()
        val filled = items().filter { it.name.isNotEmpty() || it.diagnosis.isNotEmpty() || it.price > 0 }
        if (patient.isEmpty()) {
            etPatient.error = "Enter patient name"
            return
        }
        if (filled.isEmpty()) {
            Toast.makeText(this, "Add at least one procedure", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPrint = true
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1
            )
            return
        }
        try {
            val uri = ReceiptPdf.save(this, patient, filled)
            Toast.makeText(this, "Saved to Downloads", Toast.LENGTH_LONG).show()
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "Open receipt"
                )
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (pendingPrint && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingPrint = false
            print()
        }
    }
}
