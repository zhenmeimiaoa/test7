package com.example.medicalapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class IdentityMethodActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_identity_method)
            LogActivity.addLog("IdentityMethodActivity", "setContentView success")
        } catch (e: Exception) {
            LogActivity.addLog("IdentityMethodActivity", "setContentView ERROR: " + e.message)
            Toast.makeText(this, "布局加载失败: " + e.message, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        try {
            findViewById<Button>(R.id.btnOcr).setOnClickListener {
                LogActivity.addLog("IdentityMethodActivity", "OCR clicked")
                startActivity(Intent(this, OcrActivity::class.java))
            }
            
            findViewById<Button>(R.id.btnNfc).setOnClickListener {
                LogActivity.addLog("IdentityMethodActivity", "NFC clicked")
                Toast.makeText(this, "NFC功能开发中", Toast.LENGTH_SHORT).show()
            }
            
            findViewById<Button>(R.id.btnBack).setOnClickListener {
                finish()
            }
            
            findViewById<Button>(R.id.btnLogs).setOnClickListener {
                startActivity(Intent(this, LogActivity::class.java))
            }
            
            LogActivity.addLog("IdentityMethodActivity", "onCreate completed")
        } catch (e: Exception) {
            LogActivity.addLog("IdentityMethodActivity", "init ERROR: " + e.message)
            Toast.makeText(this, "初始化失败: " + e.message, Toast.LENGTH_LONG).show()
        }
    }
}
