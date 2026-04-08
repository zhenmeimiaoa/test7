package com.example.medicalapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class SymptomInputActivity : AppCompatActivity() {
    
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    
    // 延迟初始化视图组件
    private lateinit var btnVoiceInput: Button
    
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
        
        // 初始化视图
        val tvIdentityInfo = findViewById<TextView>(R.id.tvIdentityInfo)
        val etSymptom = findViewById<EditText>(R.id.etSymptom)
        btnVoiceInput = findViewById<Button>(R.id.btnVoiceInput)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnLogs = findViewById<Button>(R.id.btnLogs)
        
        // 显示已验证的身份信息
        val info = MainActivity.idCardInfo
        tvIdentityInfo.text = "当前患者：${info?.name ?: "未知"}（已验证）"
        
        btnVoiceInput.setOnClickListener {
            if (isRecording) {
                stopRecordingAndRecognize()
            } else {
                checkPermissionAndStartRecording()
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
    
    private fun checkPermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.RECORD_AUDIO), 
                RECORD_AUDIO_REQUEST_CODE)
        } else {
            startRecording()
        }
    }
    
    private fun startRecording() {
        try {
            audioFile = File(externalCacheDir, "voice_${System.currentTimeMillis()}.wav")
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            
            isRecording = true
            btnVoiceInput.text = "🎙️ 停止录音"
            btnVoiceInput.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
            
            Toast.makeText(this, "请说话...", Toast.LENGTH_SHORT).show()
            LogActivity.addLog("SymptomInputActivity", "Recording started")
            
        } catch (e: Exception) {
            Toast.makeText(this, "录音启动失败: " + e.message, Toast.LENGTH_SHORT).show()
            LogActivity.addLog("SymptomInputActivity", "Recording error: " + e.message)
        }
    }
    
    private fun stopRecordingAndRecognize() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            btnVoiceInput.text = "🎤 语音输入"
            btnVoiceInput.backgroundTintList = getColorStateList(android.R.color.holo_orange_dark)
            
            LogActivity.addLog("SymptomInputActivity", "Recording stopped, file: " + audioFile?.length() + " bytes")
            
            // 调用百度语音API识别
            audioFile?.let { recognizeSpeech(it) }
            
        } catch (e: Exception) {
            Toast.makeText(this, "停止录音失败: " + e.message, Toast.LENGTH_SHORT).show()
            LogActivity.addLog("SymptomInputActivity", "Stop recording error: " + e.message)
        }
    }
    
    private fun recognizeSpeech(audioFile: File) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SymptomInputActivity, "识别中...", Toast.LENGTH_SHORT).show()
                
                val result = withContext(Dispatchers.IO) {
                    callBaiduSpeechAPI(audioFile)
                }
                
                if (result.isNotEmpty()) {
                    findViewById<EditText>(R.id.etSymptom).setText(result)
                    Toast.makeText(this@SymptomInputActivity, "识别完成", Toast.LENGTH_SHORT).show()
                    LogActivity.addLog("SymptomInputActivity", "Recognition result: $result")
                } else {
                    Toast.makeText(this@SymptomInputActivity, "未能识别，请重试", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@SymptomInputActivity, "识别失败: " + e.message, Toast.LENGTH_SHORT).show()
                LogActivity.addLog("SymptomInputActivity", "Recognition error: " + e.message)
            }
        }
    }
    
    private fun callBaiduSpeechAPI(audioFile: File): String {
        val client = OkHttpClient()
        
        // 1. 获取access_token
        val tokenUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials" +
                "&client_id=${BaiduSpeechConfig.API_KEY}&client_secret=${BaiduSpeechConfig.SECRET_KEY}"
        
        val tokenRequest = Request.Builder().url(tokenUrl).build()
        val tokenResponse = client.newCall(tokenRequest).execute()
        val tokenJson = JSONObject(tokenResponse.body?.string() ?: "{}")
        val accessToken = tokenJson.optString("access_token")
        
        if (accessToken.isEmpty()) {
            LogActivity.addLog("SymptomInputActivity", "Failed to get access token")
            return ""
        }
        
        // 2. 调用语音识别API
        val speechUrl = "https://vop.baidu.com/server_api?dev_pid=1537&cuid=${BaiduSpeechConfig.APP_ID}&token=$accessToken"
        
        // 读取音频文件并转为Base64
        val audioBase64 = android.util.Base64.encodeToString(audioFile.readBytes(), android.util.Base64.NO_WRAP)
        
        val jsonBody = JSONObject().apply {
            put("format", "pcm")
            put("rate", 16000)
            put("channel", 1)
            put("cuid", BaiduSpeechConfig.APP_ID)
            put("token", accessToken)
            put("speech", audioBase64)
            put("len", audioFile.length())
        }
        
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(speechUrl)
            .post(requestBody)
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: "{}"
        LogActivity.addLog("SymptomInputActivity", "API response: $responseBody")
        
        val resultJson = JSONObject(responseBody)
        val resultArray = resultJson.optJSONArray("result")
        
        return if (resultArray != null && resultArray.length() > 0) {
            resultArray.getString(0)
        } else {
            ""
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
            startRecording()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            mediaRecorder?.apply {
                stop()
                release()
            }
        }
        mediaRecorder = null
    }
}

