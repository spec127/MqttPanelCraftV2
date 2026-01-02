package com.example.mqttpanelcraft.ui

import androidx.fragment.app.FragmentActivity
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.utils.ArduinoCodeGenerator

/**
 * Handles the UI flow for exporting Arduino code.
 * Keeps Activity code clean.
 */
object ArduinoExportManager {

    fun showExportDialog(activity: FragmentActivity, project: Project) {
        val code = ArduinoCodeGenerator.generate(activity, project)
        val dialog = CodeExportDialogFragment.newInstance(code)
        dialog.show(activity.supportFragmentManager, "ExportCode")
    }
}
