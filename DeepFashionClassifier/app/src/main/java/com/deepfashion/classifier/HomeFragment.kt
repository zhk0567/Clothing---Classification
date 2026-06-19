package com.deepfashion.classifier

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.deepfashion.classifier.databinding.FragmentHomeBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.InputStream

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var recentAdapter: HomeRecentAdapter
    private var isProcessing = false

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedImage(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recentAdapter = HomeRecentAdapter(emptyList()) { item -> openHistoryResult(item) }
        binding.rvRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecent.adapter = recentAdapter

        binding.btnQuickCamera.setOnClickListener {
            startActivity(Intent(requireContext(), CameraActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.btnQuickGallery.setOnClickListener {
            if (!isProcessing) imagePickerLauncher.launch("image/*")
        }

        binding.btnViewAllHistory.setOnClickListener {
            activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId =
                R.id.nav_history
        }
    }

    override fun onResume() {
        super.onResume()
        loadSummary()
    }

    private fun loadSummary() {
        val ctx = requireContext()
        val summary = StatisticsRepository.getSummary(ctx)

        binding.tvWeekCount.text = summary.weekCount.toString()

        if (summary.topCategory != null) {
            binding.tvTopCategory.text = summary.topCategory
            binding.tvTopCategoryCount.visibility = View.VISIBLE
            binding.tvTopCategoryCount.text = getString(R.string.home_stat_top_count, summary.topCategoryCount)
        } else {
            binding.tvTopCategory.text = getString(R.string.home_stat_top_none)
            binding.tvTopCategoryCount.visibility = View.GONE
        }

        val recent = HistoryRepository.loadAll(ctx, limit = 3)
        if (recent.isEmpty()) {
            binding.rvRecent.visibility = View.GONE
            binding.emptyRecent.visibility = View.VISIBLE
            binding.btnViewAllHistory.visibility = View.GONE
        } else {
            binding.rvRecent.visibility = View.VISIBLE
            binding.emptyRecent.visibility = View.GONE
            binding.btnViewAllHistory.visibility = View.VISIBLE
            recentAdapter.update(recent)
        }
    }

    private fun openHistoryResult(item: HistoryItem) {
        val intent = Intent(requireContext(), ResultActivity::class.java)
        intent.putExtra("category", item.category)
        intent.putExtra("confidence", item.confidence)
        intent.putExtra("description", getString(R.string.history_record))
        intent.putExtra("imagePath", item.imagePath)
        intent.putExtra("fromHistory", true)
        startActivity(intent)
        activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun handleSelectedImage(uri: Uri) {
        isProcessing = true
        binding.btnQuickGallery.isEnabled = false
        try {
            val ctx = requireContext()
            val imagesDir = java.io.File(ctx.filesDir, "images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val targetFile = java.io.File(imagesDir, "${System.currentTimeMillis()}.jpg")
            val inputStream: InputStream? = ctx.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                targetFile.outputStream().use { output -> inputStream.copyTo(output) }
                inputStream.close()
            }
            val bitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
            if (bitmap != null) {
                val result = ClassifierProvider.classifyImage(ctx, bitmap, targetFile.absolutePath)
                val intent = Intent(ctx, ResultActivity::class.java)
                intent.putExtra("category", result.category)
                intent.putExtra("confidence", result.confidence)
                intent.putExtra("description", result.description)
                intent.putExtra("imagePath", targetFile.absolutePath)
                intent.putExtra("fromHistory", false)
                startActivity(intent)
                activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                Toast.makeText(ctx, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.classify_failed, Toast.LENGTH_SHORT).show()
        } finally {
            isProcessing = false
            binding.btnQuickGallery.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
