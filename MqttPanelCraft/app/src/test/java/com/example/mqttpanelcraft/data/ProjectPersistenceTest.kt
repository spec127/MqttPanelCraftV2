package com.example.mqttpanelcraft.data

import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPersistenceTest {
    @Test
    fun rapidSignalsPersistOnlyTheLatestState() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val latest = AtomicReference("first")
        val writes = CopyOnWriteArrayList<String>()
        val coordinator =
                ProjectSaveCoordinator(scope, debounceMs = 20L, maxWaitMs = 80L) {
                    writes.add(latest.get())
                }

        coordinator.requestSave()
        repeat(5) { index ->
            delay(5L)
            latest.set("latest-$index")
            coordinator.requestSave()
        }
        delay(70L)

        assertEquals(listOf("latest-4"), writes)
        scope.cancel()
    }

    @Test
    fun identicalContentDoesNotRewrite() {
        var writes = 0
        val writer = DeduplicatingTextWriter { writes++ }

        assertTrue(writer.writeIfChanged("same"))
        assertFalse(writer.writeIfChanged("same"))
        assertEquals(1, writes)
    }

    @Test
    fun failedWriteKeepsPreviousContentAndCanRetry() {
        var stored = "original"
        var fail = true
        val writer =
                DeduplicatingTextWriter { content ->
                    if (fail) error("simulated write failure")
                    stored = content
                }
        writer.seed(stored)

        runCatching { writer.writeIfChanged("new") }
        assertEquals("original", stored)

        fail = false
        assertTrue(writer.writeIfChanged("new"))
        assertEquals("new", stored)
    }

    @Test
    fun snapshotDoesNotShareComponentProps() {
        val originalComponent = component("before")
        val project =
                Project(
                        id = "id",
                        name = "name",
                        broker = "",
                        type = ProjectType.HOME,
                        components = mutableListOf(originalComponent)
                )
        val snapshot = snapshotProjects(listOf(project))

        originalComponent.props["value"] = "after"

        assertEquals("before", snapshot.single().components.single().props["value"])
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
