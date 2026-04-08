package com.example.medicalapp.face

import android.graphics.Bitmap
import android.util.Base64
import com.example.medicalapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AliyunFaceHelper {
    
    private val accessKeyId = BuildConfig.ALIYUN_ACCESS_KEY_ID
    private val accessKeySecret = BuildConfig.ALIYUN_ACCESS_KEY_SECRET
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }
    
    suspend fun compareFaces(idCardBitmap: Bitmap, cameraBitmap: Bitmap): Pair<Double, String> {
        android.util.Log.d("AliyunFace", "AccessKeyId: ${accessKeyId.take(4)}...")  // 只显示前4位
        android.util.Log.d("AliyunFace", "AccessKeySecret: ${accessKeySecret.take(4)}...")  // 只显示前4位
        return withContext(Dispatchers.IO) {
            try {
                if (accessKeyId.isEmpty() || accessKeySecret.isEmpty()) {
                    Pair(0.0, "Error: Credentials not configured")
                } else {
                    val imageA = bitmapToBase64(idCardBitmap)
                    val imageB = bitmapToBase64(cameraBitmap)
                    callCompareFaceAPI(imageA, imageB)
                }
            } catch (e: Exception) {
                Pair(0.0, "Exception: ${e.message}")
            }
        }
    }
    
    private fun callCompareFaceAPI(imageA: String, imageB: String): Pair<Double, String> {
        val url = "https://facebody.cn-shanghai.aliyuncs.com"
        
        val params = mutableMapOf(
            "Action" to "CompareFace",
            "Version" to "2019-12-30",
            "Format" to "JSON",
            "AccessKeyId" to accessKeyId,
            "SignatureMethod" to "HMAC-SHA1",
            "Timestamp" to getTimestamp(),
            "SignatureVersion" to "1.0",
            "SignatureNonce" to UUID.randomUUID().toString(),
            "ImageDataA" to imageA,
            "ImageDataB" to imageB
        )
        
        val signature = calculateSignature(params, accessKeySecret)
        params["Signature"] = signature
        
        val formBody = FormBody.Builder().apply {
            params.forEach { (key, value) ->
                add(key, value)
            }
        }.build()
        
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return Pair(0.0, "Empty response")
        
        return try {
            val json = JSONObject(body)
            if (json.has("Data")) {
                val data = json.getJSONObject("Data")
                val confidence = data.getDouble("Confidence")
                Pair(confidence, "Success")
            } else if (json.has("Code")) {
                val code = json.getString("Code")
                val message = json.optString("Message", "Unknown error")
                Pair(0.0, "API Error: $code - $message")
            } else {
                Pair(0.0, "Invalid response format")
            }
        } catch (e: Exception) {
            Pair(0.0, "Parse error: ${e.message}")
        }
    }
    
    private fun calculateSignature(params: Map<String, String>, secret: String): String {
        val sortedParams = params.toSortedMap()
        val queryString = sortedParams.map { (key, value) ->
            "${percentEncode(key)}=${percentEncode(value)}"
        }.joinToString("&")
        
        val stringToSign = "POST&${percentEncode("/")}&${percentEncode(queryString)}"
        val signKey = "$secret&"
        
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(signKey.toByteArray(), "HmacSHA1"))
        val signature = mac.doFinal(stringToSign.toByteArray())
        
        return Base64.encodeToString(signature, Base64.DEFAULT).trim()
    }
    
    private fun percentEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }
    
    private fun getTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    
    fun close() {}
}

