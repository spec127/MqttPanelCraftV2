package com.example.mqttpanelcraft

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.utils.ArduinoCodeGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewExportRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun launcherDoesNotDependOnRemovedMockLogin() {
        val context = instrumentation.targetContext
        val pm = context.packageManager
        assertEquals("${context.packageName}.DashboardActivity", pm.getLaunchIntentForPackage(context.packageName)?.component?.className)
        val names = pm.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities.orEmpty().map { it.name }
        assertFalse(names.any { it.endsWith(".LoginActivity") || it.endsWith(".RegisterActivity") })
    }

    @Test fun allControlAndSensorDefaultsExportUsingTheirConfiguredTopics() {
        val context = instrumentation.targetContext
        val definitions = ComponentDefinitionRegistry.getAllTypes().mapNotNull(ComponentDefinitionRegistry::get)
                .filter { it.group != ComponentGroup.DISPLAY }
        assertEquals(17, definitions.size)
        val components = definitions.mapIndexed { index, definition ->
            ComponentData(index + 1, definition.type, 0f, 0f, 100, 60, "review$index", "review/custom_$index",
                    definition.getDefaultProps(context).toMutableMap())
        }.toMutableList()
        val code = ArduinoCodeGenerator.generate(context, Project("review", "Review", "broker.test", type = ProjectType.HOME, components = components))
        assertFalse(code.startsWith("// Error"))
        assertFalse(code.contains("{{"))
        components.forEach { assertTrue(code.contains(it.topicConfig)) }
    }

    @Test fun exportUsesCurrentFormAndPreservesImportedTopicsWithoutSaving() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, SetupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SetupActivity
        try {
            instrumentation.runOnMainSync {
                val component = ComponentData(1, "BUTTON", 0f, 0f, 100, 60, "button1", "old/source/button_1")
                val source = Project("source", "Old", "old.test", type = ProjectType.HOME,
                        components = mutableListOf(component), clientId = "stable")
                fun setPrivate(name: String, value: Any) {
                    SetupActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
                }
                setPrivate("originalProject", source)
                activity.findViewById<TextView>(R.id.etProjectName).text = "New"
                activity.findViewById<TextView>(R.id.etProjectId).text = "newid"
                activity.findViewById<TextView>(R.id.etBroker).text = "new.test"
                val method = SetupActivity::class.java.getDeclaredMethod("buildExportProjectFromForm").apply { isAccessible = true }
                val edited = method.invoke(activity) as Project
                assertEquals("new.test", edited.broker)
                assertEquals("new/newid/button_1", edited.components.single().topicConfig)
                assertEquals("stable", edited.clientId)
                assertEquals("old/source/button_1", source.components.single().topicConfig)
                setPrivate("pendingImportedProject", source)
                setPrivate("pendingComponents", source.components)
                val imported = method.invoke(activity) as Project
                assertEquals("old/source/button_1", imported.components.single().topicConfig)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
