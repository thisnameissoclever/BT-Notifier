package com.btnotifier.ui

import android.content.Context
import android.view.ViewGroup
import com.btnotifier.R
import com.btnotifier.device.DeviceDisplayName
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object DeviceAliasDialog {

    fun show(
        context: Context,
        currentAlias: String?,
        onSave: (String) -> Unit,
        onClear: () -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        val padding = (24 * density).toInt()

        val inputLayout = TextInputLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(padding, 0, padding, 0)
        }

        val editText = TextInputEditText(context).apply {
            setText(DeviceDisplayName.normalizeAlias(currentAlias).orEmpty())
            hint = context.getString(R.string.hint_device_name)
            setSingleLine()
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.rename_device)
            .setView(inputLayout)
            .setPositiveButton(R.string.save) { _, _ ->
                onSave(editText.text?.toString().orEmpty())
            }
            .setNeutralButton(R.string.clear_alias) { _, _ -> onClear() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
