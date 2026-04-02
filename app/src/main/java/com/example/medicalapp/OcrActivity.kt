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
        setContentView(R.layout.activity_ocr)
        
        findViewById<TextView>(R.id.tvTitle).text = "拍照识别身份信息"
        findViewById<Button>(R.id.btnSelectImage).text = "选择身份证照片"
        findViewById<Button>(R.id.btnBack).text = "返回"
        
        findViewById<Button>(R.id.btnSelectImage).setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                PICK_IMAGE
            )
        }
        
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
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
                } ?: return@launch
                
                findViewById<ImageView>(R.id.ivIdCard).setImageBitmap(bitmap)
                
                val info = withContext(Dispatchers.IO) { performOCR(bitmap) }
                
                if (info != null) {
                    MainActivity.currentIdCardInfo = info
                    MainActivity.currentIdCardBitmap = bitmap
                    
                    findViewById<TextView>(R.id.etName).text = info.name
                    findViewById<TextView>(R.id.etIdNumber).text = info.idNumber
                    findViewById<TextView>(R.id.etGender).text = info.gender
                    findViewById<TextView>(R.id.etAddress).text = info.address
                    findViewById<TextView>(R.id.tvStatus).text = "识别成功：" + info.name
                    
                    // 显示下一步按钮
                    findViewById<Button>(R.id.btnNext).apply {
                        text = "下一步：人脸验证"
                        visibility = android.view.View.VISIBLE
                        setOnClickListener {
                            startActivity(Intent(this@OcrActivity, FaceVerifyActivity::class.java))
                        }
                    }
                } else {
                    findViewById<TextView>(R.id.tvStatus).text = "识别失败，请手动输入"
                }
            } catch (e: Exception) {
                findViewById<TextView>(R.id.tvStatus).text = "错误：" + e.message
            }
        }
    }
    
    private fun performOCR(bitmap: Bitmap): IDCardInfo? {
        try {
            val client = OkHttpClient()
            
            // 获取 Token
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
            val token = JSONObject(tokenResponse.body?.string() ?: "{}").optString("access_token")
            if (token.isEmpty()) return null
            
            // OCR 识别
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
            return null
        }
    }
}