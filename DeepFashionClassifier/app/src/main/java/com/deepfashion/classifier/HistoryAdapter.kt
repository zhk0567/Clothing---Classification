package com.deepfashion.classifier

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryAdapter(
    private val context: Context,
    private var items: List<HistoryItem>,
    private val scope: CoroutineScope
) : BaseAdapter() {

    private var multiSelectMode = false
    private var selectedItems: Set<HistoryItem> = emptySet()

    fun update(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setSelectionState(enabled: Boolean, selected: Set<HistoryItem>) {
        multiSelectMode = enabled
        selectedItems = selected
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): HistoryItem = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder: ViewHolder
        val view: View
        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val item = items[position]
        val isSelected = selectedItems.contains(item)

        holder.tvTitle.text = "${item.category} · ${(item.confidence * 100).toInt()}%"
        holder.tvSubtitle.text = item.time

        if (multiSelectMode) {
            holder.checkSelect.visibility = View.VISIBLE
            holder.checkSelect.isChecked = isSelected
            holder.root.setBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.history_item_selected else android.R.color.transparent
                )
            )
        } else {
            holder.checkSelect.visibility = View.GONE
            holder.root.setBackgroundResource(android.R.color.transparent)
        }

        bindThumbnail(holder.imgThumb, item)
        return view
    }

    private fun bindThumbnail(img: ImageView, item: HistoryItem) {
        val path = item.imagePath
        if (path.isNullOrBlank()) {
            img.tag = null
            img.setImageDrawable(null)
            return
        }

        img.tag = path
        HistoryThumbnailCache.get(path)?.let {
            img.setImageBitmap(it)
            return
        }

        img.setImageDrawable(null)
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                HistoryThumbnailCache.decodeThumbnail(path)
            }
            if (img.tag == path && bitmap != null) {
                img.setImageBitmap(bitmap)
            }
        }
    }

    private class ViewHolder(view: View) {
        val root: View = view.findViewById(R.id.itemRoot)
        val checkSelect: MaterialCheckBox = view.findViewById(R.id.checkSelect)
        val imgThumb: ImageView = view.findViewById(R.id.imgThumb)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvSubtitle)
    }
}
