package com.example.medicalapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
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
import java.io.ByteArrayOutputStream

class SymptomInputActivity : AppCompatActivity() {
    
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private lateinit var btnVoiceInput: Button
    private lateinit var btnAiAnalyze: Button
    private lateinit var tvAiResult: TextView
    private var pcmData: ByteArray? = null
    private var currentSymptom: String = ""
    
    // 录音参数 - 百度API要求：PCM 16kHz 16bit 单声道
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_symptom_input)
        
        LogActivity.addLog("SymptomInputActivity", "onCreate started")
        
        if (!MainActivity.isIdentityVerified) {
            Toast.makeText(this, "请先完成身份验证", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        val tvIdentityInfo = findViewById<TextView>(R.id.tvIdentityInfo)
        val etSymptom = findViewById<EditText>(R.id.etSymptom)
        btnVoiceInput = findViewById<Button>(R.id.btnVoiceInput)
        btnAiAnalyze = findViewById<Button>(R.id.btnAiAnalyze)
        tvAiResult = findViewById<TextView>(R.id.tvAiResult)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnLogs = findViewById<Button>(R.id.btnLogs)
        
        // 初始隐藏AI分析按钮和结果
        btnAiAnalyze.visibility = android.view.View.GONE
        tvAiResult.visibility = android.view.View.GONE
        
        val info = MainActivity.idCardInfo
        tvIdentityInfo.text = "当前患者：${info?.name ?: "未知"}（已验证）"
        
        btnVoiceInput.setOnClickListener {
            if (isRecording) {
                stopRecordingAndRecognize()
            } else {
                checkPermissionAndStartRecording()
            }
        }
        
        btnAiAnalyze.setOnClickListener {
            val symptom = etSymptom.text.toString().trim()
            if (symptom.isNotEmpty()) {
                analyzeSymptomWithAI(symptom)
            } else {
                Toast.makeText(this, "请先输入或识别症状", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnSave.setOnClickListener {
            val symptom = etSymptom.text.toString().trim()
            val aiResult = tvAiResult.text.toString()
            if (symptom.isEmpty()) {
                Toast.makeText(this, "请先输入症状", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveSymptom(symptom, aiResult)
        }
        
        btnBack.setOnClickListener { finish() }
        btnLogs.setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
        
        LogActivity.addLog("SymptomInputActivity", "onCreate completed")
    }
    
    private fun checkPermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
        } else {
            startRecording()
        }
    }
    
    private fun startRecording() {
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "录音初始化失败", Toast.LENGTH_SHORT).show()
                return
            }
            
            pcmData = null
            isRecording = true
            audioRecord?.startRecording()
            
            btnVoiceInput.text = "🎙️ 停止录音"
            btnVoiceInput.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
            Toast.makeText(this, "请说话...", Toast.LENGTH_SHORT).show()
            LogActivity.addLog("SymptomInputActivity", "Recording started (PCM 16kHz)")
            
            Thread {
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(minBufferSize)
                
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                    }
                }
                
                pcmData = outputStream.toByteArray()
                outputStream.close()
            }.start()
            
        } catch (e: Exception) {
            Toast.makeText(this, "录音启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            LogActivity.addLog("SymptomInputActivity", "Recording error: ${e.message}")
        }
    }
    
    private fun stopRecordingAndRecognize() {
        try {
            isRecording = false
            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null
            
            btnVoiceInput.text = "🎤 语音输入"
            btnVoiceInput.backgroundTintList = getColorStateList(android.R.color.holo_orange_dark)
            
            val dataSize = pcmData?.size ?: 0
            LogActivity.addLog("SymptomInputActivity", "Recording stopped, PCM data: $dataSize bytes")
            
            if (dataSize > 0) {
                recognizeSpeech()
            } else {
                Toast.makeText(this, "录音数据为空", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "停止录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun recognizeSpeech() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SymptomInputActivity, "识别中...", Toast.LENGTH_SHORT).show()
                
                val result = withContext(Dispatchers.IO) {
                    callBaiduSpeechAPI()
                }
                
                if (result.isNotEmpty()) {
                    findViewById<EditText>(R.id.etSymptom).setText(result)
                    currentSymptom = result
                    Toast.makeText(this@SymptomInputActivity, "识别完成，可点击AI分析获取建议", Toast.LENGTH_SHORT).show()
                    LogActivity.addLog("SymptomInputActivity", "Recognition result: $result")
                    
                    // 显示AI分析按钮
                    btnAiAnalyze.visibility = android.view.View.VISIBLE
                    btnAiAnalyze.text = "🤖 AI分析症状"
                    
                } else {
                    Toast.makeText(this@SymptomInputActivity, "未能识别，请重试", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SymptomInputActivity, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                LogActivity.addLog("SymptomInputActivity", "Recognition error: ${e.message}")
            }
        }
    }
    
    private fun callBaiduSpeechAPI(): String {
        val client = OkHttpClient()
        
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
        
        val audioBase64 = android.util.Base64.encodeToString(pcmData, android.util.Base64.NO_WRAP)
        
        val jsonBody = JSONObject().apply {
            put("format", "pcm")
            put("rate", 16000)
            put("dev_pid", 1537)
            put("channel", 1)
            put("token", accessToken)
            put("cuid", BaiduSpeechConfig.APP_ID)
            put("len", pcmData?.size ?: 0)
            put("speech", audioBase64)
        }
        
        LogActivity.addLog("SymptomInputActivity", "Request: format=pcm, rate=16000, len=${pcmData?.size}")
        
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://vop.baidu.com/server_api")
            .post(requestBody)
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: "{}"
        LogActivity.addLog("SymptomInputActivity", "API response: $responseBody")
        
        val resultJson = JSONObject(responseBody)
        
        val errNo = resultJson.optInt("err_no", -1)
        if (errNo != 0) {
            val errMsg = resultJson.optString("err_msg", "unknown error")
            LogActivity.addLog("SymptomInputActivity", "API error $errNo: $errMsg")
            return ""
        }
        
        if (resultJson.has("result")) {
            val resultArray = resultJson.getJSONArray("result")
            if (resultArray.length() > 0) {
                return resultArray.getString(0)
            }
        }
        
        return ""
    }
    
    /**
     * AI分析症状 - 框架预留，待接入AI模型
     */
    private fun analyzeSymptomWithAI(symptom: String) {
        Toast.makeText(this, "AI分析中...", Toast.LENGTH_SHORT).show()
        btnAiAnalyze.isEnabled = false
        btnAiAnalyze.text = "🤖 分析中..."
        
        lifecycleScope.launch {
            try {
                val aiResult = withContext(Dispatchers.IO) {
                    // TODO: 接入AI模型（百度文心一言/阿里通义千问/本地模型等）
                    callAIModel(symptom)
                }
                
                // 显示AI结果
                tvAiResult.text = aiResult
                tvAiResult.visibility = android.view.View.VISIBLE
                
                // 保存到日志
                saveAiAnalysisToLog(symptom, aiResult)
                
                Toast.makeText(this@SymptomInputActivity, "AI分析完成", Toast.LENGTH_SHORT).show()
                LogActivity.addLog("SymptomInputActivity", "AI analysis completed")
                
            } catch (e: Exception) {
                Toast.makeText(this@SymptomInputActivity, "AI分析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                LogActivity.addLog("SymptomInputActivity", "AI analysis error: ${e.message}")
            } finally {
                btnAiAnalyze.isEnabled = true
                btnAiAnalyze.text = "🤖 重新分析"
            }
        }
    }
    
    /**
     * 调用AI模型 - 待实现
     * 可选项：
     * 1. 百度文心一言 API
     * 2. 阿里通义千问 API  
     * 3. 科大讯飞星火 API
     * 4. 本地轻量级模型（如TinyLLM）
     */
    private fun callAIModel(symptom: String): String {
        // TODO: 实现AI模型调用
        // 临时返回模拟结果，用于测试框架
        return """
            【AI就诊建议】（模拟数据）
            
            症状：$symptom
            
            🔍 初步分析：
            根据症状描述，可能是上呼吸道感染或普通感冒。
            
            🏥 建议就诊科室：
            呼吸内科 或 全科医学科
            
            ⚠️ 注意事项：
            1. 多休息，多饮水
            2. 如症状持续3天以上或加重，请及时就医
            3. 避免自行服用抗生素
            
            💊 临时缓解建议：
            - 适当服用退烧药（如体温超过38.5℃）
            - 保持室内空气流通
            
            ⚠️ 免责声明：以上建议仅供参考，不能替代专业医生诊断。
            
            【待接入真实AI模型】
        """.trimIndent()
    }
    
    /**
     * 保存AI分析结果到日志
     */
    private fun saveAiAnalysisToLog(symptom: String, aiResult: String) {
        val info = MainActivity.idCardInfo
        val logEntry = """
            【AI就诊建议分析】
            患者：${info?.name ?: "未知"}
            症状：$symptom
            AI建议：
            $aiResult
            分析时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())}
            ====================
        """.trimIndent()
        
        LogActivity.addLog("AIAnalysis", logEntry)
    }
    
    private fun saveSymptom(symptom: String, aiResult: String) {
        MainActivity.symptomText = symptom
        
        val info = MainActivity.idCardInfo
        val logEntry = """
            【病症信息保存】
            患者：${info?.name ?: "未知"}
            身份证号：${info?.idNumber ?: "未知"}
            症状描述：$symptom
            AI建议：${if(aiResult.isNotEmpty()) "已生成" else "未分析"}
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
        if (requestCode == RECORD_AUDIO_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
    }
}
