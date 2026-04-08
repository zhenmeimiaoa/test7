package com.example.medicalapp

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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
        
        LogActivity.addLog("FaceVerifyActivity", "onCreate started")
        
        aliyunFaceHelper = AliyunFaceHelper()
        
        val tvInfo = findViewById<TextView>(R.id.tvInfo)
        val btnStartCamera = findViewById<Button>(R.id.btnStartCamera)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnLogs = findViewById<Button>(R.id.btnLogs)
        
        val info = MainActivity.idCardInfo
        if (info != null) {
            tvInfo.text = "姓名：${info.name}\n身份证号：${info.idNumber}"
            LogActivity.addLog("FaceVerifyActivity", "ID info loaded: " + info.name)
        } else {
            tvInfo.text = "错误：未找到身份信息"
            LogActivity.addLog("FaceVerifyActivity", "ERROR: No ID info")
            Toast.makeText(this, "请先录入身份信息", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        btnStartCamera.setOnClickListener {
            startActivityForResult(
                Intent(this, FaceCaptureActivity::class.java),
                FACE_CAPTURE
            )
        }
        
        // 保存按钮（验证通过后显示）
        btnSave.visibility = android.view.View.GONE
        btnSave.setOnClickListener {
            saveIdentityToLog()
        }
        
        btnBack.setOnClickListener { finish() }
        
        btnLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        LogActivity.addLog("FaceVerifyActivity", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        
        if (requestCode == FACE_CAPTURE && resultCode == RESULT_OK) {
            val faceBitmap = FaceCaptureActivity.capturedFaceBitmap
            
            if (faceBitmap != null) {
                performFaceCompare(faceBitmap)
            } else {
                findViewById<TextView>(R.id.tvStatus).text = "拍照失败，请重试"
                LogActivity.addLog("FaceVerifyActivity", "ERROR: Face bitmap is null")
            }
        }
    }
    
    private fun performFaceCompare(faceBitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                findViewById<TextView>(R.id.tvStatus).text = "比对中..."
                
                val idCardBitmap = MainActivity.idCardBitmap
                if (idCardBitmap == null) {
                    findViewById<TextView>(R.id.tvStatus).text = "错误：没有身份证照片"
                    return@launch
                }
                
                val result = withContext(Dispatchers.IO) {
                    aliyunFaceHelper.compareFaces(idCardBitmap, faceBitmap)
                }
                
                val score = result.first
                MainActivity.faceCompareScore = score
                
                LogActivity.addLog("FaceVerifyActivity", "Comparison result: score=$score")
                
                val similarity = String.format("%.1f", score)
                findViewById<TextView>(R.id.tvStatus).text = "相似度：$similarity%"
                
                if (score > 60.0) {
                    // 验证通过，显示保存按钮
                    findViewById<Button>(R.id.btnSave).visibility = android.view.View.VISIBLE
                    findViewById<Button>(R.id.btnSave).text = "✅ 验证通过，保存身份信息"
                    Toast.makeText(this@FaceVerifyActivity, "人脸识别通过", Toast.LENGTH_SHORT).show()
                } else {
                    findViewById<TextView>(R.id.tvStatus).text = "相似度：$similarity%（未通过，请重试）"
                    Toast.makeText(this@FaceVerifyActivity, "验证未通过，请重新拍照", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                findViewById<TextView>(R.id.tvStatus).text = "比对错误：" + e.message
                LogActivity.addLog("FaceVerifyActivity", "Comparison error: " + e.message)
            }
        }
    }
    
    private fun saveIdentityToLog() {
        val info = MainActivity.idCardInfo
        if (info != null) {
            MainActivity.isIdentityVerified = true
            
            // 保存到日志（后续改为上传数据库）
            val logEntry = """
                【身份信息保存】
                姓名：${info.name}
                身份证号：${info.idNumber}
                性别：${info.gender}
                地址：${info.address}
                人脸相似度：${String.format("%.1f", MainActivity.faceCompareScore)}%
                验证时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())}
                ====================
            """.trimIndent()
            
            LogActivity.addLog("IdentitySave", logEntry)
            
            Toast.makeText(this, "身份信息已保存", Toast.LENGTH_SHORT).show()
            
            // 返回首页
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}
