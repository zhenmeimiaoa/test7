package com.example.medicalapp

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class JiandaoyunApiHelper {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val jsonMediaType = "application/json".toMediaType()
    
    private fun getUploadToken(entryId: String, transactionId: String): Pair<List<JSONObject>, String> {
        val jsonBody = JSONObject().apply {
            put("app_id", JiandaoyunConfig.APP_ID)
            put("entry_id", entryId)
            put("transaction_id", transactionId)
        }
        
        val request = Request.Builder()
            .url("${JiandaoyunConfig.BASE_URL}/app/entry/file/get_upload_token")
            .header("Authorization", "Bearer ${JiandaoyunConfig.API_KEY}")
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody(jsonMediaType))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("获取凭证失败: ${response.code}")
            }
            
            val responseBody = response.body?.string() ?: "{}"
            val result = JSONObject(responseBody)
            
            val tokenList = result.optJSONArray("token_and_url_list")
                ?: throw Exception("凭证列表为空")
            
            val tokens = mutableListOf<JSONObject>()
            for (i in 0 until tokenList.length()) {
                tokens.add(tokenList.getJSONObject(i))
            }
            
            val finalTxId = result.optString("transaction_id", transactionId)
            return Pair(tokens, finalTxId)
        }
    }
    
    private fun uploadFileWithToken(file: File, tokenInfo: JSONObject): String {
        val token = tokenInfo.getString("token")
        val uploadUrl = tokenInfo.getString("url")
        
        val fileBody = file.asRequestBody("image/jpeg".toMediaType())
        
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("token", token)
            .addFormDataPart("file", file.name, fileBody)
            .build()
        
        val request = Request.Builder()
            .url(uploadUrl)
            .post(multipartBody)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("上传失败: ${response.code}")
            }
            
            val responseBody = response.body?.string() ?: "{}"
            val result = JSONObject(responseBody)
            
            if (!result.has("key")) {
                throw Exception("上传响应缺少key")
            }
            
            return result.getString("key")
        }
    }
    
    fun createPatient(
        name: String,
        idCard: String,
        gender: String,
        address: String,
        faceSimilarity: Float,
        idCardImage: File?,
        faceImage: File?
    ): Result<String> {
        return try {
            val transactionId = UUID.randomUUID().toString()
            val (tokenList, finalTxId) = getUploadToken(JiandaoyunConfig.FORM_PATIENT, transactionId)
            
            var idCardKey: String? = null
            if (idCardImage != null && idCardImage.exists()) {
                idCardKey = uploadFileWithToken(idCardImage, tokenList[0])
            }
            
            var faceKey: String? = null
            if (faceImage != null && faceImage.exists() && tokenList.size > 1) {
                faceKey = uploadFileWithToken(faceImage, tokenList[1])
            }
            
            val data = JSONObject().apply {
                put(JiandaoyunConfig.W_NAME, JSONObject().put("value", name))
                put(JiandaoyunConfig.W_ID_CARD, JSONObject().put("value", idCard))
                put(JiandaoyunConfig.W_GENDER, JSONObject().put("value", gender))
                put(JiandaoyunConfig.W_ADDRESS, JSONObject().put("value", address))
                put(JiandaoyunConfig.W_FACE_SIMILARITY, JSONObject().put("value", faceSimilarity.toDouble()))
                
                if (idCardKey != null) {
                    val arr = JSONArray().apply { put(idCardKey) }
                    put(JiandaoyunConfig.W_ID_CARD_IMAGE, JSONObject().put("value", arr))
                }
                
                if (faceKey != null) {
                    val arr = JSONArray().apply { put(faceKey) }
                    put(JiandaoyunConfig.W_FACE_IMAGE, JSONObject().put("value", arr))
                }
            }
            
            val jsonBody = JSONObject().apply {
                put("app_id", JiandaoyunConfig.APP_ID)
                put("entry_id", JiandaoyunConfig.FORM_PATIENT)
                put("transaction_id", finalTxId)
                put("data", data)
            }
            
            val request = Request.Builder()
                .url("${JiandaoyunConfig.BASE_URL}/app/entry/data/create")
                .header("Authorization", "Bearer ${JiandaoyunConfig.API_KEY}")
                .header("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("创建失败: ${response.code}"))
                }
                
                val responseBody = response.body?.string() ?: "{}"
                val result = JSONObject(responseBody)
                
                var recordId = result.optString("_id", "")
                if (recordId.isEmpty()) {
                    val dataObj = result.optJSONObject("data")
                    if (dataObj != null) {
                        recordId = dataObj.optString("_id", "")
                    }
                }
                
                if (recordId.isNotEmpty()) {
                    Result.success(recordId)
                } else {
                    Result.failure(Exception("创建失败: $responseBody"))
                }
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun createMedicalRecord(
        name: String,
        symptom: String,
        aiAdvice: String
    ): Result<String> {
        return try {
            val data = JSONObject().apply {
                put(JiandaoyunConfig.W_RECORD_NAME, JSONObject().put("value", name))
                put(JiandaoyunConfig.W_SYMPTOM, JSONObject().put("value", symptom))
                put(JiandaoyunConfig.W_AI_ADVICE, JSONObject().put("value", aiAdvice))
            }
            
            val jsonBody = JSONObject().apply {
                put("app_id", JiandaoyunConfig.APP_ID)
                put("entry_id", JiandaoyunConfig.FORM_RECORD)
                put("data", data)
            }
            
            val request = Request.Builder()
                .url("${JiandaoyunConfig.BASE_URL}/app/entry/data/create")
                .header("Authorization", "Bearer ${JiandaoyunConfig.API_KEY}")
                .header("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("创建失败: ${response.code}"))
                }
                
                val responseBody = response.body?.string() ?: "{}"
                val result = JSONObject(responseBody)
                
                var recordId = result.optString("_id", "")
                if (recordId.isEmpty()) {
                    val dataObj = result.optJSONObject("data")
                    if (dataObj != null) {
                        recordId = dataObj.optString("_id", "")
                    }
                }
                
                if (recordId.isNotEmpty()) {
                    Result.success(recordId)
                } else {
                    Result.failure(Exception("创建失败: $responseBody"))
                }
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}