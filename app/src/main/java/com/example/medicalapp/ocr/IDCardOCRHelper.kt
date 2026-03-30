package com.example.medicalapp.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.example.medicalapp.model.IDCardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IDCardOCRHelper {
    
    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )
    
    suspend fun recognizeIDCard(bitmap: Bitmap): IDCardInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                
                val result = suspendCancellableCoroutine { continuation ->
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val text = visionText.text
                            continuation.resume(parseIDCardText(text))
                        }
                        .addOnFailureListener { e ->
                            continuation.resume(null)
                        }
                }
                
                result
                
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun parseIDCardText(text: String): IDCardInfo? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        var name = ""
        var idNumber = ""
        var gender = ""
        var address = ""
        
        for (line in lines) {
            // 身份证号：18位数字
            if (line.matches(Regex(".*\\d{17}[\\dXx].*"))) {
                idNumber = line.replace(Regex("[^\\dXx]"), "")
            }
            
            // 姓名：通常在"姓名"后面，2-4个汉字
            if (line.contains("姓名") || line.matches(Regex("^[\\u4e00-\\u9fa5]{2,4}$"))) {
                name = line.replace("姓名", "").replace(":", "").replace("：", "").trim()
                if (name.isEmpty() && line.matches(Regex("^[\\u4e00-\\u9fa5]{2,4}$"))) {
                    name = line
                }
            }
            
            // 性别
            if (line.contains("性别") || line == "男" || line == "女") {
                gender = if (line.contains("男")) "男" else if (line.contains("女")) "女" else line
            }
            
            // 地址
            if (line.contains("住址") || line.contains("地址") || line.contains("省") || line.contains("市")) {
                address = line.replace("住址", "").replace("地址", "").replace(":", "").replace("：", "").trim()
            }
        }
        
        // 如果没找到姓名，尝试从第一行找（通常是姓名）
        if (name.isEmpty() && lines.isNotEmpty()) {
            val firstLine = lines[0]
            if (firstLine.matches(Regex("^[\\u4e00-\\u9fa5]{2,4}$"))) {
                name = firstLine
            }
        }
        
        return if (idNumber.isNotEmpty()) {
            IDCardInfo(
                name = name,
                idNumber = idNumber,
                gender = gender,
                address = address
            )
        } else {
            null
        }
    }
    
    fun close() {
        recognizer.close()
    }
}
