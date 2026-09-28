package com.example.mqttpanelcraft

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.PropertiesSheetManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android text watchers/layout, isolated from the phone's projects and clipboard. */
@RunWith(AndroidJUnit4::class)
class TopicEditorInstrumentedTest {
    private fun withEditor(block: (View, () -> ComponentData) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Helloworld)
            val panel = LayoutInflater.from(context).inflate(R.layout.activity_project_view, null, false)
            var current = ComponentData(105, "BUTTON", 0f, 0f, 300, 180, "Button",
                "Very_Long_Project_Name/unchanged_project_id/button_1")
            val manager = PropertiesSheetManager(panel, {}, { current = it }, {}, {})
            manager.showProperties(View(context).apply { id = 105 }, current, false)
            block(panel) { current }
        }
    }

    @Test fun nameAndSizeChangesPreserveCompleteTopic() = withEditor { panel, current ->
        val original = current().topicConfig
        assertEquals(original, panel.findViewById<EditText>(R.id.etTopicName).text.toString())
        panel.findViewById<EditText>(R.id.etPropName).setText("Renamed")
        panel.findViewById<EditText>(R.id.etPropWidth).setText("140")
        assertEquals("Renamed", current().label)
        assertEquals(original, current().topicConfig)
    }

    @Test fun fullTutorialAndCustomAddressesAreEditable() = withEditor { panel, current ->
        val topic = panel.findViewById<EditText>(R.id.etTopicName)
        for (address in listOf("tutorial/test_id/light", "external/感測器/value", "custom/#")) {
            topic.setText(address)
            assertNull(topic.error)
            assertEquals(address, current().topicConfig)
        }
        topic.setText("invalid\u0000topic")
        assertNotNull(topic.error)
        panel.findViewById<EditText>(R.id.etPropName).setText("Still editable")
        assertEquals("custom/#", current().topicConfig)
    }
}
