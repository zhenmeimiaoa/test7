package com.example.medicalapp.ocr

import android.graphics.Bitmap
import android.util.Base64
import com.example.medicalapp.BuildConfig
import com.example.medicalapp.model.IDCardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class IDCardOCRHelper {
    
    private val accessKeyId = BuildConfig.ALIYUN_ACCESS_KEY_ID
    private val accessKeySecret = BuildConfig.ALIYUN_ACCESS_KEY_SECRET
    
    private val client = OkHttpClient.Builder().build()
    
    suspend fun recognizeIDCard(bitmap: Bitmap): IDCardInfo? {
        return withContext(Dispatchers.IO) {
            try {
                if (accessKeyId.isEmpty() || accessKeySecret.isEmpty()) {
                    return@withContext null
                }
                
                val imageBase64 = bitmapToBase64(bitmap)
                callOCRAPI(imageBase64)
                
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun callOCRAPI(imageBase64: String): IDCardInfo? {
        val url = "https://ocr-api.cn-hangzhou.aliyuncs.com"
        
        val params = mutableMapOf(
            "Action" to "RecognizeIdcard",
            "Version" to "2021-07-07",
            "Format" to "JSON",
            "AccessKeyId" to accessKeyId,
            "SignatureMethod" to "HMAC-SHA1",
            "Timestamp" to getTimestamp(),
            "SignatureVersion" to "1.0",
            "SignatureNonce" to UUID.randomUUID().toString(),
            "ImageURL" to imageBase64
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
        val body = response.body?.string() ?: return null
        
        return try {
            val json = JSONObject(body)
            if (json.has("Data")) {
                val data = json.getJSONObject("Data")
                val frontResult = data.optJSONObject("FrontResult")
                
                if (frontResult != null) {
                    IDCardInfo(
                        name = frontResult.optString("Name", ""),
                        idNumber = frontResult.optString("IDNumber", ""),
                        gender = frontResult.optString("Gender", ""),
                        address = frontResult.optString("Address", "")
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
