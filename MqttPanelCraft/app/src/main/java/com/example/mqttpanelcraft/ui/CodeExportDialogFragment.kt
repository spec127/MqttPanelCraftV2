package com.example.mqttpanelcraft.ui

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.example.mqttpanelcraft.R
import java.io.OutputStream

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
        return inflater.inflate(R.layout.dialog_code_export, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val tvCode = view.findViewById<TextView>(R.id.tvCodeContent)
        tvCode.text = codeContent

        view.findViewById<Button>(R.id.btnCopy).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Arduino Code", codeContent)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), getString(R.string.common_msg_copied_clipboard), Toast.LENGTH_SHORT).show()
        }
        
        view.findViewById<Button>(R.id.btnSaveFile).setOnClickListener {
            startSaveFile("arduino_mqtt_panel.txt") // Default to .txt for mobile readability
        }
        
        view.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dismiss()
        }
    }
    
    private fun startSaveFile(fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // MIME type for text/plain is safest, though could use "application/octet-stream" via Intent
            // .ino is essentially C++ source
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
