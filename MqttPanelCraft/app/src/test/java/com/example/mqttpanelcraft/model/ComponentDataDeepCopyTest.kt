package com.example.mqttpanelcraft.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentDataDeepCopyTest {
    @Test
    fun snapshotPropsAreNotChangedByCurrentComponent() {
        val current = component("before")
        val snapshot = current.deepCopy()

        current.props["value"] = "after"

        assertEquals("before", snapshot.props["value"])
    }

    @Test
    fun restoredComponentDoesNotMutateItsHistory() {
        val history = component("before").deepCopy()
        val restored = history.deepCopy()

        restored.props["value"] = "after undo"

        assertEquals("before", history.props["value"])
    }

    private fun component(value: String) =
            ComponentData(
                    id = 1,
                    type = "TEXT",
                    x = 0f,
                    y = 0f,
                    width = 1,
                    height = 1,
                    label = "text",
                    props = mutableMapOf("value" to value)
            )
}
