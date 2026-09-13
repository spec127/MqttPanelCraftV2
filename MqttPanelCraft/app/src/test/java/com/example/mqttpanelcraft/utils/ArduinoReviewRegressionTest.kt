package com.example.mqttpanelcraft.utils

import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ArduinoReviewRegressionTest {
    private fun templates(): ArduinoTemplateBundle {
        val file = listOf(File("src/main/assets/arduino_templates.json"), File("app/src/main/assets/arduino_templates.json")).first { it.isFile }
        return ArduinoExportSupport.parseTemplates(file.readText())
    }
    private fun project() = Project("review", "Review", "broker.test", type = ProjectType.HOME)

    @Test fun webLiteralTopicsAreNotPrefixedOrStripped() {
        val project = project().copy(type = ProjectType.WEBVIEW, customCode = """
            mqtt.publish('/external/command', 'ON');
            mqtt.subscribe('outside/status');
        """)
        val code = ArduinoCodeGenerator.generateFromTemplates(project, templates())
        assertTrue(code.contains("mqttpanel_sub(\"/external/command\")"))
        assertTrue(code.contains("mqttpanel_pub(\"outside/status\""))
        assertFalse(code.contains("review/review/external"))
    }

    @Test fun webVariablesResolveOnlyWhenTheirPrefixIsKnown() {
        val topics = ArduinoWebTopics("review/review")
        topics.readVariables("""
            const T_BASE = (window.app) ? window.app.getBaseTopic() : "fallback";
            const T_CHAT = T_BASE + "/chat";
            const CUSTOM = 'elsewhere';
            const T_CUSTOM = CUSTOM + '/data';
        """)
        assertEquals("review/review/chat", topics.resolve("T_CHAT"))
        assertEquals("elsewhere/data", topics.resolve("T_CUSTOM"))
        assertEquals("a\"b", topics.resolve("'a\\\"b'"))
        assertNull(topics.resolve("unknown + '/data'"))
        assertEquals("one+two", topics.resolve("'one+two'"))
    }

    @Test fun webDefaultTemplateKeepsItsActualTopics() {
        val code = ArduinoCodeGenerator.generateFromTemplates(project().copy(type = ProjectType.WEBVIEW), templates())
        assertTrue(code.contains("mqttpanel_sub(\"review/review/chat\")"))
        assertTrue(code.contains("mqttpanel_sub(\"review/review/switch\")"))
    }

    @Test fun metadataCannotBreakCommentsOrBecomeAnotherTemplate() {
        val component = ComponentData(1, "BUTTON", 0f, 0f, 10, 10, "*/\nBAD\\", "test/topic",
                mutableMapOf("payload" to "{{PROP_PAYLOAD_RELEASE}}", "payload_release" to "STOP"))
        val code = ArduinoCodeGenerator.generateFromTemplates(project().copy(components = mutableListOf(component)), templates())
        assertFalse(code.contains("*/\nBAD"))
        assertFalse(code.lines().any { it.endsWith("\\") })
        assertTrue(code.contains("msg == \"{{PROP_PAYLOAD_RELEASE}}\""))
        assertEquals("\\000x", ArduinoExportSupport.escapeCString("\u0000x"))
    }

    @Test fun utf8BufferChecksRejectOversizedCredentialsWithoutLeakingThem() {
        val password = "密".repeat(22)
        assertNotNull(ArduinoExportSupport.lengthWarning("password", password, 64))
        val code = ArduinoCodeGenerator.generateFromTemplates(project().copy(password = password), templates())
        assertTrue(code.startsWith("// Error"))
        assertFalse(code.contains(password))
    }

    @Test fun allSeventeenControlAndSensorTemplatesHaveDistinctVariablesAndTopics() {
        val types = listOf("BUTTON", "SWITCH", "SLIDER", "SELECTOR", "STEPPER", "JOYSTICK", "DPAD",
                "PALETTE", "INPUTBOX", "LED", "SCALE_METER", "GAUGE_METER", "SIGNAL_INDICATOR",
                "TEXT_DISPLAY", "IMAGE_SENSOR", "CHART", "BROADCAST")
        val components = (types + types).mapIndexed { i, type ->
            ComponentData(i + 1, type, 0f, 0f, 10, 10, "component_$i", "review/topic_$i")
        }.toMutableList()
        val code = ArduinoCodeGenerator.generateFromTemplates(project().copy(components = components), templates())
        assertFalse(code.startsWith("// Error"))
        assertFalse(code.contains("{{"))
        components.forEach { assertTrue(code.contains(it.topicConfig)) }
        val names = Regex("^(?:bool|int|float|String) (\\w+)\\s*(?:=|;)", RegexOption.MULTILINE)
                .findAll(code).map { it.groupValues[1] }.toList()
        assertEquals(names.size, names.toSet().size)
        assertTrue(code.contains("joy_1_x = doc[\"x\"].as<float>()"))
        assertTrue(code.contains("CHUNK:1/3:data"))
        assertTrue(code.contains("pal_1_r = constrain"))
    }

    @Test fun templateKeysAreStableUnderTurkishLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val component = ComponentData(1, "SLIDER", 0f, 0f, 10, 10, "slider1", "topic",
                    mutableMapOf("min" to "-25"))
            val code = ArduinoCodeGenerator.generateFromTemplates(project().copy(components = mutableListOf(component)), templates())
            assertTrue(code.contains("min=-25"))
        } finally { Locale.setDefault(old) }
    }

    @Test fun multiSeriesChartPublishesConfiguredKeysAsJson() {
        val chart = ComponentData(1, "CHART", 0f, 0f, 100, 60, "chart", "custom/chart",
                mutableMapOf("series_mode" to "MULTI", "series_count" to "3", "series_key_1" to "temperature",
                        "series_key_2" to "humidity", "series_key_3" to "pressure"))
        val code = ArduinoCodeGenerator.generateFromTemplates(project().copy(components = mutableListOf(chart)), templates())
        assertTrue(code.contains("series[\"temperature\"]"))
        assertTrue(code.contains("series[\"pressure\"]"))
        assertTrue(code.contains("serializeJson(series, payload)"))
        assertTrue(code.contains("mqttpanel_pub(\"custom/chart\", payload)"))
        assertFalse(code.contains("{{CHART_PUBLISH}}"))
    }
}
