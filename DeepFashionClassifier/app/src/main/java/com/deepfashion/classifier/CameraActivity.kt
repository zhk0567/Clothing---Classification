package com.deepfashion.classifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.deepfashion.classifier.databinding.ActivityCameraBinding
import android.view.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var currentSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isProcessing = false
    
    // 手势检测
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var gestureDetector: GestureDetector? = null
    private var currentZoomRatio = 1.0f
    private var baseZoomRatio = 1.0f
    
    // 对焦指示器
    private var focusIndicator: FocusIndicatorView? = null

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 根据设置选择默认摄像头
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val defaultCamera = prefs.getString("pref_default_camera", "back") ?: "back"
        currentSelector = if (defaultCamera == "front") {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupUI()
        setupGestureDetectors()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    // 拍摄界面不展示设置入口，避免重复标题

    private fun setupGestureDetectors() {
        // 添加对焦指示器（添加到与viewFinder同级的父容器）
        val parent = binding.viewFinder.parent as? ViewGroup
        focusIndicator = FocusIndicatorView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            elevation = 10f // 确保对焦框在预览上方
        }
        parent?.addView(focusIndicator)
        
        // 双指缩放检测器
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newZoom = currentZoomRatio * scaleFactor
                val zoomState = camera?.cameraInfo?.zoomState?.value
                val minZoom = zoomState?.minZoomRatio ?: 1.0f
                val maxZoom = zoomState?.maxZoomRatio ?: 10.0f
                currentZoomRatio = newZoom.coerceIn(minZoom, maxZoom)
                cameraControl?.setZoomRatio(currentZoomRatio)
                return true
            }
            
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                super.onScaleEnd(detector)
            }
        })
        
        // 双击对焦检测器
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                handleFocusAndMetering(e.x, e.y)
                return true
            }
            
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // 单击也可以触发对焦（可选）
                handleFocusAndMetering(e.x, e.y)
                return true
            }
        })
        
        binding.viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector?.onTouchEvent(event)
            gestureDetector?.onTouchEvent(event)
            true
        }
    }

    private fun handleFocusAndMetering(x: Float, y: Float) {
        val control = cameraControl ?: return
        
        // 创建 MeteringPoint，坐标需要归一化到 [0,1]
        val meteringPointFactory = binding.viewFinder.meteringPointFactory
        val point = meteringPointFactory.createPoint(x, y)
        
        // 创建对焦和测光配置
        val focusAction = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        
        try {
            control.startFocusAndMetering(focusAction)
            focusIndicator?.show(x, y)
        } catch (_: Exception) { }
    }

    private fun setupUI() {
        binding.btnCapture.setOnClickListener {
            if (!isProcessing) {
                takePhoto()
            }
        }

        binding.btnSwitchCamera?.setOnClickListener {
            switchCamera()
        }
        binding.btnHistory?.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        // 顶部返回由系统返回键或导航栏处理
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, currentSelector, preview, imageCapture
                )
                cameraControl = camera?.cameraControl
                val zoomState = camera?.cameraInfo?.zoomState?.value
                baseZoomRatio = zoomState?.zoomRatio ?: 1.0f
                // 如果切换摄像头，保持当前缩放值，否则重置
                if (currentZoomRatio == 1.0f || currentZoomRatio == baseZoomRatio) {
                    currentZoomRatio = baseZoomRatio
                } else {
                    // 确保缩放值在有效范围内
                    val minZoom = zoomState?.minZoomRatio ?: 1.0f
                    val maxZoom = zoomState?.maxZoomRatio ?: 10.0f
                    currentZoomRatio = currentZoomRatio.coerceIn(minZoom, maxZoom)
                    cameraControl?.setZoomRatio(currentZoomRatio)
                }
            } catch (_: Exception) { }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun switchCamera() {
        currentSelector = if (currentSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        // 重置缩放
        currentZoomRatio = 1.0f
        startCamera()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        isProcessing = true
        binding.btnCapture.isEnabled = false

        val photoFile = java.io.File(
            getExternalFilesDir(null),
            "deepfashion_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "拍照失败", Toast.LENGTH_SHORT).show()
                    isProcessing = false
                    binding.btnCapture.isEnabled = true
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // 将图片复制到内部存储，便于长期保存与清理
                    val safePath = try {
                        val imagesDir = java.io.File(filesDir, "images")
                        if (!imagesDir.exists()) imagesDir.mkdirs()
                        val target = java.io.File(imagesDir, "${System.currentTimeMillis()}.jpg")
                        photoFile.copyTo(target, overwrite = true)
                        target.absolutePath
                    } catch (e: Exception) {
                        photoFile.absolutePath
                    }

                    // 加载图片并进行分类
                    val bitmap = BitmapFactory.decodeFile(safePath)
                    if (bitmap != null) {
                        classifyImage(bitmap, safePath)
                    } else {
                        Toast.makeText(this@CameraActivity, "图片加载失败", Toast.LENGTH_SHORT).show()
                        isProcessing = false
                        binding.btnCapture.isEnabled = true
                    }
                }
            }
        )
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
            binding.btnCapture.isEnabled = true
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能使用此功能", Toast.LENGTH_SHORT).show()
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
