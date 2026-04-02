package com.example.medicalapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceCaptureActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: Button
    private lateinit var tvHint: TextView
    
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        // 静态变量传递 Bitmap（避免 Intent 大小限制）
        var capturedFaceBitmap: Bitmap? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_capture)
        
        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        tvHint = findViewById(R.id.tvHint)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // 清空之前的 Bitmap
        capturedFaceBitmap = null
        
        if (checkPermissions()) {
            startCamera()
        }
        
        btnCapture.setOnClickListener {
            takePhoto()
        }
    }
    
    private fun checkPermissions(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
            false
        } else {
            true
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                
                tvHint.text = "Please look at camera and tap Capture"
                
            } catch (exc: Exception) {
                tvHint.text = "Camera error: "
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        btnCapture.isEnabled = false
        tvHint.text = "Capturing..."

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    scope.launch {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            
                            // 压缩图片避免内存问题
                            val bitmap = withContext(Dispatchers.Default) {
                                val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                // 压缩到 640x480 左右
                                val scaled = Bitmap.createScaledBitmap(original, 640, 480, true)
                                if (original != scaled) original.recycle()
                                scaled
                            }
                            
                            image.close()
                            
                            // 保存到静态变量
                            capturedFaceBitmap = bitmap
                            
                            withContext(Dispatchers.Main) {
                                setResult(RESULT_OK)
                                finish()
                            }
                            
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                tvHint.text = "Error: "
                                btnCapture.isEnabled = true
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    tvHint.text = "Capture failed: "
                    btnCapture.isEnabled = true
                }
            }
        )
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scope.cancel()
    }
}
