package com.example.medicalapp

import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class IdentityMethodActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identity_method)
        
        LogActivity.addLog("IdentityMethodActivity", "onCreate")
        
        findViewById<Button>(R.id.btnOcr).setOnClickListener {
            LogActivity.addLog("IdentityMethodActivity", "OCR selected")
            startActivity(Intent(this, OcrActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnNfc).setOnClickListener {
            LogActivity.addLog("IdentityMethodActivity", "NFC selected")
            // TODO: NFC功能待实现
            Toast.makeText(this, "NFC功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        findViewById<Button>(R.id.btnLogs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }
}
