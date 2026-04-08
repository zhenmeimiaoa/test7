package com.example.medicalapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_result)
            LogActivity.addLog("ResultActivity", "onCreate started")
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "布局加载失败: " + e.message, android.widget.Toast.LENGTH_LONG).show()
            LogActivity.addLog("ResultActivity", "setContentView error: " + e.message)
            finish()
            return
        }
        
        try {
            val tvResult = findViewById<TextView>(R.id.tvResult)
            val tvScore = findViewById<TextView>(R.id.tvScore)
            val btnBackHome = findViewById<Button>(R.id.btnBackHome)
            val btnLogs = findViewById<Button>(R.id.btnLogs)
            
            val score = MainActivity.faceCompareScore
            // 修复：score已经是0-100的百分比，不需要乘以100
            val similarity = String.format("%.1f", score)
            
            LogActivity.addLog("ResultActivity", "Result: score=" + score + ", similarity=" + similarity + "%")
            
            // 修复：阈值应该是60（百分比），不是0.6
            if (score > 60.0) {
                tvResult.text = "人脸识别通过"
                tvResult.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                findViewById<android.view.View>(R.id.layoutResult).setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                LogActivity.addLog("ResultActivity", "PASS: similarity > 60%")
            } else {
                tvResult.text = "人脸识别不通过"
                tvResult.setTextColor(android.graphics.Color.parseColor("#F44336"))
                findViewById<android.view.View>(R.id.layoutResult).setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                LogActivity.addLog("ResultActivity", "FAIL: similarity <= 60%")
            }
            
            tvScore.text = "相似度：" + similarity + "%"
            
            btnBackHome.text = "返回首页"
            btnBackHome.setOnClickListener {
                LogActivity.addLog("ResultActivity", "Back to home clicked")
                MainActivity.currentIdCardInfo = null
                MainActivity.currentIdCardBitmap = null
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            
            btnLogs.text = "查看日志"
            btnLogs.setOnClickListener {
                startActivity(Intent(this, LogActivity::class.java))
            }
            
            LogActivity.addLog("ResultActivity", "onCreate completed")
        } catch (e: Exception) {
            LogActivity.addLog("ResultActivity", "init error: " + e.message)
            android.widget.Toast.makeText(this, "显示结果失败: " + e.message, android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}