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
        
        try {
            setContentView(R.layout.activity_face_verify)
            LogActivity.addLog("FaceVerifyActivity", "onCreate started")
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "布局加载失败: " + e.message, android.widget.Toast.LENGTH_LONG).show()
            LogActivity.addLog("FaceVerifyActivity", "setContentView error: " + e.message)
            finish()
            return
        }
        
        try {
            aliyunFaceHelper = AliyunFaceHelper()
            LogActivity.addLog("FaceVerifyActivity", "AliyunFaceHelper initialized")
            
            findViewById<TextView>(R.id.tvTitle).text = "人脸拍照识别验证"
            findViewById<Button>(R.id.btnStartCamera).text = "开始人脸拍照"
            findViewById<Button>(R.id.btnBack).text = "返回"
            findViewById<Button>(R.id.btnLogs).text = "查看日志"
            
            val info = MainActivity.currentIdCardInfo
            if (info != null) {
                findViewById<TextView>(R.id.tvInfo).text = "姓名：" + info.name + "\n身份证号：" + info.idNumber
                LogActivity.addLog("FaceVerifyActivity", "ID info loaded: " + info.name)
            } else {
                findViewById<TextView>(R.id.tvInfo).text = "警告：未找到身份信息"
                LogActivity.addLog("FaceVerifyActivity", "WARNING: No ID info found")
                android.widget.Toast.makeText(this, "请先输入身份信息", android.widget.Toast.LENGTH_LONG).show()
            }
            
            findViewById<Button>(R.id.btnStartCamera).setOnClickListener {
                LogActivity.addLog("FaceVerifyActivity", "Start camera clicked")
                startActivityForResult(
                    Intent(this, FaceCaptureActivity::class.java),
                    FACE_CAPTURE
                )
            }
            
            findViewById<Button>(R.id.btnBack).setOnClickListener { 
                LogActivity.addLog("FaceVerifyActivity", "Back clicked")
                finish() 
            }
            
            findViewById<Button>(R.id.btnLogs).setOnClickListener {
                startActivity(Intent(this, LogActivity::class.java))
            }
            
            LogActivity.addLog("FaceVerifyActivity", "onCreate completed")
        } catch (e: Exception) {
            LogActivity.addLog("FaceVerifyActivity", "init error: " + e.message)
            android.widget.Toast.makeText(this, "初始化失败: " + e.message, android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        LogActivity.addLog("FaceVerifyActivity", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode)
        
        if (requestCode == FACE_CAPTURE && resultCode == RESULT_OK) {
            val faceBitmap = FaceCaptureActivity.capturedFaceBitmap
            LogActivity.addLog("FaceVerifyActivity", "Face bitmap is null: " + (faceBitmap == null))
            
            if (faceBitmap != null) {
                LogActivity.addLog("FaceVerifyActivity", "Face captured, starting comparison")
                performFaceCompare(faceBitmap)
            } else {
                findViewById<TextView>(R.id.tvStatus).text = "拍照失败，请重试"
                LogActivity.addLog("FaceVerifyActivity", "ERROR: Face bitmap is null")
            }
        } else {
            LogActivity.addLog("FaceVerifyActivity", "Face capture cancelled or failed")
        }
    }
    
    private fun performFaceCompare(faceBitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                findViewById<TextView>(R.id.tvStatus).text = "比对中..."
                LogActivity.addLog("FaceVerifyActivity", "Starting face comparison...")
                
                val idCardBitmap = MainActivity.currentIdCardBitmap
                if (idCardBitmap == null) {
                    findViewById<TextView>(R.id.tvStatus).text = "错误：没有身份证照片"
                    LogActivity.addLog("FaceVerifyActivity", "ERROR: No ID card bitmap")
                    return@launch
                }
                
                LogActivity.addLog("FaceVerifyActivity", "Calling Aliyun API...")
                val result = withContext(Dispatchers.IO) {
                    aliyunFaceHelper.compareFaces(idCardBitmap, faceBitmap)
                }
                
                val score = result.first
                MainActivity.faceCompareScore = score
                MainActivity.faceCompareMessage = result.second
                
                LogActivity.addLog("FaceVerifyActivity", "Comparison result: score=" + score + ", msg=" + result.second)
                
                startActivity(Intent(this@FaceVerifyActivity, ResultActivity::class.java))
                
            } catch (e: Exception) {
                findViewById<TextView>(R.id.tvStatus).text = "比对错误：" + e.message
                LogActivity.addLog("FaceVerifyActivity", "Comparison error: " + e.message)
                e.printStackTrace()
            }
        }
    }
}