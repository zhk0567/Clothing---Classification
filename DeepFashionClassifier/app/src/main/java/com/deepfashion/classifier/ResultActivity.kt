package com.deepfashion.classifier

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.deepfashion.classifier.databinding.ActivityResultBinding
import java.io.File
import java.io.FileOutputStream

class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding
    private var category: String = ""
    private var confidence: Float = 0f
    private var imagePath: String? = null
    private var isFavorite: Boolean = false
    private var currentHistoryItem: HistoryItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        category = intent.getStringExtra("category") ?: "未知"
        confidence = intent.getFloatExtra("confidence", 0f)
        imagePath = intent.getStringExtra("imagePath")
        
        setupUI()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_result, menu)
        
        // 如果是历史记录，加载收藏状态并显示收藏按钮
        val fromHistory = intent.getBooleanExtra("fromHistory", false)
        if (fromHistory) {
            loadFavoriteStatus()
            updateFavoriteIcon(menu)
        } else {
            // 新识别的结果，还没有保存，不显示收藏按钮
            menu?.findItem(R.id.action_favorite)?.isVisible = false
        }
        
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val fromHistory = intent.getBooleanExtra("fromHistory", false)
        if (fromHistory) {
            updateFavoriteIcon(menu)
        }
        return super.onPrepareOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_favorite -> {
                toggleFavorite()
                true
            }
            R.id.action_share -> {
                showShareDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun loadFavoriteStatus() {
        // 尝试从历史记录中查找当前项
        val allItems = HistoryRepository.loadAll(this)
        currentHistoryItem = allItems.find { item ->
            item.category == category &&
            kotlin.math.abs(item.confidence - confidence) < 0.001f &&
            item.imagePath == imagePath
        }
        isFavorite = currentHistoryItem?.isFavorite ?: false
    }
    
    private fun updateFavoriteIcon(menu: Menu?) {
        val favoriteItem = menu?.findItem(R.id.action_favorite)
        favoriteItem?.let {
            if (isFavorite) {
                it.icon = getDrawable(R.drawable.ic_favorite_filled)
                it.title = getString(R.string.favorite_removed)
            } else {
                it.icon = getDrawable(R.drawable.ic_favorite_outline)
                it.title = getString(R.string.favorite_added)
            }
        }
    }
    
    private fun toggleFavorite() {
        val fromHistory = intent.getBooleanExtra("fromHistory", false)
        
        if (fromHistory && currentHistoryItem != null) {
            // 从历史记录切换收藏
            isFavorite = HistoryRepository.toggleFavorite(this, currentHistoryItem!!)
            // 更新当前项的收藏状态
            currentHistoryItem = HistoryItem(
                time = currentHistoryItem!!.time,
                category = currentHistoryItem!!.category,
                confidence = currentHistoryItem!!.confidence,
                imagePath = currentHistoryItem!!.imagePath,
                isFavorite = isFavorite
            )
            invalidateOptionsMenu()
        } else if (!fromHistory) {
            // 新识别的结果，先保存到历史再收藏
            try {
                val savedItem = HistoryRepository.addEntry(this, category, confidence, imagePath)
                if (savedItem != null) {
                    isFavorite = HistoryRepository.toggleFavorite(this, savedItem)
                    invalidateOptionsMenu()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showShareDialog() {
        val options = arrayOf(
            getString(R.string.share_text),
            getString(R.string.share_image)
        )
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_share_method))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareAsText()
                    1 -> shareAsImage()
                }
            }
            .show()
    }
    
    private fun shareAsText() {
        val description = intent.getStringExtra("description") ?: ""
        val shareText = "${category} - ${(confidence * 100).toInt()}%\n$description"
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }
    
    private fun shareAsImage() {
        try {
            // 获取卡片视图（只分享卡片内容，不包括按钮）
            val cardView = binding.cardResult
            
            // 测量和布局视图
            val widthSpec = View.MeasureSpec.makeMeasureSpec(
                resources.displayMetrics.widthPixels - 40, // 减去 padding
                View.MeasureSpec.EXACTLY
            )
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            cardView.measure(widthSpec, heightSpec)
            cardView.layout(0, 0, cardView.measuredWidth, cardView.measuredHeight)
            
            // 创建 Bitmap
            val bitmap = Bitmap.createBitmap(
                cardView.measuredWidth,
                cardView.measuredHeight,
                Bitmap.Config.ARGB_8888
            )
            
            // 将视图绘制到 Bitmap
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            cardView.draw(canvas)
            
            // 将 Bitmap 保存到临时文件（仅用于分享，分享后立即删除）
            val cacheDir = File(cacheDir, "share")
            cacheDir.mkdirs()
            val tempFile = File(cacheDir, "share_${System.currentTimeMillis()}.png")
            
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            
            // 使用 FileProvider 分享图片
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                tempFile
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
            
            // 分享完成后延迟删除临时文件（3秒后）
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                } catch (_: Exception) { }
            }, 3000)
            
        } catch (e: Exception) {
            Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUI() {
        val description = intent.getStringExtra("description") ?: ""

        binding.tvCategory.text = category
        binding.tvConfidence.text = "${(confidence * 100).toInt()}%"
        binding.progressConfidence.progress = (confidence * 100).toInt()

        // parse multi-line description to sections
        val map = HashMap<String, String>()
        description.split('\n').forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                map[k] = v
            }
        }

        val isZh = resources.configuration.locales[0].language.startsWith("zh")
        binding.labelStyle.text = if (isZh) "风格" else "Style"
        binding.labelScene.text = if (isZh) "场景" else "Occasions"
        binding.labelPair.text = if (isZh) "搭配" else "Pairing"
        binding.labelSeason.text = if (isZh) "季节/材质" else "Season/Fabric"
        binding.labelCare.text = if (isZh) "护理" else "Care"

        val styleText = map[if (isZh) "风格" else "Style"] ?: ""
        val sceneText = map[if (isZh) "场景" else "Occasions"] ?: ""
        val pairText = map[if (isZh) "搭配" else "Pairing"] ?: ""
        val seasonText = map[if (isZh) "季节材质" else "Season/Fabric"]
            ?: map[if (isZh) "季节/材质" else "Season/Fabric"] ?: ""
        val careText = map[if (isZh) "护理" else "Care"] ?: ""

        val placeholder = if (isZh) "暂无" else "N/A"
        binding.tvStyle.text = if (styleText.isNotBlank()) styleText else placeholder
        binding.tvScene.text = if (sceneText.isNotBlank()) sceneText else placeholder
        binding.tvPair.text = if (pairText.isNotBlank()) pairText else placeholder
        binding.tvSeason.text = if (seasonText.isNotBlank()) seasonText else placeholder
        binding.tvCare.text = if (careText.isNotBlank()) careText else placeholder

        // thumbnail
        if (!imagePath.isNullOrBlank()) {
            try {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(imagePath, opts)
                var sample = 1
                val target = 720
                while (opts.outWidth / sample > target || opts.outHeight / sample > target) {
                    sample *= 2
                }
                val opts2 = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = android.graphics.BitmapFactory.decodeFile(imagePath, opts2)
                binding.imgThumb.setImageBitmap(bmp)

                binding.imgThumb.setOnClickListener {
                    val itn = Intent(this, FullImageActivity::class.java)
                    itn.putExtra("imagePath", imagePath)
                    startActivity(itn)
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                }

                // build palette bar under thumbnail - small squares with hex labels
                bmp?.let {
                    Palette.from(it).clearFilters().generate { pal ->
                        val bar = binding.paletteBarResult
                        bar.removeAllViews()
                        val colors = mutableListOf<Int>()
                        fun add(c: Int?) { if (c != null && c != 0 && !colors.contains(c)) colors.add(c) }
                        add(pal?.vibrantSwatch?.rgb)
                        add(pal?.lightVibrantSwatch?.rgb)
                        add(pal?.darkVibrantSwatch?.rgb)
                        add(pal?.mutedSwatch?.rgb)
                        add(pal?.lightMutedSwatch?.rgb)
                        add(pal?.darkMutedSwatch?.rgb)
                        add(pal?.dominantSwatch?.rgb)
                        if (colors.isEmpty()) {
                            bar.visibility = android.view.View.GONE
                        } else {
                            bar.visibility = android.view.View.VISIBLE
                            val sizeDp = 48f
                            val density = resources.displayMetrics.density
                            val size = (sizeDp * density).toInt()
                            val marginDp = 8f
                            val margin = (marginDp * density).toInt()
                            colors.forEach { color ->
                                // Create a FrameLayout to contain square and text
                                val container = android.widget.FrameLayout(this)
                                val lp = LinearLayout.LayoutParams(size, size)
                                lp.marginEnd = margin
                                container.layoutParams = lp
                                
                                // Create colored square
                                val square = android.view.View(this)
                                square.setBackgroundColor(color)
                                val squareLp = android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                square.layoutParams = squareLp
                                container.addView(square)
                                
                                // Create hex text label
                                val hexText = android.widget.TextView(this)
                                val hex = String.format("#%06X", 0xFFFFFF and color)
                                hexText.text = hex
                                hexText.textSize = 8f // small text
                                hexText.setTextColor(android.graphics.Color.WHITE)
                                hexText.setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
                                hexText.gravity = android.view.Gravity.CENTER
                                val textLp = android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                hexText.layoutParams = textLp
                                container.addView(hexText)
                                
                                bar.addView(container)
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        val fromHistory = intent.getBooleanExtra("fromHistory", false)
        
        // 根据设置控制展示密度（精简/标准/详细三种模式）
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val density = prefs.getString("pref_result_density", "standard") ?: "standard"
        
        if (fromHistory) {
            binding.btnRetake.text = getString(R.string.back)
            // 历史详情仅展示图片/结果/置信度
            binding.sectionsContainer.visibility = android.view.View.GONE
            binding.btnSave.text = getString(R.string.save_image)
            // 从历史记录打开时，保存按钮保存图片到相册
            binding.btnSave.setOnClickListener {
                saveImageToGallery()
            }
        } else {
            // 根据密度设置显示不同内容
            when (density) {
                "compact" -> {
                    // 精简模式：只显示图片、类别、置信度，隐藏所有详细信息
                    binding.sectionsContainer.visibility = android.view.View.GONE
                }
                "standard" -> {
                    // 标准模式：显示图片、类别、置信度，以及风格和场景
                    binding.sectionsContainer.visibility = android.view.View.VISIBLE
                    binding.sectionStyle.visibility = android.view.View.VISIBLE
                    binding.sectionScene.visibility = android.view.View.VISIBLE
                    binding.sectionPair.visibility = android.view.View.GONE
                    binding.sectionSeason.visibility = android.view.View.GONE
                    binding.sectionCare.visibility = android.view.View.GONE
                }
                "detailed" -> {
                    // 详细模式：显示所有信息
                    binding.sectionsContainer.visibility = android.view.View.VISIBLE
                    binding.sectionStyle.visibility = android.view.View.VISIBLE
                    binding.sectionScene.visibility = android.view.View.VISIBLE
                    binding.sectionPair.visibility = android.view.View.VISIBLE
                    binding.sectionSeason.visibility = android.view.View.VISIBLE
                    binding.sectionCare.visibility = android.view.View.VISIBLE
                }
                else -> {
                    // 默认使用标准模式
                    binding.sectionsContainer.visibility = android.view.View.VISIBLE
                    binding.sectionStyle.visibility = android.view.View.VISIBLE
                    binding.sectionScene.visibility = android.view.View.VISIBLE
                    binding.sectionPair.visibility = android.view.View.GONE
                    binding.sectionSeason.visibility = android.view.View.GONE
                    binding.sectionCare.visibility = android.view.View.GONE
                }
            }
            // 新识别结果时，保存按钮保存到历史记录
            binding.btnSave.setOnClickListener {
                try {
                    val savedItem = HistoryRepository.addEntry(this, category, confidence, imagePath)
                    Toast.makeText(this, "已保存到历史", Toast.LENGTH_SHORT).show()
                    // 如果已收藏，同步更新
                    if (isFavorite && savedItem != null) {
                        HistoryRepository.toggleFavorite(this, savedItem)
                    }
                } catch (_: Exception) { }
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }
        
        binding.btnRetake.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun saveImageToGallery() {
        if (imagePath.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.save_image_no_image), Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // 读取原始图片
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                Toast.makeText(this, getString(R.string.save_image_failed), Toast.LENGTH_SHORT).show()
                return
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore API
                saveImageMediaStore(bitmap)
            } else {
                // Android 9 及以下使用传统方式
                saveImageLegacy(bitmap)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.save_image_failed), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveImageMediaStore(bitmap: Bitmap) {
        var outputStream: java.io.OutputStream? = null
        var uri: android.net.Uri? = null
        try {
            val fileName = "服饰识别_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/服饰识别")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                Toast.makeText(this, "保存失败：无法创建图片文件", Toast.LENGTH_LONG).show()
                bitmap.recycle()
                return
            }
            
            // 打开输出流并写入图片
            outputStream = contentResolver.openOutputStream(uri)
            if (outputStream == null) {
                // 如果无法打开输出流，删除已创建的条目
                contentResolver.delete(uri, null, null)
                Toast.makeText(this, "保存失败：无法写入图片", Toast.LENGTH_LONG).show()
                bitmap.recycle()
                return
            }
            
            // 写入图片数据
            val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            if (!success) {
                outputStream.close()
                contentResolver.delete(uri, null, null)
                Toast.makeText(this, "保存失败：图片压缩失败", Toast.LENGTH_LONG).show()
                bitmap.recycle()
                return
            }
            
            // 确保数据已刷新到磁盘
            outputStream.flush()
            outputStream.close()
            outputStream = null
            
            // 在 Android 10+ 上，将 IS_PENDING 设置为 0 以使图片可见
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                val updated = contentResolver.update(uri, updateValues, null, null)
                if (updated == 0) {
                    // 更新失败，删除已创建的条目
                    contentResolver.delete(uri, null, null)
                    Toast.makeText(this, "保存失败：无法更新图片状态", Toast.LENGTH_LONG).show()
                    bitmap.recycle()
                    return
                }
            }
            
            // 成功保存
            Toast.makeText(this, getString(R.string.save_image_success), Toast.LENGTH_SHORT).show()
            bitmap.recycle()
            
        } catch (e: SecurityException) {
            // 权限问题
            Toast.makeText(this, "保存失败：缺少存储权限", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            uri?.let { contentResolver.delete(it, null, null) }
            bitmap.recycle()
        } catch (e: Exception) {
            // 其他错误
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            uri?.let { contentResolver.delete(it, null, null) }
            bitmap.recycle()
        } finally {
            // 确保输出流已关闭
            try {
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun saveImageLegacy(bitmap: Bitmap) {
        try {
            // 检查存储是否可用
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                Toast.makeText(this, "保存失败：外部存储不可用", Toast.LENGTH_LONG).show()
                bitmap.recycle()
                return
            }
            
            val imagesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "服饰识别"
            )
            if (!imagesDir.exists()) {
                val created = imagesDir.mkdirs()
                if (!created && !imagesDir.exists()) {
                    Toast.makeText(this, "保存失败：无法创建图片目录", Toast.LENGTH_LONG).show()
                    bitmap.recycle()
                    return
                }
            }
            
            val imageFile = File(imagesDir, "服饰识别_${System.currentTimeMillis()}.jpg")
            
            // 写入图片文件
            FileOutputStream(imageFile).use { out ->
                val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                if (!success) {
                    Toast.makeText(this, "保存失败：图片压缩失败", Toast.LENGTH_LONG).show()
                    bitmap.recycle()
                    return
                }
                out.flush()
            }
            
            // 通知媒体库更新
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7.0+ 使用 MediaScannerConnection
                    var mediaScanner: android.media.MediaScannerConnection? = null
                    val client = object : android.media.MediaScannerConnection.MediaScannerConnectionClient {
                        override fun onMediaScannerConnected() {
                            mediaScanner?.scanFile(imageFile.absolutePath, "image/jpeg")
                        }
                        
                        override fun onScanCompleted(path: String?, uri: android.net.Uri?) {
                            mediaScanner?.disconnect()
                            mediaScanner = null
                        }
                    }
                    mediaScanner = android.media.MediaScannerConnection(this, client)
                    mediaScanner?.connect()
                } else {
                    // Android 6.0 及以下使用 Broadcast
                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    val fileUri = android.net.Uri.fromFile(imageFile)
                    mediaScanIntent.data = fileUri
                    sendBroadcast(mediaScanIntent)
                }
            } catch (e: Exception) {
                // 媒体库通知失败不影响保存成功
                e.printStackTrace()
            }
            
            Toast.makeText(this, getString(R.string.save_image_success), Toast.LENGTH_SHORT).show()
            bitmap.recycle()
        } catch (e: SecurityException) {
            Toast.makeText(this, "保存失败：缺少存储权限", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            bitmap.recycle()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            bitmap.recycle()
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}

