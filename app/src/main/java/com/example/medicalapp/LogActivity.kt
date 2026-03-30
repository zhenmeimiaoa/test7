package com.example.medicalapp

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class LogActivity : AppCompatActivity() {

    companion object {
        val logMessages = mutableListOf<String>()
        
        fun addLog(tag: String, message: String) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logMessages.add("[$time] [$tag] $message")
            if (logMessages.size > 100) {
                logMessages.removeAt(0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val tvLogs: TextView = findViewById(R.id.tvLogs)
        val btnClear: Button = findViewById(R.id.btnClear)
        val btnBack: Button = findViewById(R.id.btnBack)

        refreshLogs(tvLogs)

        btnClear.setOnClickListener {
            logMessages.clear()
            refreshLogs(tvLogs)
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun refreshLogs(tvLogs: TextView) {
        if (logMessages.isEmpty()) {
            tvLogs.text = "No logs yet..."
        } else {
            tvLogs.text = logMessages.joinToString("\n\n")
        }
    }
}
