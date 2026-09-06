package com.example.mqttpanelcraft.mqtt

import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockAutomationEngineTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun countdownWaitsForConnectionAndTriggersOnlyOnce() {
        var now = 0L
        val engine = ClockAutomationEngine({ now }, { utc })
        val project = project(clockMode = "COUNTDOWN", countdown = "10")
        engine.configure(project, true)

        now = 10_000L
        assertTrue(engine.tick(project, connected = false).events.isEmpty())
        now = 20_000L
        assertEquals("ON", engine.tick(project, connected = true).events.single().payload)
        assertTrue(engine.tick(project, connected = true).events.isEmpty())
    }

    @Test
    fun scheduleCatchesUpWithinFiveMinutesOnlyOnce() {
        var now = utcMillis(2026, 1, 1, 7, 29)
        val engine = ClockAutomationEngine({ now }, { utc })
        val project = project(clockMode = "SCHEDULE", schedule = "07:30")
        engine.configure(project, true)

        now = utcMillis(2026, 1, 1, 7, 34)
        assertEquals(1, engine.tick(project, connected = true).events.size)
        assertTrue(engine.tick(project, connected = true).events.isEmpty())
    }

    @Test
    fun scheduleSkipsEventsOlderThanGraceWindow() {
        var now = utcMillis(2026, 1, 1, 7, 29)
        val engine = ClockAutomationEngine({ now }, { utc })
        val project = project(clockMode = "SCHEDULE", schedule = "07:30")
        engine.configure(project, true)
        now = utcMillis(2026, 1, 1, 7, 36)
        assertTrue(engine.tick(project, connected = true).events.isEmpty())
    }

    private fun project(clockMode: String, countdown: String = "10", schedule: String = "07:30"): Project {
        val clock = component(1, "CLOCK", "", mutableMapOf(
            "clock_mode" to clockMode,
            "countdown_seconds" to countdown,
            "schedule_time" to schedule,
            "trigger_value" to "TRIGGER",
            "linked_components" to "2"
        ))
        val target = component(2, "BUTTON", "test/clock", mutableMapOf("payload" to "ON"))
        return Project("project", "test", "broker", type = ProjectType.HOME, components = mutableListOf(clock, target))
    }

    private fun component(id: Int, type: String, topic: String, props: MutableMap<String, String>) =
        ComponentData(id, type, 0f, 0f, 100, 60, type.lowercase(), topic, props)

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
}
