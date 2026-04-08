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
        
        val etSymptom = findViewById<EditText>(R.id.etSymptom)
        val btnVoiceInput = findViewById<Button>(R.id.btnVoiceInput)
        val btnNext = findViewById<Button>(R.id.btnNext)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnLogs = findViewById<Button>(R.id.btnLogs)
        
        // 语音输入按钮
        btnVoiceInput.setOnClickListener {
            checkPermissionAndStartVoice()
        }
        
        // 下一步：保存症状并进入身份验证
        btnNext.setOnClickListener {
            val symptom = etSymptom.text.toString().trim()
            if (symptom.isEmpty()) {
                Toast.makeText(this, "请先输入症状", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 保存到全局状态
            MainActivity.currentSymptom = symptom
            LogActivity.addLog("SymptomInputActivity", "Symptom saved: $symptom")
            
            // 进入身份验证流程
            startActivity(Intent(this, ManualInputActivity::class.java))
        }
        
        btnBack.setOnClickListener { finish() }
        
        btnLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        
        LogActivity.addLog("SymptomInputActivity", "onCreate completed")
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
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请描述您的症状...")
        }
        
        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE)
            LogActivity.addLog("SymptomInputActivity", "Voice recognition started")
        } catch (e: Exception) {
            Toast.makeText(this, "您的设备不支持语音识别", Toast.LENGTH_SHORT).show()
            LogActivity.addLog("SymptomInputActivity", "Voice recognition not supported")
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                findViewById<EditText>(R.id.etSymptom).setText(spokenText)
                LogActivity.addLog("SymptomInputActivity", "Voice recognized: $spokenText")
                Toast.makeText(this, "语音识别成功", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE && grantResults.isNotEmpty() 
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
        } else {
            Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
        }
    }
}