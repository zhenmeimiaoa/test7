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
    
    // ¼����� - �ٶ�APIҪ��PCM 16kHz 16bit ������
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_symptom_input)
        
        LogActivity.addLog("SymptomInputActivity", "onCreate started")
        
        if (!MainActivity.isIdentityVerified) {
            Toast.makeText(this, "������������֤", Toast.LENGTH_LONG).show()
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
        
        // ��ʼ����AI������ť�ͽ��
        btnAiAnalyze.visibility = android.view.View.GONE
        tvAiResult.visibility = android.view.View.GONE
        
        val info = MainActivity.idCardInfo
        tvIdentityInfo.text = "��ǰ���ߣ�${info?.name ?: "δ֪"}������֤��"
        
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
                Toast.makeText(this, "���������ʶ��֢״", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnSave.setOnClickListener {
            val symptom = etSymptom.text.toString().trim()
            val aiResult = tvAiResult.text.toString()
            if (symptom.isEmpty()) {
                Toast.makeText(this, "��������֢״", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "¼���ʼ��ʧ��", Toast.LENGTH_SHORT).show()
                return
            }
            
            pcmData = null
            isRecording = true
            audioRecord?.startRecording()
            
            btnVoiceInput.text = "??? ֹͣ¼��"
            btnVoiceInput.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
            Toast.makeText(this, "��˵��...", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "¼�����ʧ��: ${e.message}", Toast.LENGTH_SHORT).show()
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
            
            btnVoiceInput.text = "?? ��������"
            btnVoiceInput.backgroundTintList = getColorStateList(android.R.color.holo_orange_dark)
            
            val dataSize = pcmData?.size ?: 0
            LogActivity.addLog("SymptomInputActivity", "Recording stopped, PCM data: $dataSize bytes")
            
            if (dataSize > 0) {
                recognizeSpeech()
            } else {
                Toast.makeText(this, "¼������Ϊ��", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "ֹͣ¼��ʧ��: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun recognizeSpeech() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SymptomInputActivity, "ʶ����...", Toast.LENGTH_SHORT).show()
                
                val result = withContext(Dispatchers.IO) {
                    callBaiduSpeechAPI()
                }
                
                if (result.isNotEmpty()) {
                    findViewById<EditText>(R.id.etSymptom).setText(result)
                    currentSymptom = result
                    Toast.makeText(this@SymptomInputActivity, "ʶ����ɣ��ɵ��AI������ȡ����", Toast.LENGTH_SHORT).show()
                    LogActivity.addLog("SymptomInputActivity", "Recognition result: $result")
                    
                    // ��ʾAI������ť
                    btnAiAnalyze.visibility = android.view.View.VISIBLE
                    btnAiAnalyze.text = "?? AI����֢״"
                    
                } else {
                    Toast.makeText(this@SymptomInputActivity, "δ��ʶ��������", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SymptomInputActivity, "ʶ��ʧ��: ${e.message}", Toast.LENGTH_SHORT).show()
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
     * AI����֢״ - ���Ԥ���������AIģ��
     */
    private fun analyzeSymptomWithAI(symptom: String) {
        Toast.makeText(this, "AI������...", Toast.LENGTH_SHORT).show()
        btnAiAnalyze.isEnabled = false
        btnAiAnalyze.text = "?? ������..."
        
        lifecycleScope.launch {
            try {
                val aiResult = withContext(Dispatchers.IO) {
                    // TODO: ����AIģ�ͣ��ٶ�����һ��/����ͨ��ǧ��/����ģ�͵ȣ�
                    callAIModel(symptom)
                }
                
                // ��ʾAI���
                tvAiResult.text = aiResult
                tvAiResult.visibility = android.view.View.VISIBLE
                
                // ���浽��־
                saveAiAnalysisToLog(symptom, aiResult)
                
                Toast.makeText(this@SymptomInputActivity, "AI�������", Toast.LENGTH_SHORT).show()
                LogActivity.addLog("SymptomInputActivity", "AI analysis completed")
                
            } catch (e: Exception) {
                Toast.makeText(this@SymptomInputActivity, "AI����ʧ��: ${e.message}", Toast.LENGTH_SHORT).show()
                LogActivity.addLog("SymptomInputActivity", "AI analysis error: ${e.message}")
            } finally {
                btnAiAnalyze.isEnabled = true
                btnAiAnalyze.text = "?? ���·���"
            }
        }
    }
    
    /**
     * ����AIģ�� - ��ʵ��
     * ��ѡ�
     * 1. �ٶ�����һ�� API
     * 2. ����ͨ��ǧ�� API  
     * 3. �ƴ�Ѷ���ǻ� API
     * 4. ����������ģ�ͣ���TinyLLM��
     */
    private suspend fun callAIModel(symptom: String): String {
        // ʹ�ñ��� AI ��ģ�ͣ�Qwen2.5-0.5B��
        val aiHelper = LocalLLMHelper(this)
        
        // ��ʼ��ģ�ͣ���һ�λḴ���ļ���������
        val initialized = aiHelper.initialize()
        
        if (!initialized) {
            return "ģ�ͳ�ʼ��ʧ�ܣ�����ģ���ļ��Ƿ����"
        }
        
        val patientName = MainActivity.idCardInfo?.name ?: ""
        return aiHelper.analyzeSymptom(symptom, patientName)
            ?? �������������Ͻ�������ο����������רҵҽ����ϡ�
            
            ����������ʵAIģ�͡�
        """.trimIndent()
    }
    
    /**
     * ����AI�����������־
     */
    private fun saveAiAnalysisToLog(symptom: String, aiResult: String) {
        val info = MainActivity.idCardInfo
        val logEntry = """
            ��AI���ｨ�������
            ���ߣ�${info?.name ?: "δ֪"}
            ֢״��$symptom
            AI���飺
            $aiResult
            ����ʱ�䣺${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())}
            ====================
        """.trimIndent()
        
        LogActivity.addLog("AIAnalysis", logEntry)
    }
    
    private fun saveSymptom(symptom: String, aiResult: String) {
        MainActivity.symptomText = symptom
        
        val info = MainActivity.idCardInfo
        val logEntry = """
            ����֢��Ϣ���桿
            ���ߣ�${info?.name ?: "δ֪"}
            ���֤�ţ�${info?.idNumber ?: "δ֪"}
            ֢״������$symptom
            AI���飺${if(aiResult.isNotEmpty()) "������" else "δ����"}
            ��¼ʱ�䣺${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())}
            ====================
        """.trimIndent()
        
        LogActivity.addLog("SymptomSave", logEntry)
        Toast.makeText(this, "��֢��Ϣ�ѱ���", Toast.LENGTH_SHORT).show()
        
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
