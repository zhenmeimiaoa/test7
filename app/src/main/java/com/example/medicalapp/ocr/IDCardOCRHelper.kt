package com.example.medicalapp.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.medicalapp.IDCardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class IDCardOCRHelper {

    private val TAG = "BaiduOCR"
    private val API_KEY = "Su4BMNAumYZWBzJbuiL1wASF"
    private val SECRET_KEY = "Zyw7FNQ3EvobHqy41ZxIoTnLQYcVW83K"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var accessToken: String? = null

    suspend fun recognizeIDCard(bitmap: Bitmap): IDCardInfo? {
        Log.d(TAG, "========== recognizeIDCard START ==========")
        
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Inside withContext, thread: ${Thread.currentThread().name}")
            
            try {
                // Step 1: Get token
                Log.d(TAG, "Step 1: Checking access token...")
                if (accessToken == null) {
                    Log.d(TAG, "Token is null, getting new token...")
                    accessToken = getAccessToken()
                    Log.d(TAG, "Got token result: ${accessToken != null}")
                } else {
                    Log.d(TAG, "Token exists: ${accessToken?.take(10)}...")
                }

                if (accessToken == null) {
                    Log.e(TAG, "ERROR: Failed to get access token")
                    return@withContext null
                }

                // Step 2: Convert image
                Log.d(TAG, "Step 2: Converting bitmap to base64...")
                val imageBase64 = bitmapToBase64(bitmap)
                Log.d(TAG, "Image converted, length: ${imageBase64.length}")

                // Step 3: Call API
                Log.d(TAG, "Step 3: Calling Baidu OCR API...")
                val result = callBaiduOCR(imageBase64)
                Log.d(TAG, "API call result: ${result != null}")
                
                Log.d(TAG, "========== recognizeIDCard END ==========")
                result

            } catch (e: Exception) {
                Log.e(TAG, "EXCEPTION in recognizeIDCard: ${e.javaClass.simpleName}", e)
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTrace.joinToString("\n")}")
                null
            }
        }
    }

    private fun getAccessToken(): String? {
        Log.d(TAG, "getAccessToken called")
        
        val url = "https://aip.baidubce.com/oauth/2.0/token" +
                "?grant_type=client_credentials" +
                "&client_id=$API_KEY" +
                "&client_secret=$SECRET_KEY"

        Log.d(TAG, "Token URL: $url")

        return try {
            val request = Request.Builder()
                .url(url)
                .post(FormBody.Builder().build())
                .build()

            Log.d(TAG, "Sending token request...")
            val response = client.newCall(request).execute()
            Log.d(TAG, "Token response code: ${response.code}")
            
            val body = response.body?.string()
            Log.d(TAG, "Token response body: $body")

            if (body == null) {
                Log.e(TAG, "Token response body is null")
                return null
            }

            val json = JSONObject(body)
            val token = json.optString("access_token")

            if (token.isEmpty()) {
                Log.e(TAG, "Token is empty in response")
                null
            } else {
                Log.d(TAG, "Token obtained successfully: ${token.take(20)}...")
                token
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get token exception: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    private fun callBaiduOCR(imageBase64: String): IDCardInfo? {
        Log.d(TAG, "callBaiduOCR called")

        val url = "https://aip.baidubce.com/rest/2.0/ocr/v1/idcard" +
                "?access_token=$accessToken" +
                "&id_card_side=front"

        Log.d(TAG, "OCR URL: ${url.take(80)}...")

        val formBody = FormBody.Builder()
            .add("image", imageBase64)
            .add("detect_direction", "true")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        return try {
            Log.d(TAG, "Sending OCR request...")
            val response = client.newCall(request).execute()
            Log.d(TAG, "OCR response code: ${response.code}")
            
            val body = response.body?.string()
            Log.d(TAG, "OCR response body: $body")

            if (body == null) {
                Log.e(TAG, "OCR response body is null")
                return null
            }

            val json = JSONObject(body)

            if (json.has("error_code")) {
                val errorCode = json.getInt("error_code")
                val errorMsg = json.optString("error_msg")
                Log.e(TAG, "Baidu API Error $errorCode: $errorMsg")
                if (errorCode == 110 || errorCode == 111) {
                    accessToken = null
                }
                return null
            }

            val wordsResult = json.optJSONObject("words_result")
            if (wordsResult == null) {
                Log.e(TAG, "No words_result in response")
                return null
            }

            val name = extractField(wordsResult, "姓名")
            val idNumber = extractField(wordsResult, "公民身份号码")
            val gender = extractField(wordsResult, "性别")
            val address = extractField(wordsResult, "住址")

            Log.d(TAG, "Parsed - Name: $name, ID: $idNumber, Gender: $gender")

            if (name.isEmpty() && idNumber.isEmpty()) {
                Log.e(TAG, "Parsed fields are empty")
                return null
            }

            IDCardInfo(
                name = name,
                idNumber = idNumber,
                gender = gender,
                address = address
            )

        } catch (e: Exception) {
            Log.e(TAG, "OCR call exception: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    private fun extractField(wordsResult: JSONObject, fieldName: String): String {
        val fieldObj = wordsResult.optJSONObject(fieldName)
        val value = fieldObj?.optString("words", "") ?: ""
        Log.d(TAG, "Field [$fieldName]: $value")
        return value
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun close() {}
}

