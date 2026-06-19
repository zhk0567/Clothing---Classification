package com.deepfashion.classifier

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView

class HomeRecentAdapter(
    private var items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HomeRecentAdapter.ViewHolder>() {

    fun update(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_recent, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgThumb: ShapeableImageView = itemView.findViewById(R.id.imgThumb)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val tvConfidence: TextView = itemView.findViewById(R.id.tvConfidence)

        fun bind(item: HistoryItem, onItemClick: (HistoryItem) -> Unit) {
            tvTitle.text = item.category
            tvSubtitle.text = item.time
            tvConfidence.text = "${(item.confidence * 100).toInt()}%"

            if (!item.imagePath.isNullOrBlank()) {
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(item.imagePath, opts)
                    var sample = 1
                    val target = 96
                    while (opts.outWidth / sample > target || opts.outHeight / sample > target) {
                        sample *= 2
                    }
                    val bmp = BitmapFactory.decodeFile(
                        item.imagePath,
                        BitmapFactory.Options().apply { inSampleSize = sample }
                    )
                    imgThumb.setImageBitmap(bmp)
                } catch (_: Exception) {
                    imgThumb.setImageDrawable(null)
                }
            } else {
                imgThumb.setImageDrawable(null)
            }

            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
