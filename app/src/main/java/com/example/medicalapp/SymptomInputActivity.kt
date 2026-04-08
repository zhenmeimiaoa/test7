package com.example.medicalapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SymptomInputActivity : AppCompatActivity() {
    
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private val VOICE_REQUEST_CODE = 100
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_symptom_input)
        
        LogActivity.addLog("SymptomInputActivity", "onCreate started")
        
        // 再次检查身份验证（双重保险）
        if (!MainActivity.isIdentityVerified) {
            Toast.makeText(this, "请先完成身份验证", Toast.LENGTH_LONG).show()
            LogActivity.addLog("SymptomInputActivity", "Blocked: identity not verified")
            finish()
            return
        }
        
        val tvIdentityInfo = findViewById<TextView>(R.id.tvIdentityInfo)
        val etSymptom = findViewById<EditText>(R.id.etSymptom)
        val btnVoiceInput = findViewById<Button>(R.id.btnVoiceInput)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnLogs = findViewById<Button>(R.id.btnLogs)
        
        // 显示已验证的身份信息
        val info = MainActivity.idCardInfo
        tvIdentityInfo.text = "当前患者：${info?.name ?: "未知"}（已验证）"
        
        btnVoiceInput.setOnClickListener {
            checkPermissionAndStartVoice()
        }
        
        btnSave.setOnClickListener {
            val symptom = etSymptom.text.toString().trim()
            if (symptom.isEmpty()) {
                Toast.makeText(this, "请先输入症状", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            saveSymptom(symptom)
        }
        
        btnBack.setOnClickListener { finish() }
        
        btnLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        
        LogActivity.addLog("SymptomInputActivity", "onCreate completed for: " + (info?.name ?: "unknown"))
    }
    
    private fun saveSymptom(symptom: String) {
        MainActivity.symptomText = symptom
        
        val info = MainActivity.idCardInfo
        val logEntry = """
            【病症信息保存】
            患者：${info?.name ?: "未知"}
            身份证号：${info?.idNumber ?: "未知"}
            症状描述：$symptom
            记录时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())}
            ====================
        """.trimIndent()
        
        LogActivity.addLog("SymptomSave", logEntry)
        
        Toast.makeText(this, "病症信息已保存", Toast.LENGTH_SHORT).show()
        
        // 返回首页
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
    
    private fun checkPermissionAndStartVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.RECORD_AUDIO), 
                RECORD_AUDIO_REQUEST_CODE)
        } else {
            startVoiceRecognition()
        }
    }
    
    private fun startVoiceRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "请描述您的症状...")
            }
            startActivityForResult(intent, VOICE_REQUEST_CODE)
            LogActivity.addLog("SymptomInputActivity", "Voice recognition started")
        } catch (e: Exception) {
            Toast.makeText(this, "语音识别不可用", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                findViewById<EditText>(R.id.etSymptom).setText(results[0])
                LogActivity.addLog("SymptomInputActivity", "Voice recognized: " + results[0])
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE && grantResults.isNotEmpty() 
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
        }
    }
}