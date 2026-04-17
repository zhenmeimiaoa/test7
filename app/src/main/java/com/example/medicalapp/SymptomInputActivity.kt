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
    
    // 褰曢煶鍙傛暟 - 鐧惧害API瑕佹眰锛歅CM 16kHz 16bit 鍗曞０閬?    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_symptom_input)
        
        LogActivity.addLog("SymptomInputActivity", "onCreate started")
        
        if (!MainActivity.isIdentityVerified) {
            Toast.makeText(this, "璇峰厛瀹屾垚韬唤楠岃瘉", Toast.LENGTH_LONG).show()
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
        
        // 鍒濆闅愯棌AI鍒嗘瀽鎸夐挳鍜岀粨鏋?        btnAiAnalyze.visibility = android.view.View.GONE
        tvAiResult.visibility = android.view.View.GONE
        
        val info = MainActivity.idCardInfo
        tvIdentityInfo.text = "褰撳墠鎮ｈ€咃細${info?.name ?: "鏈煡"}锛堝凡楠岃瘉锛?
        
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
                Toast.makeText(this, "璇峰厛杈撳叆鎴栬瘑鍒棁鐘?, Toast.LENGTH_SHORT).show()
            }
        }
        
        btnSave.setOnClickListener {
            val symptom = etSymptom.text.toString().trim()
            val aiResult = tvAiResult.text.toString()
            if (symptom.isEmpty()) {
                Toast.makeText(this, "璇峰厛杈撳叆鐥囩姸", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "褰曢煶鍒濆鍖栧け璐?, Toast.LENGTH_SHORT).show()
                return
            }
            
            pcmData = null
            isRecording = true
            audioRecord?.startRecording()
            
            btnVoiceInput.text = "馃帣锔?鍋滄褰曢煶"
            btnVoiceInput.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
            Toast.makeText(this, "璇疯璇?..", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "褰曢煶鍚姩澶辫触: ${e.message}", Toast.LENGTH_SHORT).show()
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
            
            btnVoiceInput.text = "馃帳 璇煶杈撳叆"
            btnVoiceInput.backgroundTintList = getColorStateList(android.R.color.holo_orange_dark)
            
            val dataSize = pcmData?.size ?: 0
            LogActivity.addLog("SymptomInputActivity", "Recording stopped, PCM data: $dataSize bytes")
            
            if (dataSize > 0) {
                recognizeSpeech()
            } else {
                Toast.makeText(this, "褰曢煶鏁版嵁涓虹┖", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "鍋滄褰曢煶澶辫触: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun recognizeSpeech() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SymptomInputActivity, "璇嗗埆涓?..", Toast.LENGTH_SHORT).show()
                
                val result = withContext(Dispatchers.IO) {
                    callBaiduSpeechAPI()
                }
                
                if (result.isNotEmpty()) {
                    findViewById<EditText>(R.id.etSymptom).setText(result)
                    currentSymptom = result
                    Toast.makeText(this@SymptomInputActivity, "璇嗗埆瀹屾垚锛屽彲鐐瑰嚮AI鍒嗘瀽鑾峰彇寤鸿", Toast.LENGTH_SHORT).show()
                    LogActivity.addLog("SymptomInputActivity", "Recognition result: $result")
                    
                    // 鏄剧ずAI鍒嗘瀽鎸夐挳
                    btnAiAnalyze.visibility = android.view.View.VISIBLE
                    btnAiAnalyze.text = "馃 AI鍒嗘瀽鐥囩姸"
                    
                } else {
                    Toast.makeText(this@SymptomInputActivity, "鏈兘璇嗗埆锛岃閲嶈瘯", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SymptomInputActivity, "璇嗗埆澶辫触: ${e.message}", Toast.LENGTH_SHORT).show()
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
     * AI鍒嗘瀽鐥囩姸 - 妗嗘灦棰勭暀锛屽緟鎺ュ叆AI妯″瀷
     */
    private fun analyzeSymptomWithAI(symptom: String) {
        Toast.makeText(this, "AI鍒嗘瀽涓?..", Toast.LENGTH_SHORT).show()
        btnAiAnalyze.isEnabled = false
        btnAiAnalyze.text = "馃 鍒嗘瀽涓?.."
        
        lifecycleScope.launch {
            try {
                val aiResult = withContext(Dispatchers.IO) {
                    // TODO: 鎺ュ叆AI妯″瀷锛堢櫨搴︽枃蹇冧竴瑷€/闃块噷閫氫箟鍗冮棶/鏈湴妯″瀷绛夛級
                    callAIModel(symptom)
                }
                
                // 鏄剧ずAI缁撴灉
                tvAiResult.text = aiResult
                tvAiResult.visibility = android.view.View.VISIBLE
                
                // 淇濆瓨鍒版棩蹇?                saveAiAnalysisToLog(symptom, aiResult)
                
                Toast.makeText(this@SymptomInputActivity, "AI鍒嗘瀽瀹屾垚", Toast.LENGTH_SHORT).show()
                LogActivity.addLog("SymptomInputActivity", "AI analysis completed")
                
            } catch (e: Exception) {
                Toast.makeText(this@SymptomInputActivity, "AI鍒嗘瀽澶辫触: ${e.message}", Toast.LENGTH_SHORT).show()
                LogActivity.addLog("SymptomInputActivity", "AI analysis error: ${e.message}")
            } finally {
                btnAiAnalyze.isEnabled = true
                btnAiAnalyze.text = "馃 閲嶆柊鍒嗘瀽"
            }
        }
    }
    
    /**
     * 璋冪敤AI妯″瀷 - 寰呭疄鐜?     * 鍙€夐」锛?     * 1. 鐧惧害鏂囧績涓€瑷€ API
     * 2. 闃块噷閫氫箟鍗冮棶 API  
     * 3. 绉戝ぇ璁鏄熺伀 API
     * 4. 鏈湴杞婚噺绾фā鍨嬶紙濡俆inyLLM锛?     */
    private fun callAIModel(symptom: String): String {
        return try {
            val patientName = MainActivity.idCardInfo?.name ?: "鏈煡"
            
            val prompt = """璇蜂綔涓轰竴浣嶇粡楠屼赴瀵岀殑鍏ㄧ鍖荤敓锛屾牴鎹互涓嬫偅鑰呬俊鎭粰鍑哄氨璇婂缓璁細

鎮ｈ€呭鍚嶏細$patientName
鐥囩姸鎻忚堪锛?symptom

璇锋寜浠ヤ笅鏍煎紡鍥炲锛?馃攳銆愬彲鑳界梾鍥犮€? 绠€瑕佸垎鏋愬彲鑳界殑鐥呭洜锛?-3绉嶏級
馃彞銆愭帹鑽愮瀹ゃ€? 寤鸿灏辫瘖鐨勭瀹?鈿狅笍銆愮揣鎬ョ▼搴︺€? 鍒ゆ柇锛氿煍寸揣鎬?馃煛灏藉揩/馃煝鍙瀵?馃拪銆愪复鏃跺鐞嗐€? 灏辫瘖鍓嶅彲閲囧彇鐨勭紦瑙ｆ帾鏂?馃毃銆愬嵄闄╀俊鍙枫€? 濡傛灉鍑虹幇浠ヤ笅鎯呭喌璇风珛鍗冲氨鍖?
娉ㄦ剰锛氫互涓婂缓璁粎渚涘弬鑰冿紝涓嶈兘鏇夸唬鍖荤敓闈㈣瘖銆?""

            val jsonBody = org.json.JSONObject().apply {
                put("model", WenxinAIConfig.MODEL)
                put("messages", listOf(
                    org.json.JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                ))
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
                throw Exception("API閿欒: ${resultJson.getJSONObject("error").optString("message")}")
            }

            val choices = resultJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val content = choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")
                if (!content.isNullOrEmpty()) {
                    return content
                }
            }
            
            "AI鏈繑鍥炴湁鏁堝唴瀹?
            
        } catch (e: Exception) {
            LogActivity.addLog("SymptomInputActivity", "AI API error: ${e.message}")
            // 缃戠粶澶辫触鏃惰繑鍥炴ā鎷熸暟鎹紝纭繚鍔熻兘鍙敤
            val patientName = MainActivity.idCardInfo?.name ?: "鏈煡"
            return """
                銆怉I灏辫瘖寤鸿銆戯紙缃戠粶寮傚父锛屼娇鐢ㄧ绾垮缓璁級
                
                鎮ｈ€咃細$patientName
                鐥囩姸锛?symptom
                
                馃攳 鍒濇鍒嗘瀽锛?                鍙兘鏄笂鍛煎惛閬撴劅鏌撴垨鏅€氭劅鍐掋€?                
                馃彞 寤鸿灏辫瘖绉戝锛?                鍛煎惛鍐呯 鎴?鍏ㄧ鍖诲绉?                
                鈿狅笍 娉ㄦ剰浜嬮」锛?                1. 澶氫紤鎭紝澶氶ギ姘?                2. 濡傜棁鐘舵寔缁?澶╀互涓婃垨鍔犻噸锛岃鍙婃椂灏卞尰
                
                馃挕 鎻愮ず锛氱綉缁滃紓甯革紝寤鸿妫€鏌ョ綉缁滃悗閲嶆柊鍒嗘瀽銆?            """.trimIndent()
        }
    }
    
    /**
     * 淇濆瓨AI鍒嗘瀽缁撴灉鍒版棩蹇?     */
    private fun saveAiAnalysisToLog(symptom: String, aiResult: String) {
        val info = MainActivity.idCardInfo
        val logEntry = """
            銆怉I灏辫瘖寤鸿鍒嗘瀽銆?            鎮ｈ€咃細${info?.name ?: "鏈煡"}
            鐥囩姸锛?symptom
            AI寤鸿锛?            $aiResult
            鍒嗘瀽鏃堕棿锛?{java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())}
            ====================
        """.trimIndent()
        
        LogActivity.addLog("AIAnalysis", logEntry)
    }
    
    private fun saveSymptom(symptom: String, aiResult: String) {
        MainActivity.symptomText = symptom
        
        val info = MainActivity.idCardInfo
        val logEntry = """
            銆愮梾鐥囦俊鎭繚瀛樸€?            鎮ｈ€咃細${info?.name ?: "鏈煡"}
            韬唤璇佸彿锛?{info?.idNumber ?: "鏈煡"}
            鐥囩姸鎻忚堪锛?symptom
            AI寤鸿锛?{if(aiResult.isNotEmpty()) "宸茬敓鎴? else "鏈垎鏋?}
            璁板綍鏃堕棿锛?{java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())}
            ====================
        """.trimIndent()
        
        LogActivity.addLog("SymptomSave", logEntry)
        Toast.makeText(this, "鐥呯棁淇℃伅宸蹭繚瀛?, Toast.LENGTH_SHORT).show()
        
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
