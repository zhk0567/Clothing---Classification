package com.deepfashion.classifier

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.palette.graphics.Palette

class HistoryAdapter(
    private val context: Context,
    private var items: List<HistoryItem>
) : BaseAdapter() {

    fun update(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
        val img = view.findViewById<ImageView>(R.id.imgThumb)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
        val paletteBar = view.findViewById<LinearLayout>(R.id.paletteBar)

        val item = items[position]
        tvTitle.text = "${item.category} · ${(item.confidence * 100).toInt()}%"
        tvSubtitle.text = item.time

        // decode thumbnail with sampling to avoid OOM
        if (!item.imagePath.isNullOrBlank()) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(item.imagePath, opts)
                var sample = 1
                val target = 112 // ~2x of 56dp for quality
                while (opts.outWidth / sample > target || opts.outHeight / sample > target) {
                    sample *= 2
                }
                val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeFile(item.imagePath, opts2)
                img.setImageBitmap(bmp)

                // build palette bar
                if (bmp != null) {
                    Palette.from(bmp).clearFilters().generate { pal ->
                        paletteBar?.let { bar ->
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
                                bar.visibility = View.GONE
                            } else {
                                bar.visibility = View.VISIBLE
                                colors.forEach { color ->
                                    val v = View(context)
                                    v.setBackgroundColor(color)
                                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT)
                                    lp.weight = 1f
                                    lp.marginEnd = 2
                                    v.layoutParams = lp
                                    bar.addView(v)
                                }
                            }
                        }
                    }
                } else {
                    paletteBar?.visibility = View.GONE
                }
            } catch (_: Exception) {
                img.setImageDrawable(null)
                paletteBar?.visibility = View.GONE
            }
        } else {
            img.setImageDrawable(null)
            paletteBar?.visibility = View.GONE
        }

        return view
    }
}


