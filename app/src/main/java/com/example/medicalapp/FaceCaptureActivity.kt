package com.example.medicalapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.content.Intent
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
import java.io.ByteArrayOutputStream
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
        // 锟斤拷态锟斤拷锟斤拷锟斤拷锟斤拷 Bitmap锟斤拷锟斤拷锟斤拷 Intent 锟斤拷小锟斤拷锟狡ｏ拷
        var capturedFaceBitmap: Bitmap? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_capture)
        
        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        tvHint = findViewById(R.id.tvHint)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // 锟斤拷锟街帮拷锟?Bitmap
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
                tvHint.text = "Camera error: ${exc.message}"
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
                            
                            // 鍘嬬缉鍥惧儚浠ュ噺灏戝唴瀛樹娇鐢?                            val bitmap = withContext(Dispatchers.Default) {
                                val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                // 鍘嬬缉鍒?640x480 澶у皬
                                val scaled = Bitmap.createScaledBitmap(original, 640, 480, true)
                                if (original != scaled) original.recycle()
                                scaled
                            }
                            
                            image.close()
                            
                            // 淇濆瓨鍒伴潤鎬佸彉閲?                            capturedFaceBitmap = bitmap
                            
                            // 鍒涘缓杩斿洖鎰忓浘
                            val intent = Intent()
                            intent.putExtra("face_bitmap", bitmap)
                            setResult(RESULT_OK, intent)
                            finish()
                            
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                tvHint.text = "Error: ${e.message}"
                                btnCapture.isEnabled = true
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    tvHint.text = "Capture failed: ${exception.message}"
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

