package com.example.mqttpanelcraft.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.example.mqttpanelcraft.R
import java.io.OutputStream
import java.util.regex.Pattern

class CodeExportDialogFragment : DialogFragment() {

    private var codeContent: String = ""
    
    // File Saver
    private val saveFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveContentToUri(uri)
            }
        }
    }

    companion object {
        fun newInstance(code: String): CodeExportDialogFragment {
            val fragment = CodeExportDialogFragment()
            fragment.codeContent = code
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Transparent background for rounded corners
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return inflater.inflate(R.layout.dialog_code_export_refactored, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val tvCode = view.findViewById<TextView>(R.id.tvCodeContent)
        
        // Apply Syntax Highlighting
        tvCode.text = applySyntaxHighlighting(codeContent)

        view.findViewById<View>(R.id.btnCopy).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Arduino Code", codeContent)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), getString(R.string.common_msg_copied_clipboard), Toast.LENGTH_SHORT).show()
        }
        
        view.findViewById<View>(R.id.btnSaveFile).setOnClickListener {
            startSaveFile("arduino_mqtt_panel.ino") // Changed to .ino
        }
        
        view.findViewById<View>(R.id.btnClose).setOnClickListener {
            dismiss()
        }
    }
    
    private fun applySyntaxHighlighting(code: String): SpannableString {
        val spannable = SpannableString(code)

        // Regex Patterns
        val keywords = Pattern.compile("\\b(void|char|int|float|bool|true|false|if|else|while|for|return|break|const|#include|#define|#ifdef|#endif|#elif)\\b")
        val numbers = Pattern.compile("\\b(\\d+)\\b")
        val strings = Pattern.compile("\"(.*?)\"")
        val comments = Pattern.compile("//.*") // Single line comments
        
        // Colors (Light Theme)
        val colorKeyword = Color.parseColor("#A626A4") // Purple
        val colorNumber = Color.parseColor("#986801") // Orange/Brown
        val colorString = Color.parseColor("#50A14F") // Green
        val colorComment = Color.parseColor("#A0A1A7") // Gray

        // Apply
        val matcherKeywords = keywords.matcher(code)
        while (matcherKeywords.find()) {
            spannable.setSpan(ForegroundColorSpan(colorKeyword), matcherKeywords.start(), matcherKeywords.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        val matcherNumbers = numbers.matcher(code)
        while (matcherNumbers.find()) {
            spannable.setSpan(ForegroundColorSpan(colorNumber), matcherNumbers.start(), matcherNumbers.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val matcherStrings = strings.matcher(code)
        while (matcherStrings.find()) {
            spannable.setSpan(ForegroundColorSpan(colorString), matcherStrings.start(), matcherStrings.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val matcherComments = comments.matcher(code)
        while (matcherComments.find()) {
            spannable.setSpan(ForegroundColorSpan(colorComment), matcherComments.start(), matcherComments.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return spannable
    }
    
    private fun startSaveFile(fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
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
            Toast.makeText(requireContext(), getString(R.string.project_msg_file_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), getString(R.string.project_msg_file_save_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
