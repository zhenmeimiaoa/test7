package com.example.medicalapp

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    
    companion object {
        // 身份信息
        var idCardInfo: IDCardInfo? = null
        var idCardBitmap: Bitmap? = null
        var faceCompareScore: Double = 0.0
        var isIdentityVerified: Boolean = false
        
        // 病症信息
        var symptomText: String = ""
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        LogActivity.addLog("MainActivity", "onCreate started")
        
        val btnIdentityVerify = findViewById<Button>(R.id.btnIdentityVerify)
        val btnSymptomAnalysis = findViewById<Button>(R.id.btnSymptomAnalysis)
        val btnLogs = findViewById<Button>(R.id.btnLogs)
        
        // 按钮一：身份验证
        btnIdentityVerify.setOnClickListener {
            LogActivity.addLog("MainActivity", "Identity verify button clicked")
            startActivity(Intent(this, IdentityMethodActivity::class.java))
        }
        
        // 按钮二：病症分析
        btnSymptomAnalysis.setOnClickListener {
            LogActivity.addLog("MainActivity", "Symptom analysis button clicked")
            if (!isIdentityVerified) {
                Toast.makeText(this, "请先完成身份验证", Toast.LENGTH_LONG).show()
                LogActivity.addLog("MainActivity", "Blocked: identity not verified")
            } else {
                startActivity(Intent(this, SymptomInputActivity::class.java))
            }
        }
        
        // 查看日志
        btnLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        
        LogActivity.addLog("MainActivity", "onCreate completed, verified=" + isIdentityVerified)
    }
    
    override fun onResume() {
        super.onResume()
        // 更新按钮状态显示
        updateButtonStatus()
        
        // 如果身份已验证，同步到简道云
        if (isIdentityVerified) {
            syncPatientToJiandaoyun()
        }
    }
    
    private fun updateButtonStatus() {
        val btnSymptomAnalysis = findViewById<Button>(R.id.btnSymptomAnalysis)
        if (isIdentityVerified) {
            btnSymptomAnalysis.text = "病症分析（已验证）"
            btnSymptomAnalysis.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
        } else {
            btnSymptomAnalysis.text = "病症分析（需先验证身份）"
            btnSymptomAnalysis.backgroundTintList = getColorStateList(android.R.color.darker_gray)
        }
    }
    
    /**
     * 同步患者信息到简道云
     */
    private fun syncPatientToJiandaoyun() {
        val info = idCardInfo ?: return
        if (!isIdentityVerified) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val helper = JiandaoyunApiHelper()

                val idCardImageFile = File(cacheDir, "id_card_image.jpg")
                val faceImageFile = File(cacheDir, "face_image.jpg")

                val result = helper.createPatient(
                    name = info.name,
                    idCard = info.idNumber,
                    gender = info.gender,
                    address = info.address,
                    faceSimilarity = faceCompareScore.toFloat(),
                    idCardImage = if (idCardImageFile.exists()) idCardImageFile else null,
                    faceImage = if (faceImageFile.exists()) faceImageFile else null
                )

                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { recordId ->
                            LogActivity.addLog("Jiandaoyun", "患者信息已同步，记录ID: $recordId")
                            Toast.makeText(this@MainActivity, "身份信息已云端备份", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            LogActivity.addLog("Jiandaoyun", "同步失败: ${error.message}")
                        }
                    )
                }
            } catch (e: Exception) {
                LogActivity.addLog("Jiandaoyun", "同步异常: ${e.message}")
            }
        }
    }
}