package com.example.medicalapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.medicalapp.model.IDCardInfo

class MainActivity : AppCompatActivity() {

    companion object {
        var currentIdCardInfo: IDCardInfo? = null
        var currentIdCardBitmap: android.graphics.Bitmap? = null
        var faceCompareScore: Double = 0.0
        var faceCompareMessage: String = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        currentIdCardInfo = null
        currentIdCardBitmap = null
        faceCompareScore = 0.0
        faceCompareMessage = ""
        
        findViewById<TextView>(R.id.tvTitle).text = "就医数据采集系统"
        findViewById<Button>(R.id.btnManualInput).text = "手动输入身份信息"
        findViewById<Button>(R.id.btnOCR).text = "拍照识别身份信息"
        findViewById<Button>(R.id.btnNFC).text = "NFC识别身份信息"
        findViewById<Button>(R.id.btnLogs).text = "查看日志"
        
        findViewById<Button>(R.id.btnManualInput).setOnClickListener {
            startActivity(Intent(this, ManualInputActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnOCR).setOnClickListener {
            startActivity(Intent(this, OcrActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnNFC).setOnClickListener {
            android.widget.Toast.makeText(this, "NFC功能开发中", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnLogs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }
}