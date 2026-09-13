package com.example.mqttpanelcraft.ui

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.utils.ArduinoCodeGenerator
import com.example.mqttpanelcraft.utils.DemoBroker

object ArduinoExportManager {

    fun showExportDialog(activity: FragmentActivity, project: Project) {
        if (DemoBroker.isLocal(project.broker)) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.arduino_demo_export_title)
                .setMessage(R.string.arduino_demo_export_message)
                .setNegativeButton(R.string.common_btn_cancel, null)
                .setPositiveButton(R.string.arduino_demo_export_continue) { _, _ ->
                    presentCode(activity, project)
                }
                .show()
            return
        }
        presentCode(activity, project)
    }

    private fun presentCode(activity: FragmentActivity, project: Project) {
        val code = ArduinoCodeGenerator.generate(activity, project)
        val dialog = CodeExportDialogFragment.newInstance(code)
        dialog.show(activity.supportFragmentManager, "ExportCode")
    }
}
