package com.example.medicalapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.medicalapp.model.IDCardInfo

class ManualInputActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_input)
        
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            val name = findViewById<EditText>(R.id.etName).text.toString().trim()
            val idNumber = findViewById<EditText>(R.id.etIdNumber).text.toString().trim()
            
            if (name.isEmpty() || idNumber.isEmpty()) {
                android.widget.Toast.makeText(this, "请填写姓名和身份证号", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            MainActivity.currentIdCardInfo = IDCardInfo(
                name = name,
                idNumber = idNumber,
                gender = findViewById<EditText>(R.id.etGender).text.toString().trim(),
                address = findViewById<EditText>(R.id.etAddress).text.toString().trim()
            )
            MainActivity.currentIdCardBitmap = null
            
            startActivity(Intent(this, FaceVerifyActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }
}