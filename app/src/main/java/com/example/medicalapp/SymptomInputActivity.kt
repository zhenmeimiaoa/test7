package com.example.medicalapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.baidu.speech.EventListener
import com.baidu.speech.EventManager
import com.baidu.speech.EventManagerFactory
import com.baidu.speech.asr.SpeechConstant
import org.json.JSONObject

class SymptomInputActivity : AppCompatActivity(), EventListener {
    
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private var asr: EventManager? = null
    private var isRecording = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_symptom_input)
        
        LogActivity.addLog("SymptomInputActivity", "onCreate started")
        
        // 检查身份验证
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
        
        // 初始化百度语音识别
        initBaiduASR()
        
        btnVoiceInput.setOnClickListener {
            if (isRecording) {
                stopVoiceRecognition()
            } else {
                checkPermissionAndStartVoice()
            }
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
    
    private fun initBaiduASR() {
        try {
            asr = EventManagerFactory.create(this, "asr")
            asr?.registerListener(this)
            LogActivity.addLog("SymptomInputActivity", "Baidu ASR initialized")
        } catch (e: Exception) {
            LogActivity.addLog("SymptomInputActivity", "Baidu ASR init failed: " + e.message)
            Toast.makeText(this, "语音初始化失败: " + e.message, Toast.LENGTH_SHORT).show()
        }
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
            val params = LinkedHashMap<String, Any>()
            params[SpeechConstant.APP_ID] = BaiduSpeechConfig.APP_ID
            params[SpeechConstant.API_KEY] = BaiduSpeechConfig.API_KEY
            params[SpeechConstant.SECRET_KEY] = BaiduSpeechConfig.SECRET_KEY
            
            // 识别参数
            params[SpeechConstant.DECODER] = 0 // 纯在线识别
            params[SpeechConstant.VAD] = SpeechConstant.VAD_DNN
            params[SpeechConstant.PID] = 1537 // 普通话输入法模型
            params[SpeechConstant.ACCEPT_AUDIO_VOLUME] = false
            
            val json = JSONObject(params as Map<*, *>).toString()
            
            asr?.send(SpeechConstant.ASR_START, json, null, 0, 0)
            isRecording = true
            
            findViewById<Button>(R.id.btnVoiceInput).text = "🎙️ 停止录音"
            findViewById<Button>(R.id.btnVoiceInput).backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
            
            Toast.makeText(this, "请说话...", Toast.LENGTH_SHORT).show()
            LogActivity.addLog("SymptomInputActivity", "Baidu ASR started")
            
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: " + e.message, Toast.LENGTH_SHORT).show()
            LogActivity.addLog("SymptomInputActivity", "Baidu ASR error: " + e.message)
        }
    }
    
    private fun stopVoiceRecognition() {
        asr?.send(SpeechConstant.ASR_STOP, null, null, 0, 0)
        isRecording = false
        
        findViewById<Button>(R.id.btnVoiceInput).text = "🎤 语音输入"
        findViewById<Button>(R.id.btnVoiceInput).backgroundTintList = getColorStateList(android.R.color.holo_orange_dark)
        
        LogActivity.addLog("SymptomInputActivity", "Baidu ASR stopped")
    }
    
    override fun onEvent(name: String?, params: String?, data: ByteArray?, offset: Int, length: Int) {
        when (name) {
            SpeechConstant.CALLBACK_EVENT_ASR_READY -> {
                LogActivity.addLog("SymptomInputActivity", "ASR Ready")
            }
            SpeechConstant.CALLBACK_EVENT_ASR_BEGIN -> {
                runOnUiThread {
                    Toast.makeText(this, "开始识别...", Toast.LENGTH_SHORT).show()
                }
            }
            SpeechConstant.CALLBACK_EVENT_ASR_END -> {
                LogActivity.addLog("SymptomInputActivity", "ASR End")
            }
            SpeechConstant.CALLBACK_EVENT_ASR_PARTIAL -> {
                // 临时结果
                try {
                    val json = JSONObject(params ?: "{}")
                    val results = json.optJSONArray("results_recognition")
                    if (results != null && results.length() > 0) {
                        val text = results.getString(0)
                        runOnUiThread {
                            findViewById<EditText>(R.id.etSymptom).setText(text)
                        }
                    }
                } catch (e: Exception) {
                    LogActivity.addLog("SymptomInputActivity", "Partial result error: " + e.message)
                }
            }
            SpeechConstant.CALLBACK_EVENT_ASR_FINISH -> {
                // 最终结果
                try {
                    val json = JSONObject(params ?: "{}")
                    val results = json.optJSONArray("results_recognition")
                    if (results != null && results.length() > 0) {
                        val text = results.getString(0)
                        runOnUiThread {
                            findViewById<EditText>(R.id.etSymptom).setText(text)
                            findViewById<Button>(R.id.btnVoiceInput).text = "🎤 语音输入"
                            findViewById<Button>(R.id.btnVoiceInput).backgroundTintList = getColorStateList(android.R.color.holo_orange_dark)
                            Toast.makeText(this, "识别完成", Toast.LENGTH_SHORT).show()
                            LogActivity.addLog("SymptomInputActivity", "ASR result: $text")
                        }
                    }
                    isRecording = false
                } catch (e: Exception) {
                    LogActivity.addLog("SymptomInputActivity", "Finish result error: " + e.message)
                }
            }
            SpeechConstant.CALLBACK_EVENT_ASR_ERROR -> {
                runOnUiThread {
                    isRecording = false
                    findViewById<Button>(R.id.btnVoiceInput).text = "🎤 语音输入"
                    findViewById<Button>(R.id.btnVoiceInput).backgroundTintList = getColorStateList(android.R.color.holo_orange_dark)
                    Toast.makeText(this, "识别出错，请重试", Toast.LENGTH_SHORT).show()
                    LogActivity.addLog("SymptomInputActivity", "ASR error: $params")
                }
            }
        }
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
        
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE && grantResults.isNotEmpty() 
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            asr?.send(SpeechConstant.ASR_STOP, null, null, 0, 0)
        }
        asr?.unregisterListener(this)
    }
}
