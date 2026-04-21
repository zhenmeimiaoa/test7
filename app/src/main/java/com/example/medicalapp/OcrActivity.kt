package com.example.medicalapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class OcrActivity : AppCompatActivity() {
    
    private val PICK_IMAGE = 100
    private val API_KEY = "Su4BMNAumYZWBzJbuiL1wASF"
    private val SECRET_KEY = "2yw7FNQ3EvobHqy41ZxIoTnLQYcVW83K"
    
    private var capturedBitmap: Bitmap? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)
        
        LogActivity.addLog("OcrActivity", "onCreate")
        
        findViewById<Button>(R.id.btnSelectImage).setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                PICK_IMAGE
            )
        }
        
        // 右上角手动修改按钮
        findViewById<Button>(R.id.btnManualEdit).setOnClickListener {
            enableManualEdit()
        }
        
        // 确认并进入人脸验证
        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            saveIdentityAndProceed()
        }
        
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        
        findViewById<Button>(R.id.btnLogs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            data.data?.let { processImage(it) }
        }
    }
    
    private fun processImage(imageUri: Uri) {
        lifecycleScope.launch {
            try {
                findViewById<TextView>(R.id.tvStatus).text = "识别中..."
                
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
                } ?: run {
                    findViewById<TextView>(R.id.tvStatus).text = "加载图片失败"
                    return@launch
                }
                
                capturedBitmap = bitmap
                
                // 保存到文件供简道云上传
                try {
                    val idCardFile = File(cacheDir, "id_card_image.jpg")
                    FileOutputStream(idCardFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    LogActivity.addLog("OcrActivity", "ID card saved to ${idCardFile.absolutePath}")
                } catch (e: Exception) {
                    LogActivity.addLog("OcrActivity", "Save failed: ${e.message}")
                }
                
                findViewById<ImageView>(R.id.ivIdCard).setImageBitmap(bitmap)
                
                val info = withContext(Dispatchers.IO) { performOCR(bitmap) }
                
                if (info != null) {
                    // 显示识别结果到可编辑字段
                    findViewById<EditText>(R.id.etName).setText(info.name)
                    findViewById<EditText>(R.id.etIdNumber).setText(info.idNumber)
                    findViewById<EditText>(R.id.etGender).setText(info.gender)
                    findViewById<EditText>(R.id.etAddress).setText(info.address)
                    
                    findViewById<TextView>(R.id.tvStatus).text = "识别成功，请核对或修改"
                    findViewById<Button>(R.id.btnConfirm).visibility = android.view.View.VISIBLE
                    
                    LogActivity.addLog("OcrActivity", "OCR success: " + info.name)
                } else {
                    findViewById<TextView>(R.id.tvStatus).text = "识别失败，请手动输入"
                    enableManualEdit()
                }
            } catch (e: Exception) {
                findViewById<TextView>(R.id.tvStatus).text = "错误：" + e.message
                LogActivity.addLog("OcrActivity", "Error: " + e.message)
            }
        }
    }
    
    private fun performOCR(bitmap: Bitmap): IDCardInfo? {
        try {
            val client = OkHttpClient()
            
            // 获取token
            val tokenBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", API_KEY)
                .add("client_secret", SECRET_KEY)
                .build()
            val tokenRequest = Request.Builder()
                .url("https://aip.baidubce.com/oauth/2.0/token")
                .post(tokenBody)
                .build()
            
            val tokenResponse = client.newCall(tokenRequest).execute()
            val tokenJson = JSONObject(tokenResponse.body?.string() ?: "{}")
            val token = tokenJson.optString("access_token")
            
            if (token.isEmpty()) return null
            
            // OCR识别
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val imageBase64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
            
            val ocrBody = FormBody.Builder()
                .add("image", imageBase64)
                .add("id_card_side", "front")
                .build()
            val ocrRequest = Request.Builder()
                .url("https://aip.baidubce.com/rest/2.0/ocr/v1/idcard?access_token=" + token)
                .post(ocrBody)
                .build()
            
            val ocrResponse = client.newCall(ocrRequest).execute()
            val json = JSONObject(ocrResponse.body?.string() ?: "{}")
            
            if (json.has("error_code")) return null
            
            val wordsResult = json.optJSONObject("words_result") ?: return null
            
            return IDCardInfo(
                name = wordsResult.optJSONObject("姓名")?.optString("words", "") ?: "",
                idNumber = wordsResult.optJSONObject("公民身份号码")?.optString("words", "") ?: "",
                gender = wordsResult.optJSONObject("性别")?.optString("words", "") ?: "",
                nation = wordsResult.optJSONObject("民族")?.optString("words", "") ?: "",
                address = wordsResult.optJSONObject("住址")?.optString("words", "") ?: ""
            )
        } catch (e: Exception) {
            LogActivity.addLog("OcrActivity", "OCR Exception: " + e.message)
            return null
        }
    }
    
    private fun enableManualEdit() {
        findViewById<EditText>(R.id.etName).isEnabled = true
        findViewById<EditText>(R.id.etIdNumber).isEnabled = true
        findViewById<EditText>(R.id.etGender).isEnabled = true
        findViewById<EditText>(R.id.etAddress).isEnabled = true
        findViewById<Button>(R.id.btnConfirm).visibility = android.view.View.VISIBLE
        Toast.makeText(this, "已启用手动编辑", Toast.LENGTH_SHORT).show()
    }
    
    private fun saveIdentityAndProceed() {
        val name = findViewById<EditText>(R.id.etName).text.toString().trim()
        val idNumber = findViewById<EditText>(R.id.etIdNumber).text.toString().trim()
        val gender = findViewById<EditText>(R.id.etGender).text.toString().trim()
        val address = findViewById<EditText>(R.id.etAddress).text.toString().trim()
        
        if (name.isEmpty() || idNumber.isEmpty()) {
            Toast.makeText(this, "姓名和身份证号不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 保存到全局状态
        MainActivity.idCardInfo = IDCardInfo(name, idNumber, gender, "", address)
        MainActivity.idCardBitmap = capturedBitmap
        
        LogActivity.addLog("OcrActivity", "Identity saved: $name, $idNumber")
        
        // 进入人脸验证
        startActivity(Intent(this, FaceVerifyActivity::class.java))
    }
}