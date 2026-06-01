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
import com.deepfashion.classifier.databinding.FragmentClassifyBinding
import java.io.InputStream

class ClassifyFragment : Fragment() {

    private var _binding: FragmentClassifyBinding? = null
    private val binding get() = _binding!!
    private var isProcessing = false

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedImage(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassifyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnStartCamera.setOnClickListener {
            startActivity(Intent(requireContext(), CameraActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.btnSelectFromGallery.setOnClickListener {
            if (!isProcessing) imagePickerLauncher.launch("image/*")
        }
        binding.btnBatchClassify.setOnClickListener {
            startActivity(Intent(requireContext(), BatchClassifyActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun handleSelectedImage(uri: Uri) {
        isProcessing = true
        binding.btnSelectFromGallery.isEnabled = false
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
                classifyImage(bitmap, targetFile.absolutePath)
            } else {
                Toast.makeText(ctx, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.image_read_failed, Toast.LENGTH_SHORT).show()
        } finally {
            isProcessing = false
            binding.btnSelectFromGallery.isEnabled = true
        }
    }

    private fun classifyImage(bitmap: android.graphics.Bitmap, imagePath: String?) {
        try {
            val result = ClassifierProvider.classifyImage(requireContext(), bitmap, imagePath)
            val intent = Intent(requireContext(), ResultActivity::class.java)
            intent.putExtra("category", result.category)
            intent.putExtra("confidence", result.confidence)
            intent.putExtra("description", result.description)
            intent.putExtra("imagePath", imagePath)
            intent.putExtra("fromHistory", false)
            startActivity(intent)
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.classify_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
