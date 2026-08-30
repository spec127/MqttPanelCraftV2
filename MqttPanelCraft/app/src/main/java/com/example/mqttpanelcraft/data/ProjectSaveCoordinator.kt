package com.example.mqttpanelcraft.data

import com.example.mqttpanelcraft.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun snapshotProjects(projects: List<Project>): List<Project> {
    return projects.map { project ->
        project.copy(components = project.components.map { it.deepCopy() }.toMutableList())
    }
}

internal class ProjectSaveCoordinator(
        scope: CoroutineScope,
        private val debounceMs: Long = 500L,
        private val maxWaitMs: Long = 2_000L,
        private val saveLatest: suspend () -> Unit
) {
    private val signals = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in signals) {
                val startedAt = System.nanoTime()
                while (true) {
                    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                    val remainingMs = (maxWaitMs - elapsedMs).coerceAtLeast(0L)
                    val waitMs = minOf(debounceMs, remainingMs)
                    if (waitMs > 0L) delay(waitMs)

                    var receivedAnotherSignal = false
                    while (signals.tryReceive().isSuccess) receivedAnotherSignal = true

                    val totalElapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                    if (!receivedAnotherSignal || totalElapsedMs >= maxWaitMs) break
                }
                saveLatest()
            }
        }
    }

    fun requestSave() {
        signals.trySend(Unit)
    }
}

internal class DeduplicatingTextWriter(private val writeAtomically: (String) -> Unit) {
    private var lastWrittenContent: String? = null

    @Synchronized
    fun seed(content: String) {
        lastWrittenContent = content
    }

    @Synchronized
    fun writeIfChanged(content: String): Boolean {
        if (content == lastWrittenContent) return false
        writeAtomically(content)
        lastWrittenContent = content
        return true
    }
}
