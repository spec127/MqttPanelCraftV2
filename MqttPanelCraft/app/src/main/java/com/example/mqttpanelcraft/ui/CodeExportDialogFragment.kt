package com.example.mqttpanelcraft.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.DialogFragment
import com.example.mqttpanelcraft.R

class CodeExportDialogFragment : DialogFragment() {

    enum class Mode {
        EXPORT_ARDUINO,
        EXPORT_JSON,
        IMPORT_JSON
    }

    private var currentMode: Mode = Mode.EXPORT_ARDUINO
    private var codeContent: String = ""
    private var onImportCallback: ((String) -> Unit)? = null

    // File Saver
    private val saveFileLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri -> saveContentToUri(uri) }
                }
            }

    companion object {
        fun newInstance(
                code: String,
                mode: Mode = Mode.EXPORT_ARDUINO,
                onImport: ((String) -> Unit)? = null
        ): CodeExportDialogFragment {
            val fragment = CodeExportDialogFragment()
            fragment.codeContent = code
            fragment.currentMode = mode
            fragment.onImportCallback = onImport
            return fragment
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_code_export, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Close Button
        view.findViewById<View>(R.id.btnClose)?.setOnClickListener { dismiss() }

        val etCode = view.findViewById<android.widget.EditText>(R.id.etCodeContent)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val tvCodeFilename = view.findViewById<TextView>(R.id.tvCodeFilename)
        val btnSave = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnSaveFile)
        val btnCopy = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCopy)

        // 1. Initial State based on Mode
        when (currentMode) {
            Mode.EXPORT_ARDUINO -> {
                tvTitle.text = getString(R.string.arduino_code_export)
                tvCodeFilename.text = "arduino_sketch.ino"
                btnSave.text = getString(R.string.common_btn_save)
                btnCopy.text = getString(R.string.copy_code)
                etCode.isFocusable = false
                etCode.setText(highlightCode(codeContent))
            }
            Mode.EXPORT_JSON -> {
                tvTitle.text = getString(R.string.dialog_export_title)
                tvCodeFilename.text = "project_config.json"
                btnSave.text = getString(R.string.common_btn_save)
                btnCopy.text = getString(R.string.common_btn_copy)
                etCode.isFocusable = false
                etCode.setText(codeContent)
            }
            Mode.IMPORT_JSON -> {
                tvTitle.text = getString(R.string.dialog_import_title)
                tvCodeFilename.text = getString(R.string.import_json)
                btnSave.text = getString(R.string.common_btn_load_file)
                btnCopy.text = getString(R.string.common_btn_load_text)
                btnCopy.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_save, 0, 0, 0)
                etCode.isFocusable = true
                etCode.isFocusableInTouchMode = true
                etCode.setText(codeContent)
                etCode.hint = getString(R.string.hint_paste_json)
            }
        }

        // 2. Buttons Actions
        btnCopy.setOnClickListener {
            if (currentMode == Mode.IMPORT_JSON) {
                // Load from Text
                val json = etCode.text.toString()
                if (json.isNotBlank()) {
                    onImportCallback?.invoke(json)
                    dismiss()
                } else {
                    Toast.makeText(
                                    requireContext(),
                                    getString(R.string.hint_paste_json),
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                }
            } else {
                // Copy to Clipboard
                val clipboard =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as
                                ClipboardManager
                val clip =
                        ClipData.newPlainText(
                                if (currentMode == Mode.EXPORT_JSON) "JSON Config"
                                else "Arduino Code",
                                etCode.text.toString()
                        )
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                                requireContext(),
                                getString(R.string.common_msg_copied_clipboard),
                                Toast.LENGTH_SHORT
                        )
                        .show()
            }
        }

        btnSave.setOnClickListener {
            if (currentMode == Mode.IMPORT_JSON) {
                // Since Fragment can't directly launch activity for result easily in a unified way
                // without complexity,
                // we'll let SetupActivity handle the file selection for now or use a listener.
                // Actually, SetupActivity already has openJsonLauncher.
                // For simplicity, we can just signal the activity or use a dedicated callback.
                onImportCallback?.invoke("action:OPEN_FILE")
                dismiss()
            } else {
                val ext = if (currentMode == Mode.EXPORT_ARDUINO) ".ino" else ".json"
                val name =
                        if (currentMode == Mode.EXPORT_ARDUINO) "arduino_mqtt_panel$ext"
                        else "config$ext"
                startSaveFile(name)
            }
        }

        // 3. Dark Mode Support
        val isDarkMode =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        val cardRoot = view as? androidx.cardview.widget.CardView
        val containerHeader = view.findViewById<View>(R.id.containerCodeHeader)
        val scrollCode = view.findViewById<View>(R.id.scrollCodeVertical)

        if (isDarkMode) {
            cardRoot?.setCardBackgroundColor(0xFF0F172A.toInt()) // Slate-900
            tvTitle.setTextColor(android.graphics.Color.WHITE)

            containerHeader?.setBackgroundColor(0xFF334155.toInt()) // Slate-700
            tvCodeFilename.setTextColor(0xFF94A3B8.toInt()) // Slate-400
            scrollCode?.setBackgroundColor(0xFF1E293B.toInt()) // Slate-800
            etCode.setTextColor(0xFFE2E8F0.toInt()) // Slate-200
            etCode.setHintTextColor(0xFF64748B.toInt())

            val darkBorderColor = 0xFF475569.toInt() // Slate-600
            btnSave.supportBackgroundTintList =
                    android.content.res.ColorStateList.valueOf(darkBorderColor)
            btnSave.setTextColor(0xFF94A3B8.toInt())
        } else {
            cardRoot?.setCardBackgroundColor(android.graphics.Color.WHITE)
            tvTitle.setTextColor(0xFF0F172A.toInt()) // Slate-950

            containerHeader?.setBackgroundColor(0xFFE2E8F0.toInt()) // Slate-200
            tvCodeFilename.setTextColor(0xFF475569.toInt()) // Slate-600
            scrollCode?.setBackgroundColor(0xFFF1F5F9.toInt()) // Slate-100
            etCode.setTextColor(0xFF1E293B.toInt()) // Slate-800
            etCode.setHintTextColor(0xFF94A3B8.toInt())

            btnSave.supportBackgroundTintList = null
            btnSave.setTextColor(0xFF7C3AED.toInt())
        }
    }

    private fun highlightCode(code: String): CharSequence {
        val spannable = SpannableString(code)

        // Colors (Midnight Tech Palette)
        val colorKeyword = 0xFF7C3AED.toInt() // Purple
        val colorString = 0xFF059669.toInt() // Emerald
        val colorComment = 0xFF94A3B8.toInt() // Slate 400
        val colorNumber = 0xFF2563EB.toInt() // Blue

        // Helper
        fun colorize(pattern: String, color: Int) {
            val regex = Regex(pattern)
            regex.findAll(code).forEach { result ->
                spannable.setSpan(
                        ForegroundColorSpan(color),
                        result.range.first,
                        result.range.last + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // Apply Rules (Order matters for overlaps, though simple regex is limited)
        // 1. Strings
        colorize("\".*?\"", colorString)

        // 2. Directives & Keywords
        val keywords =
                listOf(
                        "#include",
                        "#define",
                        "#ifdef",
                        "#endif",
                        "#else",
                        "void",
                        "int",
                        "float",
                        "bool",
                        "char",
                        "String",
                        "if",
                        "else",
                        "for",
                        "while",
                        "return",
                        "true",
                        "false",
                        "item"
                )
        // Word boundary match
        keywords.forEach { word -> colorize("\\b$word\\b|${Regex.escape(word)}", colorKeyword) }

        // 3. Numbers
        colorize("\\b\\d+\\b", colorNumber)

        // 4. Comments (Last to override others if inside comment)
        colorize("//.*", colorComment)

        return spannable
    }

    private fun startSaveFile(fileName: String) {
        val intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, fileName)
                }
        saveFileLauncher.launch(intent)
    }

    private fun saveContentToUri(uri: Uri) {
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(codeContent.toByteArray())
            }
            Toast.makeText(
                            requireContext(),
                            getString(R.string.project_msg_file_saved),
                            Toast.LENGTH_SHORT
                    )
                    .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                            requireContext(),
                            getString(R.string.project_msg_file_save_failed, e.localizedMessage),
                            Toast.LENGTH_LONG
                    )
                    .show()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // Shrink width by using 88% of screen width
            // Shrink height by using 75% of screen height
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.88).toInt()
            val height = (displayMetrics.heightPixels * 0.75).toInt()
            setLayout(width, height)

            setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
        }
    }
}
