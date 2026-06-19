package com.deepfashion.classifier

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.deepfashion.classifier.databinding.FragmentMoreBinding

class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        binding.cardStatistics.setOnClickListener {
            startActivity(Intent(ctx, StatisticsActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.cardFavorites.setOnClickListener {
            startActivity(Intent(ctx, FavoritesActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.cardCompare.setOnClickListener {
            startActivity(Intent(ctx, CompareActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.cardExport.setOnClickListener {
            startActivity(Intent(ctx, ExportActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.cardCrashLogs.setOnClickListener {
            startActivity(Intent(ctx, LogViewerActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.cardHelp.setOnClickListener {
            startActivity(Intent(ctx, HelpCenterActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.cardChangelog.setOnClickListener {
            startActivity(Intent(ctx, ChangelogActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.cardModelInfo.setOnClickListener {
            startActivity(Intent(ctx, ModelInfoActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(ctx, SettingsActivity::class.java))
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        binding.cardDemoData.setOnClickListener { showDemoDataDialog() }
    }

    private fun showDemoDataDialog() {
        val ctx = requireContext()
        val hasDemo = DemoDataSeeder.hasDemoData(ctx)
        AlertDialog.Builder(ctx)
            .setTitle(R.string.demo_data)
            .setMessage(
                if (hasDemo) getString(R.string.demo_data_has_data)
                else getString(R.string.demo_data_message)
            )
            .setPositiveButton(R.string.demo_data_load) { _, _ ->
                Thread {
                    val count = DemoDataSeeder.seed(ctx, replace = false)
                    activity?.runOnUiThread {
                        val msg = if (count > 0) {
                            getString(R.string.demo_data_loaded, count)
                        } else {
                            getString(R.string.demo_data_already)
                        }
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
            .setNeutralButton(R.string.demo_data_replace) { _, _ ->
                Thread {
                    val count = DemoDataSeeder.seed(ctx, replace = true)
                    activity?.runOnUiThread {
                        Toast.makeText(ctx, getString(R.string.demo_data_loaded, count), Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
            .apply {
                if (hasDemo) {
                    setNegativeButton(R.string.demo_data_clear) { _, _ ->
                        Thread {
                            val removed = DemoDataSeeder.clearDemo(ctx)
                            androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                                .putBoolean("pref_demo_data_seeded", false)
                                .apply()
                            activity?.runOnUiThread {
                                Toast.makeText(
                                    ctx,
                                    getString(R.string.demo_data_cleared, removed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }.start()
                    }
                } else {
                    setNegativeButton(android.R.string.cancel, null)
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
