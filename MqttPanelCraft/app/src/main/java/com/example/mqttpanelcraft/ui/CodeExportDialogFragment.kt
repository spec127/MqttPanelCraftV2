package com.example.mqttpanelcraft.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.mqttpanelcraft.R

class CodeExportDialogFragment : DialogFragment() {

    private var codeContent: String = ""

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
            Toast.makeText(requireContext(), "Copied to Clipboard", Toast.LENGTH_SHORT).show()
        }
        
        view.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dismiss()
        }
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
