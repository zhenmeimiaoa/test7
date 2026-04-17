package com.example.medicalapp.test

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 百度文心一言 AI 测试助手
 * 使用 ERNIE 4.5 Turbo 模型
 */
class WenxinAIHelper {
    
    companion object {
        const val API_KEY = "bce-v3/ALTAK-zqkOJUrkULEOgGPi7wzQh/5fbaa9a0656230f2882587f4dbc43ea81b6ba538"
        const val SECRET_KEY = "" // 如果需要，从百度控制台获取
        const val MODEL = "ernie-4.5-turbo-128k"
        const val CHAT_URL = "https://qianfan.baidubce.com/v2/chat/completions"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * 测试：分析症状
     */
    fun testSymptomAnalysis(symptom: String, patientName: String = "测试患者"): String {
        return try {
            val prompt = buildMedicalPrompt(symptom, patientName)
            callWenxinAPI(prompt)
        } catch (e: Exception) {
            "AI调用失败: "
        }
    }
    
    /**
     * 构建医疗咨询 Prompt
     */
    private fun buildMedicalPrompt(symptom: String, patientName: String): String {
        return """请作为一位经验丰富的全科医生，根据以下患者信息给出就诊建议：

患者姓名：
症状描述：

请按以下格式回复：
🔍【可能病因】- 简要分析可能的病因（2-3种）
🏥【推荐科室】- 建议就诊的科室
⚠️【紧急程度】- 判断：🔴紧急/🟡尽快/🟢可观察
💊【临时处理】- 就诊前可采取的缓解措施
🚨【危险信号】- 如果出现以下情况请立即就医

注意：以上建议仅供参考，不能替代医生面诊。"""
    }
    
    /**
     * 调用百度文心一言 API
     */
    private fun callWenxinAPI(prompt: String): String {
        // 构建请求体
        val jsonBody = JSONObject().apply {
            put("model", MODEL)
            put("messages", listOf(
                JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
            ))
            put("temperature", 0.7)
            put("max_tokens", 1024)
        }
        
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(CHAT_URL)
            .header("Authorization", "Bearer ")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: "{}"
        
        // 解析响应
        val resultJson = JSONObject(responseBody)
        
        // 检查错误
        if (resultJson.has("error")) {
            val error = resultJson.getJSONObject("error")
            throw Exception("API错误: ")
        }
        
        // 提取回复内容
        val choices = resultJson.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message")
            return message?.optString("content", "无回复内容") ?: "无回复内容"
        }
        
        return "无法解析API响应"
    }
}

// 简单测试函数
fun main() {
    println("=== 百度文心一言 AI 测试 ===")
    
    val aiHelper = WenxinAIHelper()
    
    // 测试症状
    val testSymptom = "头痛、发烧、喉咙痛"
    val patientName = "张三"
    
    println("患者: ")
    println("症状: ")
    println("正在调用 AI...")
    println()
    
    val result = aiHelper.testSymptomAnalysis(testSymptom, patientName)
    
    println("=== AI 回复 ===")
    println(result)
}
