package com.deepfashion.classifier

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.deepfashion.classifier.databinding.ActivityHistoryBinding
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private var allItems: List<HistoryItem> = emptyList()
    private var filteredItems: List<HistoryItem> = emptyList()
    private lateinit var adapter: HistoryAdapter
    
    // 筛选状态
    private var selectedCategories: Set<String> = emptySet()
    private var minConfidence: Int = 0
    private var timeFilter: TimeRange = TimeRange.ALL
    private var searchQuery: String = ""
    private var showFavoritesOnly: Boolean = false

    private enum class TimeRange {
        ALL, TODAY, WEEK, MONTH
    }

    // DeepFashion 50个类别（与DeepFashionClassifier.kt保持一致）
    private val categories = listOf(
        "Anorak", "Blazer", "Blouse", "Bomber", "Button-Down", "Cardigan", "Flannel", "Halter",
        "Henley", "Hoodie", "Jacket", "Jersey", "Parka", "Peacoat", "Poncho", "Sweater", "Tank",
        "Tee", "Top", "Turtleneck", "Capris", "Chinos", "Culottes", "Cutoffs", "Gauchos", "Jeans",
        "Jeggings", "Jodhpurs", "Joggers", "Leggings", "Sarong", "Shorts", "Skirt", "Sweatpants",
        "Sweatshorts", "Trunks", "Caftan", "Cape", "Coat", "Coverup", "Dress", "Jumpsuit", "Kaftan",
        "Kimono", "Nightdress", "Onesie", "Robe", "Romper", "Shirtdress", "Sundress"
    )
    
    private val categoryChinese = mapOf(
        "Anorak" to "冲锋衣", "Blazer" to "西装外套", "Blouse" to "女式衬衫", "Bomber" to "飞行员夹克",
        "Button-Down" to "纽扣衫", "Cardigan" to "开衫", "Flannel" to "法兰绒衬衫", "Halter" to "挂脖上衣",
        "Henley" to "半开领衫", "Hoodie" to "连帽衫", "Jacket" to "夹克", "Jersey" to "运动衫",
        "Parka" to "派克大衣", "Peacoat" to "双排扣大衣", "Poncho" to "斗篷", "Sweater" to "毛衣",
        "Tank" to "背心", "Tee" to "T恤", "Top" to "上衣", "Turtleneck" to "高领衫",
        "Capris" to "七分裤", "Chinos" to "卡其裤", "Culottes" to "阔腿短裤", "Cutoffs" to "牛仔短裤",
        "Gauchos" to "高乔裤", "Jeans" to "牛仔裤", "Jeggings" to "打底裤", "Jodhpurs" to "马裤",
        "Joggers" to "慢跑裤", "Leggings" to "紧身裤", "Sarong" to "纱笼", "Shorts" to "短裤",
        "Skirt" to "裙子", "Sweatpants" to "运动裤", "Sweatshorts" to "运动短裤", "Trunks" to "泳裤",
        "Caftan" to "长袍", "Cape" to "斗篷", "Coat" to "外套", "Coverup" to "罩衫",
        "Dress" to "连衣裙", "Jumpsuit" to "连体衣", "Kaftan" to "长袍", "Kimono" to "和服",
        "Nightdress" to "睡裙", "Onesie" to "连体服", "Robe" to "长袍", "Romper" to "连体短裤",
        "Shirtdress" to "衬衫裙", "Sundress" to "太阳裙"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = HistoryAdapter(this, filteredItems)
        binding.listView.adapter = adapter
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 点击根布局时取消搜索框焦点
        binding.root.setOnClickListener {
            hideKeyboard()
        }
        
        setupSearchAndFilters()
        loadHistory()
    }
    
    override fun onResume() {
        super.onResume()
        // 当从详情页返回时，重新加载数据以反映收藏状态的变化
        loadHistory()
    }
    
    private fun setupSearchAndFilters() {
        // 搜索框
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
        })
        
        binding.editSearch.setOnEditorActionListener { _, _, _ ->
            // 隐藏键盘并取消焦点
            hideKeyboard()
            applyFilters()
            true
        }
        
        // 点击列表空白区域时取消搜索框焦点
        binding.listView.setOnTouchListener { _, _ ->
            hideKeyboard()
            false
        }
        
        // 点击筛选按钮时取消搜索框焦点
        binding.btnFilterCategory.setOnClickListener {
            hideKeyboard()
            showCategoryFilterDialog()
        }
        
        binding.btnFilterConfidence.setOnClickListener {
            hideKeyboard()
            showConfidenceFilterDialog()
        }
        
        binding.btnFilterTime.setOnClickListener {
            hideKeyboard()
            showTimeFilterDialog()
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editSearch.windowToken, 0)
        binding.editSearch.clearFocus()
    }
    
    private fun showCategoryFilterDialog() {
        val isZh = resources.configuration.locales[0].language.startsWith("zh")
        val items = categories.map { category ->
            val displayName = if (isZh) categoryChinese[category] ?: category else category
            displayName to (category in selectedCategories)
        }.toTypedArray()
        
        val selected = BooleanArray(items.size) { items[it].second }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_by_category))
            .setMultiChoiceItems(
                items.map { it.first }.toTypedArray(),
                selected
            ) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedCategories = categories.filterIndexed { index, _ -> selected[index] }.toSet()
                updateFilterChips()
                applyFilters()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showConfidenceFilterDialog() {
        val items = arrayOf(
            getString(R.string.filter_time_all),
            "≥ 50%", "≥ 60%", "≥ 70%", "≥ 80%", "≥ 90%"
        )
        var selectedIndex = when (minConfidence) {
            0 -> 0
            50 -> 1
            60 -> 2
            70 -> 3
            80 -> 4
            90 -> 5
            else -> 0
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_by_confidence))
            .setSingleChoiceItems(items, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                minConfidence = when (selectedIndex) {
                    0 -> 0
                    1 -> 50
                    2 -> 60
                    3 -> 70
                    4 -> 80
                    5 -> 90
                    else -> 0
                }
                updateFilterChips()
                applyFilters()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showTimeFilterDialog() {
        val items = arrayOf(
            getString(R.string.filter_time_all),
            getString(R.string.filter_time_today),
            getString(R.string.filter_time_week),
            getString(R.string.filter_time_month)
        )
        var selectedIndex = timeFilter.ordinal
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_by_time))
            .setSingleChoiceItems(items, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                timeFilter = TimeRange.values()[selectedIndex]
                updateFilterChips()
                applyFilters()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun updateFilterChips() {
        binding.chipGroupFilters.removeAllViews()
        
        // 收藏筛选Chip
        if (showFavoritesOnly) {
            val chip = createChip(getString(R.string.filter_favorites_only)) {
                showFavoritesOnly = false
                // 更新菜单图标
                invalidateOptionsMenu()
                updateFilterChips()
                applyFilters()
            }
            binding.chipGroupFilters.addView(chip)
        }
        
        // 类别筛选Chip
        if (selectedCategories.isNotEmpty()) {
            val isZh = resources.configuration.locales[0].language.startsWith("zh")
            val categoryNames = selectedCategories.map { category ->
                if (isZh) categoryChinese[category] ?: category else category
            }.joinToString(", ")
            val chip = createChip("类别: $categoryNames") {
                selectedCategories = emptySet()
                updateFilterChips()
                applyFilters()
            }
            binding.chipGroupFilters.addView(chip)
        }
        
        // 置信度筛选Chip
        if (minConfidence > 0) {
            val chip = createChip("置信度 ≥ ${minConfidence}%") {
                minConfidence = 0
                updateFilterChips()
                applyFilters()
            }
            binding.chipGroupFilters.addView(chip)
        }
        
        // 时间筛选Chip
        if (timeFilter != TimeRange.ALL) {
            val timeLabel = when (timeFilter) {
                TimeRange.TODAY -> getString(R.string.filter_time_today)
                TimeRange.WEEK -> getString(R.string.filter_time_week)
                TimeRange.MONTH -> getString(R.string.filter_time_month)
                else -> ""
            }
            val chip = createChip("时间: $timeLabel") {
                timeFilter = TimeRange.ALL
                updateFilterChips()
                applyFilters()
            }
            binding.chipGroupFilters.addView(chip)
        }
        
        // 搜索Chip
        if (searchQuery.isNotEmpty()) {
            val chip = createChip("搜索: $searchQuery") {
                searchQuery = ""
                binding.editSearch.setText("")
                updateFilterChips()
                applyFilters()
            }
            binding.chipGroupFilters.addView(chip)
        }
        
        // 清除所有筛选
        if (selectedCategories.isNotEmpty() || minConfidence > 0 || timeFilter != TimeRange.ALL || searchQuery.isNotEmpty() || showFavoritesOnly) {
            val chip = createChip(getString(R.string.filter_clear)) {
                selectedCategories = emptySet()
                minConfidence = 0
                timeFilter = TimeRange.ALL
                searchQuery = ""
                showFavoritesOnly = false
                binding.editSearch.setText("")
                // 更新菜单图标
                invalidateOptionsMenu()
                updateFilterChips()
                applyFilters()
            }
            binding.chipGroupFilters.addView(chip)
        }
    }
    
    private fun createChip(text: String, onClose: () -> Unit): Chip {
        return Chip(this).apply {
            this.text = text
            isCloseIconVisible = true
            setOnCloseIconClickListener { onClose() }
            setEnsureMinTouchTargetSize(false)
        }
    }
    
    
    private fun applyFilters() {
        filteredItems = allItems.filter { item ->
            // 收藏筛选
            val matchFavorite = !showFavoritesOnly || item.isFavorite
            
            // 类别筛选
            var matchCategory = selectedCategories.isEmpty() || item.category in selectedCategories
            
            // 置信度筛选
            val matchConfidence = (item.confidence * 100).toInt() >= minConfidence
            
            // 时间筛选
            val matchTime = when (timeFilter) {
                TimeRange.ALL -> true
                TimeRange.TODAY -> isToday(item.time)
                TimeRange.WEEK -> isWithinWeek(item.time)
                TimeRange.MONTH -> isWithinMonth(item.time)
            }
            
            // 搜索筛选（搜索类别或时间）
            val matchSearch = searchQuery.isEmpty() || 
                    item.category.contains(searchQuery, ignoreCase = true) ||
                    item.time.contains(searchQuery, ignoreCase = true) ||
                    (categoryChinese[item.category]?.contains(searchQuery, ignoreCase = true) == true)
            
            matchFavorite && matchCategory && matchConfidence && matchTime && matchSearch
        }
        
        adapter.update(filteredItems)
        updateFilterChips()
        
        if (filteredItems.isEmpty() && allItems.isNotEmpty()) {
            Toast.makeText(this, getString(R.string.no_results), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun isToday(timeStr: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val itemDate = sdf.format(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timeStr) ?: Date())
            val today = sdf.format(Date())
            itemDate == today
        } catch (_: Exception) {
            false
        }
    }
    
    private fun isWithinWeek(timeStr: String): Boolean {
        return try {
            val itemDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timeStr) ?: return false
            val now = Date()
            val diff = now.time - itemDate.time
            diff >= 0 && diff <= 7 * 24 * 60 * 60 * 1000L
        } catch (_: Exception) {
            false
        }
    }
    
    private fun isWithinMonth(timeStr: String): Boolean {
        return try {
            val itemDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timeStr) ?: return false
            val now = Date()
            val diff = now.time - itemDate.time
            diff >= 0 && diff <= 30 * 24 * 60 * 60 * 1000L
        } catch (_: Exception) {
            false
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                true
            }
            R.id.action_show_favorites -> {
                showFavoritesOnly = !showFavoritesOnly
                item.isChecked = showFavoritesOnly
                // 更新图标
                updateFavoriteMenuIcon(item, showFavoritesOnly)
                applyFilters()
                updateFilterChips()
                true
            }
            R.id.action_clear_history -> {
                AlertDialog.Builder(this)
                    .setTitle("清空历史")
                    .setMessage("将删除所有历史记录和已保存的识别图片，确定继续？")
                    .setPositiveButton("清空") { _, _ ->
                        HistoryRepository.clearAll(this)
                        loadHistory()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val favoriteItem = menu?.findItem(R.id.action_show_favorites)
        favoriteItem?.isChecked = showFavoritesOnly
        favoriteItem?.let { updateFavoriteMenuIcon(it, showFavoritesOnly) }
        return super.onPrepareOptionsMenu(menu)
    }
    
    private fun updateFavoriteMenuIcon(item: MenuItem, isActive: Boolean) {
        if (isActive) {
            item.icon = getDrawable(R.drawable.ic_favorite_filled)
            item.title = getString(R.string.filter_favorites_only)
        } else {
            item.icon = getDrawable(R.drawable.ic_favorite_outline)
            item.title = getString(R.string.favorite)
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun loadHistory() {
        allItems = HistoryRepository.loadAll(this)
        applyFilters()

        binding.listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            hideKeyboard() // 点击列表项时取消搜索框焦点
            val item = filteredItems[position]
            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("category", item.category)
            intent.putExtra("confidence", item.confidence)
            intent.putExtra("description", "历史记录")
            if (!item.imagePath.isNullOrBlank()) {
                intent.putExtra("imagePath", item.imagePath)
            }
            intent.putExtra("fromHistory", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val item = filteredItems[position]
            AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定删除这条历史记录吗？")
                .setPositiveButton("删除") { _, _ ->
                    HistoryRepository.deleteEntry(this, item)
                    loadHistory()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}

