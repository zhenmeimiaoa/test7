package com.example.medicalapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        
        val tvResult = findViewById<TextView>(R.id.tvResult)
        val tvScore = findViewById<TextView>(R.id.tvScore)
        val btnBackHome = findViewById<Button>(R.id.btnBackHome)
        
        val score = MainActivity.faceCompareScore
        val similarity = String.format("%.1f", score * 100)
        
        if (score > 0.6) {
            tvResult.text = "人脸识别通过"
            tvResult.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            findViewById<android.view.View>(R.id.layoutResult).setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
        } else {
            tvResult.text = "人脸识别不通过"
            tvResult.setTextColor(android.graphics.Color.parseColor("#F44336"))
            findViewById<android.view.View>(R.id.layoutResult).setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
        }
        
        tvScore.text = "相似度：" + similarity + "%"
        
        btnBackHome.text = "返回首页"
        btnBackHome.setOnClickListener {
            // 清空数据，返回首页
            MainActivity.currentIdCardInfo = null
            MainActivity.currentIdCardBitmap = null
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}