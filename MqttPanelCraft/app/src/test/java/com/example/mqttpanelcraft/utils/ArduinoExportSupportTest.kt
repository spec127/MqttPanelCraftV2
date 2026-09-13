package com.example.mqttpanelcraft.utils

import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArduinoExportSupportTest {
    @Test
    fun escapeCString_escapesQuotesAndNewlines() {
        assertEquals("say \\\"hi\\\"\\nnext", ArduinoExportSupport.escapeCString("say \"hi\"\nnext"))
    }

    @Test
    fun resolveComponentTopic_prefersTopicConfig() {
        val project = project("My Home!")
        val component = ComponentData(1, "BUTTON", 0f, 0f, 10, 10, "button1", "custom/path")
        assertEquals("custom/path", ArduinoExportSupport.resolveComponentTopic(project, component))
    }

    @Test
    fun resolveComponentTopic_fallsBackToNormalizedBase() {
        val project = project("My Home!")
        val component = ComponentData(1, "BUTTON", 0f, 0f, 10, 10, "button1", "  ")
        assertEquals(
                "my_home/abc123/button_1",
                ArduinoExportSupport.resolveComponentTopic(project, component)
        )
    }

    @Test
    fun generateFromTemplates_usesRealTopicsAndNormalizedBase() {
        val json = loadTemplates()
        val button =
                ComponentData(
                        1,
                        "BUTTON",
                        0f,
                        0f,
                        10,
                        10,
                        "button1",
                        "my_home/abc123/button_1",
                        mutableMapOf("payload" to "GO", "payload_release" to "STOP")
                )
        val meter =
                ComponentData(2, "SCALE_METER", 0f, 0f, 10, 10, "meter1", "my_home/abc123/meter_1")
        val project =
                project("My Home!").copy(
                        username = "user",
                        password = "secret",
                        components = mutableListOf(button, meter)
                )

        val code = ArduinoCodeGenerator.generateFromTemplates(project, json)
        assertTrue("generated:\n$code", code.contains("my_home/abc123"))
        assertTrue(code.contains("my_home/abc123/button_1"))
        assertTrue(code.contains("my_home/abc123/meter_1"))
        assertTrue(code.contains("GO"))
        assertTrue(code.contains("STOP"))
        assertTrue(code.contains("mqtt_user[64]    = \"user\""))
        assertTrue(code.contains("mqttpanel_set_auth"))
        assertFalse(code.contains("/set"))
        assertFalse(code.contains("Hello"))
        assertFalse(code.contains("Serial.readString"))
    }

    @Test
    fun generateFromTemplates_imageSensorDoesNotUseHello() {
        val json = loadTemplates()
        val cam = ComponentData(3, "IMAGE_SENSOR", 0f, 0f, 10, 10, "cam1", "my_home/abc123/cam_1")
        val project = project("My Home!").copy(components = mutableListOf(cam))
        val code = ArduinoCodeGenerator.generateFromTemplates(project, json)
        assertTrue(code.contains("my_home/abc123/cam_1"))
        assertTrue(code.contains("ESP32"))
        assertFalse(code.contains("\"Hello\""))
    }

    @Test
    fun generateFromTemplates_escapesBrokerQuotes() {
        val json = loadTemplates()
        val project = project("Lab").copy(broker = "host\"name")
        val code = ArduinoCodeGenerator.generateFromTemplates(project, json)
        assertTrue(code.contains("host\\\"name"))
    }

    @Test
    fun lengthWarning_flagsOversizeValues() {
        val warning = ArduinoExportSupport.lengthWarning("broker", "x".repeat(128), 128)
        assertTrue(warning!!.contains("exceeds"))
    }

    private fun project(name: String) =
            Project(
                    id = "abc123",
                    name = name,
                    broker = "broker.local",
                    type = ProjectType.HOME
            )

    private fun loadTemplates(): ArduinoTemplateBundle {
        val file =
                listOf(
                                File("src/main/assets/arduino_templates.json"),
                                File("app/src/main/assets/arduino_templates.json")
                        )
                        .firstOrNull { it.isFile }
                        ?: error("Cannot locate arduino_templates.json")
        return ArduinoExportSupport.parseTemplates(file.readText())
    }
}
