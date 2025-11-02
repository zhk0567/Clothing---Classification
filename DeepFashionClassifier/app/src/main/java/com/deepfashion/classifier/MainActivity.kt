package com.deepfashion.classifier

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityMainBinding
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isProcessing = false

    // 图片选择器
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleSelectedImage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.btnStartCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.btnSelectFromGallery.setOnClickListener {
            if (!isProcessing) {
                openGallery()
            }
        }

        binding.btnViewResults.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun openGallery() {
        imagePickerLauncher.launch("image/*")
    }

    private fun handleSelectedImage(uri: Uri) {
        isProcessing = true
        binding.btnSelectFromGallery.isEnabled = false

        try {
            // 将图片复制到内部存储，便于长期保存与清理
            val imagesDir = java.io.File(filesDir, "images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val targetFile = java.io.File(imagesDir, "${System.currentTimeMillis()}.jpg")
            
            // 从URI读取图片
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                targetFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
            }

            // 加载图片并进行分类
            val bitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
            if (bitmap != null) {
                classifyImage(bitmap, targetFile.absolutePath)
            } else {
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
                isProcessing = false
                binding.btnSelectFromGallery.isEnabled = true
            }
        } catch (e: Exception) {
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show()
            isProcessing = false
            binding.btnSelectFromGallery.isEnabled = true
        }
    }

    private fun classifyImage(bitmap: Bitmap, imagePath: String?) {
        try {
            val classifier = DeepFashionClassifier(this)
            val result = classifier.classifyImage(bitmap)
            
            // 跳转到结果页面
            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("category", result.category)
            intent.putExtra("confidence", result.confidence)
            intent.putExtra("description", result.description)
            intent.putExtra("imagePath", imagePath)
            intent.putExtra("fromHistory", false)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            
        } catch (e: Exception) {
            Toast.makeText(this, "分类失败", Toast.LENGTH_SHORT).show()
        } finally {
            isProcessing = false
            binding.btnSelectFromGallery.isEnabled = true
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}
