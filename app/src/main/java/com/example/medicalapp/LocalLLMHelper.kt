package com.example.medicalapp

import android.content.Context
import android.util.Log
import com.example.medicalapp.llama.AiChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LocalLLMHelper(private val context: Context) {
    
    private val TAG = "LocalLLMHelper"
    private var aiChat: AiChat? = null
    private val modelFile: File by lazy {
        File(context.filesDir, "qwen2.5-0.5b-instruct-q4_k_m.gguf")
    }
    
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始初始化本地AI模型...")
            if (!modelFile.exists()) {
                Log.d(TAG, "从 assets 复制模型文件...")
                copyModelFromAssets()
            }
            aiChat = AiChat()
            aiChat?.load(modelFile.absolutePath, context.filesDir.absolutePath)
            Log.d(TAG, "本地AI模型初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "模型初始化失败", e)
            false
        }
    }
    
    suspend fun analyzeSymptom(symptom: String, patientName: String = ""): String {
        return withContext(Dispatchers.IO) {
            try {
                if (aiChat == null) {
                    return@withContext "错误：模型未初始化"
                }
                val prompt = buildMedicalPrompt(symptom, patientName)
                val response = StringBuilder()
                aiChat?.generate(prompt, object : AiChat.Callback {
                    override fun onToken(token: String) {
                        response.append(token)
                    }
                })
                formatAIResult(response.toString())
            } catch (e: Exception) {
                Log.e(TAG, "分析异常", e)
                "AI分析失败: "
            }
        }
    }
    
    private fun buildMedicalPrompt(symptom: String, patientName: String): String {
        return """<|im_start|>system
你是一位经验丰富的全科医生，请根据患者的症状描述给出专业的就诊建议。语气要温和、专业、有同理心。<|im_end|>
<|im_start|>user
患者姓名：
症状描述：

请按以下格式给出建议：
🔍【可能病因】- 简要分析可能的病因（2-3种）
🏥【推荐科室】- 建议就诊的科室
⚠️【紧急程度】- 判断：🔴紧急/🟡尽快/🟢可观察
💊【临时处理】- 就诊前可采取的缓解措施
🚨【危险信号】- 如果出现以下情况请立即就医

必须包含免责声明：以上建议仅供参考，不能替代医生面诊。<|im_end|>
<|im_start|>assistant
""".trimIndent()
    }
    
    private fun copyModelFromAssets() {
        context.assets.open("qwen2.5-0.5b-instruct-q4_k_m.gguf").use { input ->
            FileOutputStream(modelFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }
                output.flush()
                Log.d(TAG, "模型复制完成:  bytes")
            }
        }
    }
    
    private fun formatAIResult(content: String): String {
        return """
            🤖【本地AI就诊建议】（离线模型）
            
            
            
            ──────────────────────
            ⚠️ 免责声明：以上建议由本地AI模型生成，仅供参考，不能替代专业医生的诊断和治疗建议。
            💡 提示：本分析完全离线完成，保护您的隐私。
        """.trimIndent()
    }
    
    fun release() {
        aiChat?.unload()
        aiChat = null
    }
}
