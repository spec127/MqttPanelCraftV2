package com.example.mqttpanelcraft.ui.components

import com.example.mqttpanelcraft.model.ComponentData
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalComponentTriggerTest {
    private fun component(props: MutableMap<String, String>) = ComponentData(
        id = 1,
        type = "BUTTON",
        x = 0f,
        y = 0f,
        width = 100,
        height = 60,
        label = "target",
        topicConfig = "target/topic",
        props = props
    )

    @Test
    fun targetPayloadTakesPriorityOverClockTriggerValue() {
        assertEquals(
            "ON",
            resolveLinkedTriggerPayload(component(mutableMapOf("payload" to "ON")), "TRIGGER")
        )
    }

    @Test
    fun switchUsesItsOwnRightPayload() {
        assertEquals(
            "OPEN",
            resolveLinkedTriggerPayload(component(mutableMapOf("payloadRight" to "OPEN")), "TRIGGER")
        )
    }

    @Test
    fun triggerValueIsOnlyTheFallback() {
        assertEquals(
            "CLOCK_DONE",
            resolveLinkedTriggerPayload(component(mutableMapOf()), "CLOCK_DONE")
        )
    }
}
