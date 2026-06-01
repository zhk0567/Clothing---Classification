package com.deepfashion.classifier

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityImageEnhanceBinding

class ImageEnhanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageEnhanceBinding
    private var sourceBitmap: Bitmap? = null
    private var imagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageEnhanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.enhance_reclassify)

        imagePath = intent.getStringExtra("imagePath")
        sourceBitmap = BitmapFactory.decodeFile(imagePath)
        updatePreview()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.seekBrightness.setOnSeekBarChangeListener(listener)
        binding.seekContrast.setOnSeekBarChangeListener(listener)
        binding.seekSharpen.setOnSeekBarChangeListener(listener)

        binding.btnReclassify.setOnClickListener {
            val enhanced = getEnhancedBitmap() ?: return@setOnClickListener
            try {
                val result = ClassifierProvider.classifyImage(this, enhanced, imagePath)
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
    }

    private fun updatePreview() {
        binding.imgPreview.setImageBitmap(getEnhancedBitmap())
    }

    private fun getEnhancedBitmap(): Bitmap? {
        val src = sourceBitmap ?: return null
        val brightness = binding.seekBrightness.progress / 100f
        val contrast = binding.seekContrast.progress / 100f
        val cm = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness * 50 - 25,
            0f, contrast, 0f, 0f, brightness * 50 - 25,
            0f, 0f, contrast, 0f, brightness * 50 - 25,
            0f, 0f, 0f, 1f, 0f
        ))
        val adjusted = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(adjusted)
        val paint = android.graphics.Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)

        val sharpen = binding.seekSharpen.progress
        return if (sharpen > 0) applySharpen(adjusted, sharpen / 100f) else adjusted
    }

    private fun applySharpen(bitmap: Bitmap, strength: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.copyOf()
        val amount = strength * 0.8f
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val neighbors = intArrayOf(
                    pixels[idx - w], pixels[idx + w], pixels[idx - 1], pixels[idx + 1]
                )
                var a = pixels[idx] ushr 24 and 0xFF
                var r = pixels[idx] ushr 16 and 0xFF
                var g = pixels[idx] ushr 8 and 0xFF
                var b = pixels[idx] and 0xFF
                for (n in neighbors) {
                    r -= ((r - (n ushr 16 and 0xFF)) * amount).toInt()
                    g -= ((g - (n ushr 8 and 0xFF)) * amount).toInt()
                    b -= ((b - (n and 0xFF)) * amount).toInt()
                }
                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)
                out[idx] = a shl 24 or (r shl 16) or (g shl 8) or b
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        if (bitmap != sourceBitmap) bitmap.recycle()
        return result
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}
