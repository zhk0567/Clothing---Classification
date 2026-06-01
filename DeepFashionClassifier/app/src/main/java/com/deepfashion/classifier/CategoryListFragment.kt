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
import android.widget.BaseExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import com.deepfashion.classifier.databinding.FragmentCategoryListBinding
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CategoryListFragment : Fragment() {

    private var _binding: FragmentCategoryListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CategoryExpandableAdapter
    private var filteredGroups: Map<CategoryGroup, List<CategoryInfo>> = emptyMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isZh = resources.configuration.locales[0].language.startsWith("zh")
        filteredGroups = CategoryRepository.getGrouped()
        adapter = CategoryExpandableAdapter(isZh, filteredGroups)
        binding.expandableCategoryList.setAdapter(adapter)
        for (i in 0 until adapter.groupCount) {
            binding.expandableCategoryList.expandGroup(i)
        }
        binding.expandableCategoryList.setOnChildClickListener { _, _, groupPos, childPos, _ ->
            val info = adapter.getChild(groupPos, childPos) as CategoryInfo
            val intent = Intent(requireContext(), CategoryDetailActivity::class.java)
            intent.putExtra(CategoryDetailActivity.EXTRA_CATEGORY_EN, info.english)
            startActivity(intent)
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            true
        }
        binding.editCategorySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                val isZhLocal = resources.configuration.locales[0].language.startsWith("zh")
                if (query.isEmpty()) {
                    filteredGroups = CategoryRepository.getGrouped()
                } else {
                    val matched = CategoryRepository.search(query, isZhLocal)
                    filteredGroups = matched.groupBy { it.group }
                }
                adapter.update(isZhLocal, filteredGroups)
                for (i in 0 until adapter.groupCount) {
                    binding.expandableCategoryList.expandGroup(i)
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class CategoryExpandableAdapter(
        private var isZh: Boolean,
        private var groups: Map<CategoryGroup, List<CategoryInfo>>
    ) : BaseExpandableListAdapter() {

        private val groupKeys = CategoryGroup.values().filter { groups.containsKey(it) }

        fun update(isZh: Boolean, groups: Map<CategoryGroup, List<CategoryInfo>>) {
            this.isZh = isZh
            this.groups = groups
            notifyDataSetChanged()
        }

        override fun getGroupCount(): Int = groupKeys.size
        override fun getChildrenCount(groupPosition: Int): Int =
            groups[groupKeys[groupPosition]]?.size ?: 0

        override fun getGroup(groupPosition: Int): Any = groupKeys[groupPosition]
        override fun getChild(groupPosition: Int, childPosition: Int): Any =
            groups[groupKeys[groupPosition]]!![childPosition]

        override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()
        override fun getChildId(groupPosition: Int, childPosition: Int): Long = childPosition.toLong()
        override fun hasStableIds(): Boolean = true

        override fun getGroupView(
            groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?
        ): View {
            val view = convertView ?: LayoutInflater.from(parent?.context)
                .inflate(R.layout.item_category_group, parent, false)
            val group = groupKeys[groupPosition]
            view.findViewById<TextView>(R.id.tvGroupTitle).text =
                CategoryRepository.getGroupLabel(group, isZh)
            return view
        }

        override fun getChildView(
            groupPosition: Int, childPosition: Int, isLastChild: Boolean,
            convertView: View?, parent: ViewGroup?
        ): View {
            val view = convertView ?: LayoutInflater.from(parent?.context)
                .inflate(R.layout.item_category_child, parent, false)
            val info = getChild(groupPosition, childPosition) as CategoryInfo
            view.findViewById<TextView>(R.id.tvCategoryName).text =
                CategoryRepository.getDisplayName(info.english, isZh)
            return view
        }

        override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true
    }
}
