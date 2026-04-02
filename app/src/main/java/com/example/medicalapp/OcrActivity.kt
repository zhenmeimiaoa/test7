package com.example.medicalapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.medicalapp.model.IDCardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class OcrActivity : AppCompatActivity() {
    
    private val PICK_IMAGE = 100
    private val API_KEY = "Su4BMNAumYZWBzJbuiL1wASF"
    private val SECRET_KEY = "2yw7FNQ3EvobHqy41ZxIoTnLQYcVW83K"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_ocr)
            LogActivity.addLog("OcrActivity", "onCreate started")
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "布局加载失败: " + e.message, android.widget.Toast.LENGTH_LONG).show()
            LogActivity.addLog("OcrActivity", "setContentView error: " + e.message)
            finish()
            return
        }
        
        try {
            findViewById<TextView>(R.id.tvTitle).text = "拍照识别身份信息"
            findViewById<Button>(R.id.btnSelectImage).text = "选择身份证照片"
            findViewById<Button>(R.id.btnBack).text = "返回"
            findViewById<Button>(R.id.btnLogs).text = "查看日志"
            
            findViewById<Button>(R.id.btnSelectImage).setOnClickListener {
                LogActivity.addLog("OcrActivity", "Select image clicked")
                startActivityForResult(
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                    PICK_IMAGE
                )
            }
            
            findViewById<Button>(R.id.btnBack).setOnClickListener { 
                LogActivity.addLog("OcrActivity", "Back clicked")
                finish() 
            }
            
            findViewById<Button>(R.id.btnLogs).setOnClickListener {
                startActivity(Intent(this, LogActivity::class.java))
            }
            
            LogActivity.addLog("OcrActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            LogActivity.addLog("OcrActivity", "init error: " + e.message)
            android.widget.Toast.makeText(this, "初始化失败: " + e.message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        LogActivity.addLog("OcrActivity", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode)
        
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            data.data?.let { 
                LogActivity.addLog("OcrActivity", "Image selected: " + it.toString())
                processImage(it) 
            }
        } else {
            LogActivity.addLog("OcrActivity", "Image selection cancelled or failed")
        }
    }
    
    private fun processImage(imageUri: Uri) {
        lifecycleScope.launch {
            try {
                findViewById<TextView>(R.id.tvStatus).text = "识别中..."
                LogActivity.addLog("OcrActivity", "Processing image...")
                
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
                } 
                
                if (bitmap == null) {
                    findViewById<TextView>(R.id.tvStatus).text = "加载图片失败"
                    LogActivity.addLog("OcrActivity", "Bitmap is null")
                    return@launch
                }
                
                findViewById<ImageView>(R.id.ivIdCard).setImageBitmap(bitmap)
                LogActivity.addLog("OcrActivity", "Bitmap loaded, size: " + bitmap.width + "x" + bitmap.height)
                
                val info = withContext(Dispatchers.IO) { performOCR(bitmap) }
                
                if (info != null) {
                    MainActivity.currentIdCardInfo = info
                    MainActivity.currentIdCardBitmap = bitmap
                    
                    findViewById<TextView>(R.id.etName).text = info.name
                    findViewById<TextView>(R.id.etIdNumber).text = info.idNumber
                    findViewById<TextView>(R.id.etGender).text = info.gender
                    findViewById<TextView>(R.id.etAddress).text = info.address
                    findViewById<TextView>(R.id.tvStatus).text = "识别成功：" + info.name
                    LogActivity.addLog("OcrActivity", "OCR success: " + info.name)
                    
                    findViewById<Button>(R.id.btnNext).apply {
                        text = "下一步：人脸验证"
                        visibility = android.view.View.VISIBLE
                        setOnClickListener {
                            LogActivity.addLog("OcrActivity", "Next clicked, starting FaceVerifyActivity")
                            startActivity(Intent(this@OcrActivity, FaceVerifyActivity::class.java))
                        }
                    }
                } else {
                    findViewById<TextView>(R.id.tvStatus).text = "识别失败，请手动输入"
                    LogActivity.addLog("OcrActivity", "OCR failed")
                }
            } catch (e: Exception) {
                findViewById<TextView>(R.id.tvStatus).text = "错误：" + e.message
                LogActivity.addLog("OcrActivity", "Process error: " + e.message)
                e.printStackTrace()
            }
        }
    }
    
    private fun performOCR(bitmap: Bitmap): IDCardInfo? {
        try {
            LogActivity.addLog("OcrActivity", "Starting OCR...")
            val client = OkHttpClient()
            
            val tokenBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", API_KEY)
                .add("client_secret", SECRET_KEY)
                .build()
            val tokenRequest = Request.Builder()
                .url("https://aip.baidubce.com/oauth/2.0/token")
                .post(tokenBody)
                .build()
            
            LogActivity.addLog("OcrActivity", "Getting token...")
            val tokenResponse = client.newCall(tokenRequest).execute()
            val tokenJson = JSONObject(tokenResponse.body?.string() ?: "{}")
            val token = tokenJson.optString("access_token")
            
            if (token.isEmpty()) {
                LogActivity.addLog("OcrActivity", "Token is empty")
                return null
            }
            LogActivity.addLog("OcrActivity", "Token obtained")
            
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
            
            LogActivity.addLog("OcrActivity", "Calling OCR API...")
            val ocrResponse = client.newCall(ocrRequest).execute()
            val json = JSONObject(ocrResponse.body?.string() ?: "{}")
            
            if (json.has("error_code")) {
                LogActivity.addLog("OcrActivity", "OCR API error: " + json.toString())
                return null
            }
            
            val wordsResult = json.optJSONObject("words_result") 
            if (wordsResult == null) {
                LogActivity.addLog("OcrActivity", "No words_result")
                return null
            }
            
            val name = wordsResult.optJSONObject("姓名")?.optString("words", "") ?: ""
            val idNumber = wordsResult.optJSONObject("公民身份号码")?.optString("words", "") ?: ""
            val gender = wordsResult.optJSONObject("性别")?.optString("words", "") ?: ""
            val nation = wordsResult.optJSONObject("民族")?.optString("words", "") ?: ""
            val address = wordsResult.optJSONObject("住址")?.optString("words", "") ?: ""
            
            LogActivity.addLog("OcrActivity", "Parsed: name=" + name + ", id=" + idNumber)
            
            if (name.isEmpty() && idNumber.isEmpty()) return null
            
            return IDCardInfo(name=name, idNumber=idNumber, gender=gender, nation=nation, address=address)
        } catch (e: Exception) {
            LogActivity.addLog("OcrActivity", "OCR Exception: " + e.message)
            e.printStackTrace()
            return null
        }
    }
}