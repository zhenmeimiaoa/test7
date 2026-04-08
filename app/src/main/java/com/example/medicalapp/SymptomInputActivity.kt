package com.example.medicalapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SymptomInputActivity : AppCompatActivity() {
    
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private val VOICE_REQUEST_CODE = 100
    private var speechRecognizer: SpeechRecognizer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_symptom_input)
        
        LogActivity.addLog("SymptomInputActivity", "onCreate started")
        
        val etSymptom = findViewById<EditText>(R.id.etSymptom)
        val btnVoiceInput = findViewById<Button>(R.id.btnVoiceInput)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnLogs = findViewById<Button>(R.id.btnLogs)
        
        // 检查身份验证是否通过
        if (MainActivity.faceCompareScore <= 60.0) {
            LogActivity.addLog("SymptomInputActivity", "ERROR: Face verification not passed, score=" + MainActivity.faceCompareScore)
            Toast.makeText(this, "请先完成身份验证", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        LogActivity.addLog("SymptomInputActivity", "Identity verified, score=" + MainActivity.faceCompareScore)
        
        // 初始化语音识别
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            if (speechRecognizer == null) {
                LogActivity.addLog("SymptomInputActivity", "WARNING: SpeechRecognizer is null")
            } else {
                LogActivity.addLog("SymptomInputActivity", "SpeechRecognizer initialized")
            }
        } catch (e: Exception) {
            LogActivity.addLog("SymptomInputActivity", "ERROR initializing SpeechRecognizer: " + e.message)
            LogActivity.addLog("SymptomInputActivity", "Stack: " + e.stackTraceToString())
        }
        
        // 语音输入按钮
        btnVoiceInput.setOnClickListener {
            LogActivity.addLog("SymptomInputActivity", "Voice button clicked")
            checkPermissionAndStartVoice()
        }
        
        // 提交症状
        btnSubmit.setOnClickListener {
            val symptom = etSymptom.text.toString().trim()
            LogActivity.addLog("SymptomInputActivity", "Submit clicked, symptom length=" + symptom.length)
            
            if (symptom.isEmpty()) {
                LogActivity.addLog("SymptomInputActivity", "ERROR: Empty symptom")
                Toast.makeText(this, "请先输入症状", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 保存到全局状态
            MainActivity.currentSymptom = symptom
            LogActivity.addLog("SymptomInputActivity", "Symptom saved successfully")
            
            // TODO: 这里后续可以添加保存到数据库/上传云端
            Toast.makeText(this, "症状已记录", Toast.LENGTH_SHORT).show()
            
            // 返回首页或进入下一步
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
        
        btnBack.setOnClickListener { 
            LogActivity.addLog("SymptomInputActivity", "Back clicked")
            finish() 
        }
        
        btnLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        
        LogActivity.addLog("SymptomInputActivity", "onCreate completed successfully")
    }
    
    private fun checkPermissionAndStartVoice() {
        LogActivity.addLog("SymptomInputActivity", "Checking RECORD_AUDIO permission")
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            
            LogActivity.addLog("SymptomInputActivity", "Permission not granted, requesting...")
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.RECORD_AUDIO), 
                RECORD_AUDIO_REQUEST_CODE)
        } else {
            LogActivity.addLog("SymptomInputActivity", "Permission already granted")
            startVoiceRecognition()
        }
    }
    
    private fun startVoiceRecognition() {
        LogActivity.addLog("SymptomInputActivity", "Starting voice recognition...")
        
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "请描述您的症状，如：头痛、发烧、咳嗽...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            
            LogActivity.addLog("SymptomInputActivity", "Intent created: ACTION_RECOGNIZE_SPEECH")
            startActivityForResult(intent, VOICE_REQUEST_CODE)
            LogActivity.addLog("SymptomInputActivity", "startActivityForResult called with requestCode=" + VOICE_REQUEST_CODE)
            
        } catch (e: Exception) {
            LogActivity.addLog("SymptomInputActivity", "ERROR starting voice recognition: " + e.message)
            LogActivity.addLog("SymptomInputActivity", "Exception type: " + e.javaClass.simpleName)
            LogActivity.addLog("SymptomInputActivity", "Stack: " + e.stackTraceToString())
            Toast.makeText(this, "启动语音识别失败: " + e.message, Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        LogActivity.addLog("SymptomInputActivity", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode)
        
        if (requestCode == VOICE_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    LogActivity.addLog("SymptomInputActivity", "Voice recognition RESULT_OK")
                    
                    if (data != null) {
                        val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        val confidence = data.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES)
                        
                        LogActivity.addLog("SymptomInputActivity", "Results count: " + (results?.size ?: 0))
                        
                        if (!results.isNullOrEmpty()) {
                            val spokenText = results[0]
                            val conf = confidence?.get(0) ?: 0f
                            
                            findViewById<EditText>(R.id.etSymptom).setText(spokenText)
                            LogActivity.addLog("SymptomInputActivity", "Voice recognized successfully: '" + spokenText + "'")
                            LogActivity.addLog("SymptomInputActivity", "Confidence: " + conf)
                            Toast.makeText(this, "语音识别成功", Toast.LENGTH_SHORT).show()
                        } else {
                            LogActivity.addLog("SymptomInputActivity", "ERROR: Results list is empty")
                            Toast.makeText(this, "未能识别语音，请重试", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        LogActivity.addLog("SymptomInputActivity", "ERROR: Intent data is null")
                        Toast.makeText(this, "识别数据为空", Toast.LENGTH_SHORT).show()
                    }
                }
                RESULT_CANCELED -> {
                    LogActivity.addLog("SymptomInputActivity", "Voice recognition canceled by user")
                    Toast.makeText(this, "已取消语音识别", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    LogActivity.addLog("SymptomInputActivity", "ERROR: Unknown resultCode=" + resultCode)
                    Toast.makeText(this, "识别失败，错误码: " + resultCode, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        LogActivity.addLog("SymptomInputActivity", "onRequestPermissionsResult: requestCode=" + requestCode)
        
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LogActivity.addLog("SymptomInputActivity", "Permission GRANTED")
                startVoiceRecognition()
            } else {
                LogActivity.addLog("SymptomInputActivity", "Permission DENIED")
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LogActivity.addLog("SymptomInputActivity", "onDestroy")
        speechRecognizer?.destroy()
    }
}