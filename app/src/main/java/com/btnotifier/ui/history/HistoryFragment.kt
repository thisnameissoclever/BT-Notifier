package com.btnotifier.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.btnotifier.R
import com.btnotifier.databinding.FragmentHistoryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class HistoryFragment : Fragment() {
    private var binding: FragmentHistoryBinding? = null
    private val viewModel: HistoryViewModel by viewModels()
    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentHistoryBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = binding ?: return
        ViewCompat.setAccessibilityHeading(views.tvHistoryTitle, true)
        val adapter = HistoryAdapter()
        views.recyclerHistory.adapter = adapter
        views.btnExportHistory.setOnClickListener { exportDocument.launch("bluetooth-detections.csv") }
        views.btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.history_clear_title)
                .setMessage(R.string.history_clear_message)
                .setNegativeButton(R.string.history_cancel, null)
                .setPositiveButton(R.string.history_clear) { _, _ -> viewModel.clearHistory() }
                .show()
        }
        views.btnRetryHistory.setOnClickListener { viewModel.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.rows)
                    views.progressHistory.visibility = if (state.loading || state.busy) View.VISIBLE else View.GONE
                    views.tvHistoryEmpty.visibility = if (!state.loading && !state.loadFailed && state.rows.isEmpty()) View.VISIBLE else View.GONE
                    views.btnRetryHistory.visibility = if (state.loadFailed) View.VISIBLE else View.GONE
                    views.btnExportHistory.isEnabled = !state.busy && !state.loading && !state.loadFailed && state.rows.isNotEmpty()
                    views.btnClearHistory.isEnabled = !state.busy && !state.loading && !state.loadFailed
                    views.tvHistoryStatus.visibility = if (state.message == null) View.GONE else View.VISIBLE
                    views.tvHistoryStatus.text = state.message?.let { message ->
                        getString(when (message) {
                            HistoryViewModel.Message.LOAD_FAILED -> R.string.history_load_failed
                            HistoryViewModel.Message.CLEAR_FAILED -> R.string.history_clear_failed
                            HistoryViewModel.Message.CLEARED -> R.string.history_cleared
                            HistoryViewModel.Message.EXPORT_FAILED -> R.string.history_export_failed
                            HistoryViewModel.Message.EXPORTED -> R.string.history_exported
                        })
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding?.recyclerHistory?.adapter = null
        binding = null
        super.onDestroyView()
    }
}
