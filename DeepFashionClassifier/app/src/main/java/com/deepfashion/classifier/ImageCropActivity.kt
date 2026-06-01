package com.deepfashion.classifier

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityImageCropBinding

class ImageCropActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageCropBinding
    private var sourceBitmap: Bitmap? = null
    private var imagePath: String? = null
    private val displayMatrix = Matrix()
    private val imageBounds = RectF()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageCropBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.crop_reclassify)

        imagePath = intent.getStringExtra("imagePath")
        if (imagePath.isNullOrBlank()) {
            finish()
            return
        }
        sourceBitmap = BitmapFactory.decodeFile(imagePath)
        binding.imgCrop.setImageBitmap(sourceBitmap)
        binding.imgCrop.scaleType = android.widget.ImageView.ScaleType.MATRIX

        binding.cropContainer.viewTreeObserver.addOnGlobalLayoutListener {
            updateImageMatrix()
        }

        binding.btnRecrop.setOnClickListener {
            val bmp = sourceBitmap ?: return@setOnClickListener
            val cropped = cropFromOverlay(bmp) ?: return@setOnClickListener
            reclassify(cropped)
        }
    }

    private fun updateImageMatrix() {
        val bmp = sourceBitmap ?: return
        val viewW = binding.imgCrop.width.toFloat()
        val viewH = binding.imgCrop.height.toFloat()
        if (viewW <= 0 || viewH <= 0) return

        displayMatrix.reset()
        val scale = minOf(viewW / bmp.width, viewH / bmp.height)
        val dx = (viewW - bmp.width * scale) / 2f
        val dy = (viewH - bmp.height * scale) / 2f
        displayMatrix.postScale(scale, scale)
        displayMatrix.postTranslate(dx, dy)
        binding.imgCrop.imageMatrix = displayMatrix

        imageBounds.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        displayMatrix.mapRect(imageBounds)

        val overlayLeft = binding.cropOverlay.left.toFloat()
        val overlayTop = binding.cropOverlay.top.toFloat()
        binding.cropOverlay.setImageBounds(
            imageBounds.left - overlayLeft,
            imageBounds.top - overlayTop,
            imageBounds.right - overlayLeft,
            imageBounds.bottom - overlayTop
        )
    }

    private fun cropFromOverlay(bitmap: Bitmap): Bitmap? {
        val cropInView = binding.cropOverlay.getCropRectInView()
        val overlayLeft = binding.cropOverlay.left.toFloat()
        val overlayTop = binding.cropOverlay.top.toFloat()
        cropInView.offset(overlayLeft, overlayTop)

        val inverse = Matrix()
        if (!displayMatrix.invert(inverse)) return null

        val pts = floatArrayOf(cropInView.left, cropInView.top, cropInView.right, cropInView.bottom)
        inverse.mapPoints(pts)

        var left = minOf(pts[0], pts[2]).toInt().coerceIn(0, bitmap.width - 1)
        var top = minOf(pts[1], pts[3]).toInt().coerceIn(0, bitmap.height - 1)
        var right = maxOf(pts[0], pts[2]).toInt().coerceIn(left + 1, bitmap.width)
        var bottom = maxOf(pts[1], pts[3]).toInt().coerceIn(top + 1, bitmap.height)

        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(bitmap, left, top, w, h)
    }

    private fun reclassify(bitmap: Bitmap) {
        try {
            val result = ClassifierProvider.classifyImage(this, bitmap, imagePath)
            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("category", result.category)
            intent.putExtra("confidence", result.confidence)
            intent.putExtra("description", result.description)
            intent.putExtra("imagePath", imagePath)
            intent.putExtra("fromHistory", false)
            startActivity(intent)
            finish()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.classify_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}
