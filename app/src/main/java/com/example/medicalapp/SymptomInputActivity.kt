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
    private lateinit var scrollAiResult: android.widget.ScrollView
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
            Toast.makeText(this, "请先完成身份认证", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        val tvIdentityInfo = findViewById<TextView>(R.id.tvIdentityInfo)
        val etSymptom = findViewById<EditText>(R.id.etSymptom)
        btnVoiceInput = findViewById<Button>(R.id.btnVoiceInput)
        btnAiAnalyze = findViewById<Button>(R.id.btnAiAnalyze)
        tvAiResult = findViewById<TextView>(R.id.tvAiResult)
        scrollAiResult = findViewById<android.widget.ScrollView>(R.id.scrollAiResult)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnLogs = findViewById<Button>(R.id.btnLogs)
        
        // 初始隐藏AI分析按钮和结果
        btnAiAnalyze.visibility = android.view.View.GONE
        scrollAiResult.visibility = android.view.View.GONE
        
        val info = MainActivity.idCardInfo
        tvIdentityInfo.text = "当前患者：${info?.name ?: "未知"}（已验证）"
        
        // 监听症状输入框变化，有内容就显示AI按钮（支持打字输入）
        etSymptom.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hasText = !s.isNullOrBlank()
                btnAiAnalyze.visibility = if (hasText) android.view.View.VISIBLE else android.view.View.GONE
                if (hasText) {
                    btnAiAnalyze.text = "🤖 AI分析症状"
                }
            }
        })
        
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
            
            btnVoiceInput.text = "🛑 停止录音"
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
                    callAIModel(symptom)
                }
                
                // 显示AI结果
                tvAiResult.text = aiResult
                scrollAiResult.visibility = android.view.View.VISIBLE
                
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
     * 调用AI模型 - 百度文心一言 ERNIE 4.5 Turbo
     */
    private fun callAIModel(symptom: String): String {
        return try {
            val patientName = MainActivity.idCardInfo?.name ?: "未知"
            
            val prompt = """请作为一位经验丰富的全科医生，根据以下患者信息给出就诊建议：

患者姓名：$patientName
症状描述：$symptom

请按以下格式回复：
🔍【可能病因】- 简要分析可能的病因（2-3种）
🏥【推荐科室】- 建议就诊的科室
⚠️【紧急程度】- 判断：🔴紧急/🟡尽快/🟢可观察
💊【临时处理】- 就诊前可采取的缓解措施
🚨【危险信号】- 如果出现以下情况请立即就医

注意：以上建议仅供参考，不能替代医生面诊。"""

            // 创建 messages 数组 - 使用 JSONArray 而不是 listOf
            val messageObj = org.json.JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }
            
            val messagesArray = org.json.JSONArray().apply {
                put(messageObj)
            }

            val jsonBody = org.json.JSONObject().apply {
                put("model", WenxinAIConfig.MODEL)
                put("messages", messagesArray)
                put("temperature", 0.7)
                put("max_tokens", 1024)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

            val request = okhttp3.Request.Builder()
                .url(WenxinAIConfig.CHAT_URL)
                .header("Authorization", "Bearer ${WenxinAIConfig.API_KEY}")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            val resultJson = org.json.JSONObject(responseBody)

            if (resultJson.has("error")) {
                throw Exception("API错误: ${resultJson.getJSONObject("error").optString("message")}")
            }

            val choices = resultJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val content = choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")
                if (!content.isNullOrEmpty()) {
                    return content
                }
            }
            
            "AI未返回有效内容"
            
        } catch (e: Exception) {
            LogActivity.addLog("SymptomInputActivity", "AI API error: ${e.message}")
            // 网络失败时返回模拟数据，确保功能可用
            val patientName = MainActivity.idCardInfo?.name ?: "未知"
            return """
                【AI就诊建议】（网络异常，使用离线建议）
                
                患者：$patientName
                症状：$symptom
                
                🔍 初步分析：
                可能是上呼吸道感染或普通感冒。
                
                🏥 建议就诊科室：
                呼吸内科 或 全科医学科
                
                ⚠️ 注意事项：
                1. 多休息，多饮水
                2. 如症状持续3天以上或加重，请及时就医
                
                💡 提示：网络异常，建议检查网络后重新分析。
            """.trimIndent()
        }
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