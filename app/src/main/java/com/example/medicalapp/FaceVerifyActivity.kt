package com.example.medicalapp

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.medicalapp.face.AliyunFaceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FaceVerifyActivity : AppCompatActivity() {
    
    private val FACE_CAPTURE = 101
    private lateinit var aliyunFaceHelper: AliyunFaceHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_verify)
        
        aliyunFaceHelper = AliyunFaceHelper()
        
        findViewById<TextView>(R.id.tvTitle).text = "人脸拍照识别验证"
        findViewById<Button>(R.id.btnStartCamera).text = "开始人脸拍照"
        findViewById<Button>(R.id.btnBack).text = "返回"
        
        // 显示当前身份信息
        val info = MainActivity.currentIdCardInfo
        if (info != null) {
            findViewById<TextView>(R.id.tvInfo).text = "姓名：" + info.name + "\n身份证号：" + info.idNumber
        }
        
        findViewById<Button>(R.id.btnStartCamera).setOnClickListener {
            startActivityForResult(
                Intent(this, FaceCaptureActivity::class.java),
                FACE_CAPTURE
            )
        }
        
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FACE_CAPTURE && resultCode == RESULT_OK) {
            val faceBitmap = FaceCaptureActivity.capturedFaceBitmap
            if (faceBitmap != null) {
                performFaceCompare(faceBitmap)
            } else {
                findViewById<TextView>(R.id.tvStatus).text = "拍照失败，请重试"
            }
        }
    }
    
    private fun performFaceCompare(faceBitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                findViewById<TextView>(R.id.tvStatus).text = "比对中..."
                
                val idCardBitmap = MainActivity.currentIdCardBitmap
                if (idCardBitmap == null) {
                    findViewById<TextView>(R.id.tvStatus).text = "错误：没有身份证照片"
                    return@launch
                }
                
                val result = withContext(Dispatchers.IO) {
                    aliyunFaceHelper.compareFaces(idCardBitmap, faceBitmap)
                }
                
                val score = result.first
                MainActivity.faceCompareScore = score
                MainActivity.faceCompareMessage = result.second
                
                // 跳转到结果页面
                startActivity(Intent(this@FaceVerifyActivity, ResultActivity::class.java))
                
            } catch (e: Exception) {
                findViewById<TextView>(R.id.tvStatus).text = "比对错误：" + e.message
            }
        }
    }
}