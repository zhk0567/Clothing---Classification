package com.deepfashion.classifier

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.deepfashion.classifier.databinding.ActivityHistoryBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: ActivityHistoryBinding? = null
    private val binding get() = _binding!!
    private var allItems: List<HistoryItem> = emptyList()
    private var filteredItems: List<HistoryItem> = emptyList()
    private lateinit var adapter: HistoryAdapter

    private var selectedCategories: Set<String> = emptySet()
    private var minConfidence: Int = 0
    private var timeFilter: TimeRange = TimeRange.ALL
    private var searchQuery: String = ""
    private var showFavoritesOnly: Boolean = false
    private var multiSelectMode: Boolean = false
    private val selectedItems = mutableSetOf<HistoryItem>()

    private enum class TimeRange { ALL, TODAY, WEEK, MONTH }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getString(ARG_CATEGORY_FILTER)?.let { categoryEn ->
            selectedCategories = setOf(categoryEn)
        }
        adapter = HistoryAdapter(requireContext(), filteredItems, viewLifecycleOwner.lifecycleScope)
        binding.listView.adapter = adapter
        binding.root.setOnClickListener { hideKeyboard() }
        setupSearchAndFilters()
        setupListListeners()
        setupMultiSelectBar()
        setupMenu()
        if (selectedCategories.isNotEmpty()) {
            updateFilterChips()
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_history, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_show_favorites)?.isVisible = !multiSelectMode
                menu.findItem(R.id.action_clear_history)?.isVisible = !multiSelectMode
                menu.findItem(R.id.action_multi_select)?.isVisible = !multiSelectMode
                menu.findItem(R.id.action_cancel_multi_select)?.isVisible = multiSelectMode
                menu.findItem(R.id.action_show_favorites)?.isChecked = showFavoritesOnly
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_show_favorites -> {
                        showFavoritesOnly = !showFavoritesOnly
                        menuItem.isChecked = showFavoritesOnly
                        applyFilters()
                        updateFilterChips()
                        true
                    }
                    R.id.action_clear_history -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.clear_history_title)
                            .setMessage(R.string.clear_history_message)
                            .setPositiveButton(R.string.clear) { _, _ ->
                                HistoryRepository.clearAll(requireContext())
                                exitMultiSelectMode()
                                loadHistory()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                        true
                    }
                    R.id.action_multi_select -> {
                        enterMultiSelectMode()
                        true
                    }
                    R.id.action_cancel_multi_select -> {
                        exitMultiSelectMode()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupSearchAndFilters() {
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
        })
        binding.editSearch.setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            applyFilters()
            true
        }
        binding.listView.setOnTouchListener { _, _ ->
            hideKeyboard()
            false
        }
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
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editSearch.windowToken, 0)
        binding.editSearch.clearFocus()
    }

    private fun showCategoryFilterDialog() {
        val isZh = resources.configuration.locales[0].language.startsWith("zh")
        val categories = CategoryRepository.englishNames
        val items = categories.map { cat ->
            CategoryRepository.getDisplayName(cat, isZh) to (cat in selectedCategories)
        }.toTypedArray()
        val selected = BooleanArray(items.size) { items[it].second }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.filter_by_category)
            .setMultiChoiceItems(items.map { it.first }.toTypedArray(), selected) { _, which, isChecked ->
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
            getString(R.string.filter_time_all), "≥ 50%", "≥ 60%", "≥ 70%", "≥ 80%", "≥ 90%"
        )
        var selectedIndex = when (minConfidence) {
            0 -> 0; 50 -> 1; 60 -> 2; 70 -> 3; 80 -> 4; 90 -> 5; else -> 0
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.filter_by_confidence)
            .setSingleChoiceItems(items, selectedIndex) { _, which -> selectedIndex = which }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                minConfidence = when (selectedIndex) {
                    0 -> 0; 1 -> 50; 2 -> 60; 3 -> 70; 4 -> 80; 5 -> 90; else -> 0
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
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.filter_by_time)
            .setSingleChoiceItems(items, selectedIndex) { _, which -> selectedIndex = which }
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
        if (showFavoritesOnly) {
            binding.chipGroupFilters.addView(createChip(getString(R.string.filter_favorites_only)) {
                showFavoritesOnly = false
                updateFilterChips()
                applyFilters()
            })
        }
        if (selectedCategories.isNotEmpty()) {
            val isZh = resources.configuration.locales[0].language.startsWith("zh")
            val names = selectedCategories.joinToString(", ") {
                CategoryRepository.getDisplayName(it, isZh)
            }
            binding.chipGroupFilters.addView(createChip("${getString(R.string.filter_category_prefix)}$names") {
                selectedCategories = emptySet()
                updateFilterChips()
                applyFilters()
            })
        }
        if (minConfidence > 0) {
            binding.chipGroupFilters.addView(createChip("≥ ${minConfidence}%") {
                minConfidence = 0
                updateFilterChips()
                applyFilters()
            })
        }
        if (timeFilter != TimeRange.ALL) {
            val label = when (timeFilter) {
                TimeRange.TODAY -> getString(R.string.filter_time_today)
                TimeRange.WEEK -> getString(R.string.filter_time_week)
                TimeRange.MONTH -> getString(R.string.filter_time_month)
                else -> ""
            }
            binding.chipGroupFilters.addView(createChip(label) {
                timeFilter = TimeRange.ALL
                updateFilterChips()
                applyFilters()
            })
        }
        if (searchQuery.isNotEmpty()) {
            binding.chipGroupFilters.addView(createChip(searchQuery) {
                searchQuery = ""
                binding.editSearch.setText("")
                updateFilterChips()
                applyFilters()
            })
        }
    }

    private fun createChip(text: String, onClose: () -> Unit): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isCloseIconVisible = true
            setOnCloseIconClickListener { onClose() }
            setEnsureMinTouchTargetSize(false)
        }
    }

    private fun applyFilters() {
        val isZh = resources.configuration.locales[0].language.startsWith("zh")
        filteredItems = allItems.filter { item ->
            val matchFavorite = !showFavoritesOnly || item.isFavorite
            val catEn = CategoryRepository.getEnglishName(item.category) ?: item.category
            val matchCategory = selectedCategories.isEmpty() ||
                item.category in selectedCategories ||
                catEn in selectedCategories
            val matchConfidence = (item.confidence * 100).toInt() >= minConfidence
            val matchTime = when (timeFilter) {
                TimeRange.ALL -> true
                TimeRange.TODAY -> isToday(item.time)
                TimeRange.WEEK -> isWithinWeek(item.time)
                TimeRange.MONTH -> isWithinMonth(item.time)
            }
            val matchSearch = searchQuery.isEmpty() ||
                item.category.contains(searchQuery, ignoreCase = true) ||
                item.time.contains(searchQuery, ignoreCase = true)
            matchFavorite && matchCategory && matchConfidence && matchTime && matchSearch
        }
        adapter.update(filteredItems)
        if (multiSelectMode) {
            selectedItems.retainAll(filteredItems.toSet())
            updateMultiSelectUi()
        }
    }

    private fun isToday(timeStr: String): Boolean {
        return try {
            val itemDate = dateTimeFormat.parse(timeStr) ?: return false
            dateFormat.format(itemDate) == dateFormat.format(Date())
        } catch (_: Exception) {
            false
        }
    }

    private fun isWithinWeek(timeStr: String): Boolean {
        return try {
            val itemDate = dateTimeFormat.parse(timeStr) ?: return false
            val diff = Date().time - itemDate.time
            diff in 0..7 * 24 * 60 * 60 * 1000L
        } catch (_: Exception) {
            false
        }
    }

    private fun isWithinMonth(timeStr: String): Boolean {
        return try {
            val itemDate = dateTimeFormat.parse(timeStr) ?: return false
            val diff = Date().time - itemDate.time
            diff in 0..30L * 24 * 60 * 60 * 1000L
        } catch (_: Exception) {
            false
        }
    }

    private fun loadHistory() {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                HistoryRepository.loadAll(ctx)
            }
            if (!isAdded) return@launch
            allItems = items
            applyFilters()
            withContext(Dispatchers.IO) {
                val paths = items.take(24).mapNotNull { it.imagePath }
                HistoryThumbnailCache.preload(paths)
            }
        }
    }

    private fun setupListListeners() {
        binding.listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            hideKeyboard()
            val item = filteredItems[position]
            if (multiSelectMode) {
                toggleSelection(item)
            } else {
                openResult(item)
            }
        }
        binding.listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val item = filteredItems[position]
            if (multiSelectMode) {
                toggleSelection(item)
            } else {
                enterMultiSelectMode()
                selectedItems.add(item)
                updateMultiSelectUi()
            }
            true
        }
    }

    private fun setupMultiSelectBar() {
        binding.btnMultiCancel.setOnClickListener { exitMultiSelectMode() }
        binding.btnMultiSelectAll.setOnClickListener {
            if (selectedItems.size == filteredItems.size) {
                selectedItems.clear()
            } else {
                selectedItems.clear()
                selectedItems.addAll(filteredItems)
            }
            updateMultiSelectUi()
        }
        binding.btnMultiDelete.setOnClickListener { confirmBatchDelete() }
        binding.btnMultiExport.setOnClickListener { batchExport() }
    }

    private fun enterMultiSelectMode() {
        multiSelectMode = true
        selectedItems.clear()
        updateMultiSelectUi()
    }

    private fun exitMultiSelectMode() {
        multiSelectMode = false
        selectedItems.clear()
        updateMultiSelectUi()
    }

    private fun toggleSelection(item: HistoryItem) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }
        updateMultiSelectUi()
    }

    private fun updateMultiSelectUi() {
        if (multiSelectMode) {
            binding.multiSelectBar.visibility = View.VISIBLE
            binding.filterCard.visibility = View.GONE
            hideKeyboard()
            binding.tvSelectedCount.text = getString(R.string.selected_count, selectedItems.size)
            val allSelected = selectedItems.size == filteredItems.size && filteredItems.isNotEmpty()
            binding.btnMultiSelectAll.setIconResource(
                if (allSelected) R.drawable.ic_deselect_all else R.drawable.ic_select_all
            )
            binding.btnMultiSelectAll.contentDescription = getString(
                if (allSelected) R.string.deselect_all else R.string.select_all
            )
            binding.btnMultiDelete.isEnabled = selectedItems.isNotEmpty()
            binding.btnMultiExport.isEnabled = selectedItems.isNotEmpty()
            binding.btnMultiDelete.alpha = if (selectedItems.isNotEmpty()) 1f else 0.38f
            binding.btnMultiExport.alpha = if (selectedItems.isNotEmpty()) 1f else 0.38f
        } else {
            binding.multiSelectBar.visibility = View.GONE
            binding.filterCard.visibility = View.VISIBLE
        }
        adapter.setSelectionState(multiSelectMode, selectedItems.toSet())
        requireActivity().invalidateOptionsMenu()
    }

    private fun confirmBatchDelete() {
        if (selectedItems.isEmpty()) return
        val count = selectedItems.size
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.batch_delete)
            .setMessage(getString(R.string.batch_delete_confirm, count))
            .setPositiveButton(R.string.delete) { _, _ ->
                HistoryRepository.deleteEntries(requireContext(), selectedItems.toList())
                exitMultiSelectMode()
                loadHistory()
                Toast.makeText(requireContext(), getString(R.string.batch_delete_success, count), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun batchExport() {
        if (selectedItems.isEmpty()) return
        ExportHelper.exportSelected(requireContext(), selectedItems.toList())
        exitMultiSelectMode()
    }

    private fun openResult(item: HistoryItem) {
        val intent = Intent(requireContext(), ResultActivity::class.java)
        intent.putExtra("category", item.category)
        intent.putExtra("confidence", item.confidence)
        intent.putExtra("description", getString(R.string.history_record))
        if (!item.imagePath.isNullOrBlank()) intent.putExtra("imagePath", item.imagePath)
        intent.putExtra("fromHistory", true)
        startActivity(intent)
        activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY_FILTER = "category_filter"
        private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun newInstance(categoryFilter: String? = null): HistoryFragment {
            return HistoryFragment().apply {
                arguments = Bundle().apply {
                    categoryFilter?.let { putString(ARG_CATEGORY_FILTER, it) }
                }
            }
        }
    }
}
