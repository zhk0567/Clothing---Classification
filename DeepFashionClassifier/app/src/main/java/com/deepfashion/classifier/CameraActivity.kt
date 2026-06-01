package com.deepfashion.classifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deepfashion.classifier.databinding.ActivityCameraBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var currentSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isProcessing = false
    private var burstMode = false

    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var gestureDetector: GestureDetector? = null
    private var currentZoomRatio = 1.0f
    private var baseZoomRatio = 1.0f
    private var focusIndicator: FocusIndicatorView? = null
    private val shutterSound = MediaActionSound()

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleGalleryImage(it) }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            startCamera()
        } else {
            Snackbar.make(binding.root, R.string.camera_permission_required, Snackbar.LENGTH_LONG)
                .setAction(R.string.open_app_settings) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupUI()
        setupGestureDetectors()
        updateGridOverlay()
    }

    override fun onResume() {
        super.onResume()
        updateGridOverlay()
    }

    private fun updateGridOverlay() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val showGrid = prefs.getBoolean("pref_camera_grid", false)
        binding.gridOverlay.visibility = if (showGrid) View.VISIBLE else View.GONE
    }

    private fun handleGalleryImage(uri: Uri) {
        lifecycleScope.launch {
            isProcessing = true
            binding.btnCapture.isEnabled = false
            try {
                val safePath = withContext(Dispatchers.IO) {
                    val imagesDir = java.io.File(filesDir, "images")
                    if (!imagesDir.exists()) imagesDir.mkdirs()
                    val target = java.io.File(imagesDir, "${System.currentTimeMillis()}.jpg")
                    contentResolver.openInputStream(uri)?.use { it.copyTo(target.outputStream()) }
                    target.absolutePath
                }
                val bitmap = BitmapFactory.decodeFile(safePath)
                if (bitmap != null) {
                    classifyAndNavigate(bitmap, safePath)
                } else {
                    Toast.makeText(this@CameraActivity, R.string.image_read_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@CameraActivity, R.string.image_read_failed, Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
                binding.btnCapture.isEnabled = true
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    private fun setupGestureDetectors() {
        val parent = binding.viewFinder.parent as? ViewGroup
        focusIndicator = FocusIndicatorView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            elevation = 10f
        }
        parent?.addView(focusIndicator)

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newZoom = currentZoomRatio * detector.scaleFactor
                val zoomState = camera?.cameraInfo?.zoomState?.value
                val minZoom = zoomState?.minZoomRatio ?: 1.0f
                val maxZoom = zoomState?.maxZoomRatio ?: 10.0f
                currentZoomRatio = newZoom.coerceIn(minZoom, maxZoom)
                cameraControl?.setZoomRatio(currentZoomRatio)
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                handleFocusAndMetering(e.x, e.y)
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
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
        val point = binding.viewFinder.meteringPointFactory.createPoint(x, y)
        val focusAction = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        try {
            control.startFocusAndMetering(focusAction)
            focusIndicator?.show(x, y)
        } catch (_: Exception) { }
    }

    private fun setupUI() {
        binding.toggleCaptureMode.check(R.id.btnSingleMode)
        binding.toggleCaptureMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                burstMode = checkedId == R.id.btnBurstMode
            }
        }

        binding.btnCapture.setOnClickListener {
            if (!isProcessing) {
                if (burstMode) startBurstCapture() else takePhoto()
            }
        }

        binding.btnSwitchCamera.setOnClickListener { switchCamera() }
        binding.btnGallery.setOnClickListener {
            if (!isProcessing) galleryLauncher.launch("image/*")
        }
        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, MainContainerActivity::class.java)
            intent.putExtra(MainContainerActivity.EXTRA_TAB, MainContainerActivity.TAB_HISTORY)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun getCaptureResolution(): Size {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return when (prefs.getString("pref_camera_resolution", "high")) {
            "low" -> Size(640, 480)
            "medium" -> Size(1280, 720)
            else -> Size(1920, 1080)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val resolution = getCaptureResolution()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(resolution)
                .build()
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, currentSelector, preview, imageCapture)
                cameraControl = camera?.cameraControl
                val zoomState = camera?.cameraInfo?.zoomState?.value
                baseZoomRatio = zoomState?.zoomRatio ?: 1.0f
                if (currentZoomRatio == 1.0f || currentZoomRatio == baseZoomRatio) {
                    currentZoomRatio = baseZoomRatio
                } else {
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
        currentZoomRatio = 1.0f
        startCamera()
    }

    private fun playShutterSound() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("pref_shutter_sound", true)) {
            try { shutterSound.play(MediaActionSound.SHUTTER_CLICK) } catch (_: Exception) { }
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        isProcessing = true
        binding.btnCapture.isEnabled = false
        playShutterSound()

        val photoFile = java.io.File(getExternalFilesDir(null), "deepfashion_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, R.string.capture_failed, Toast.LENGTH_SHORT).show()
                    resetCaptureState()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val safePath = copyToInternalStorage(photoFile)
                    val bitmap = BitmapFactory.decodeFile(safePath)
                    if (bitmap != null) {
                        lifecycleScope.launch { classifyAndNavigate(bitmap, safePath) }
                    } else {
                        Toast.makeText(this@CameraActivity, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
                        resetCaptureState()
                    }
                }
            }
        )
    }

    private fun startBurstCapture() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val count = prefs.getString("pref_burst_count", "5")?.toIntOrNull() ?: 5
        val strategy = prefs.getString("pref_burst_strategy", "best") ?: "best"
        isProcessing = true
        binding.btnCapture.isEnabled = false
        binding.tvBurstProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val results = mutableListOf<Triple<String, DeepFashionClassifier.ClassificationResult, android.graphics.Bitmap>>()
            repeat(count) { index ->
                binding.tvBurstProgress.text = getString(R.string.burst_progress, index + 1, count)
                val path = captureOnePhotoSync()
                if (path != null) {
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        val result = withContext(Dispatchers.Default) {
                            ClassifierProvider.classifyImage(this@CameraActivity, bitmap, path)
                        }
                        results.add(Triple(path, result, bitmap))
                    }
                }
                if (index < count - 1) delay(300)
            }
            binding.tvBurstProgress.visibility = View.GONE
            handleBurstResults(results, strategy)
            resetCaptureState()
        }
    }

    private suspend fun captureOnePhotoSync(): String? = withContext(Dispatchers.Main) {
        val capture = imageCapture ?: return@withContext null
        playShutterSound()
        val photoFile = java.io.File(getExternalFilesDir(null), "burst_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this@CameraActivity),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (cont.isActive) cont.resume(copyToInternalStorage(photoFile))
                    }
                }
            )
        }
    }

    private fun copyToInternalStorage(photoFile: java.io.File): String {
        return try {
            val imagesDir = java.io.File(filesDir, "images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val target = java.io.File(imagesDir, "${System.currentTimeMillis()}.jpg")
            photoFile.copyTo(target, overwrite = true)
            target.absolutePath
        } catch (_: Exception) {
            photoFile.absolutePath
        }
    }

    private fun handleBurstResults(
        results: List<Triple<String, DeepFashionClassifier.ClassificationResult, android.graphics.Bitmap>>,
        strategy: String
    ) {
        if (results.isEmpty()) {
            Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_SHORT).show()
            return
        }
        if (strategy == "all") {
            BatchResultStore.items = results.map { (path, result, _) ->
                BatchResultItem(path, result.category, result.confidence, result.description)
            }
            startActivity(Intent(this, BatchResultActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            val best = results.maxByOrNull { it.second.confidence }!!
            navigateToResult(best.second, best.first)
        }
    }

    private suspend fun classifyAndNavigate(bitmap: android.graphics.Bitmap, imagePath: String?) {
        isProcessing = true
        binding.btnCapture.isEnabled = false
        try {
            val result = withContext(Dispatchers.Default) {
                ClassifierProvider.classifyImage(this@CameraActivity, bitmap, imagePath)
            }
            navigateToResult(result, imagePath)
        } catch (_: Exception) {
            Toast.makeText(this@CameraActivity, R.string.classify_failed, Toast.LENGTH_SHORT).show()
        } finally {
            resetCaptureState()
        }
    }

    private fun navigateToResult(result: DeepFashionClassifier.ClassificationResult, imagePath: String?) {
        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra("category", result.category)
        intent.putExtra("confidence", result.confidence)
        intent.putExtra("description", result.description)
        intent.putExtra("imagePath", imagePath)
        intent.putExtra("fromHistory", false)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun resetCaptureState() {
        isProcessing = false
        binding.btnCapture.isEnabled = true
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        shutterSound.release()
    }
}
