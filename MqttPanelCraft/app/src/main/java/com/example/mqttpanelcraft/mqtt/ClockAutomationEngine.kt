package com.example.mqttpanelcraft.mqtt

import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.ui.components.resolveLinkedTriggerPayload
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ClockTriggerEvent(
    val clockId: Int,
    val targetId: Int,
    val topic: String,
    val payload: String
)

data class ClockRuntimeRecord(
    val signature: String,
    var deadlineAt: Long = 0L,
    var countdownFired: Boolean = false,
    var lastScheduleDate: String = "",
    var activatedAt: Long = 0L
)

data class ClockTickResult(val events: List<ClockTriggerEvent>, val changed: Boolean)

class ClockAutomationEngine(
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault
) {
    private var projectId: String? = null
    private var runtime = false
    private val records = mutableMapOf<Int, ClockRuntimeRecord>()

    fun configure(project: Project, isRuntime: Boolean) {
        if (projectId != project.id) {
            records.clear()
            projectId = project.id
        }
        if (!isRuntime) {
            runtime = false
            records.clear()
            return
        }
        runtime = true
        val current = now()
        val clocks = project.components.filter { it.type == "CLOCK" }
        records.keys.retainAll(clocks.map { it.id }.toSet())
        clocks.forEach { clock ->
            val signature = signature(clock)
            val existing = records[clock.id]
            if (existing == null || existing.signature != signature) {
                val mode = clock.props["clock_mode"] ?: "TIME"
                val seconds = clock.props["countdown_seconds"]?.toLongOrNull()?.coerceAtLeast(1L) ?: 60L
                records[clock.id] = ClockRuntimeRecord(
                    signature = signature,
                    deadlineAt = if (mode == "COUNTDOWN") current + seconds * 1000L else 0L,
                    activatedAt = current
                )
            }
        }
    }

    fun restore(projectId: String, restored: Map<Int, ClockRuntimeRecord>) {
        this.projectId = projectId
        records.clear()
        records.putAll(restored)
    }

    fun snapshot(): Map<Int, ClockRuntimeRecord> = records.mapValues { (_, value) -> value.copy() }

    fun clear() {
        projectId = null
        runtime = false
        records.clear()
    }

    fun tick(project: Project, connected: Boolean): ClockTickResult {
        if (!runtime || project.id != projectId) return ClockTickResult(emptyList(), false)
        val current = now()
        val events = mutableListOf<ClockTriggerEvent>()
        var changed = false
        project.components.filter { it.type == "CLOCK" }.forEach { clock ->
            val record = records[clock.id] ?: return@forEach
            when (clock.props["clock_mode"] ?: "TIME") {
                "COUNTDOWN" -> if (!record.countdownFired && current >= record.deadlineAt && connected) {
                    events += eventsFor(project, clock)
                    record.countdownFired = true
                    changed = true
                }
                "SCHEDULE" -> {
                    val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
                        this.timeZone = this@ClockAutomationEngine.timeZone()
                    }.format(Date(current))
                    if (record.lastScheduleDate != dateKey) {
                        val due = scheduledAt(current, clock.props["schedule_time"] ?: "07:30")
                        if (due != null && current >= due) {
                            if (current - due <= SCHEDULE_GRACE_MS && connected) {
                                events += eventsFor(project, clock)
                                record.lastScheduleDate = dateKey
                                changed = true
                            } else if (current - due > SCHEDULE_GRACE_MS) {
                                record.lastScheduleDate = dateKey
                                changed = true
                            }
                        }
                    }
                }
            }
        }
        return ClockTickResult(events, changed)
    }

    fun remainingSeconds(componentId: Int): Long? = records[componentId]?.let {
        ((it.deadlineAt - now() + 999L) / 1000L).coerceAtLeast(0L)
    }

    fun deadlines(): Map<Int, Long> = records.mapValues { it.value.deadlineAt }.filterValues { it > 0L }

    private fun eventsFor(project: Project, clock: ComponentData): List<ClockTriggerEvent> {
        val triggerValue = clock.props["trigger_value"].orEmpty().ifBlank { "TRIGGER" }
        val targetIds = clock.props["linked_components"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        return project.components.filter { it.id in targetIds && it.topicConfig.isNotBlank() }.map { target ->
            ClockTriggerEvent(clock.id, target.id, target.topicConfig, resolveLinkedTriggerPayload(target, triggerValue))
        }
    }

    private fun signature(clock: ComponentData): String = listOf(
        clock.props["clock_mode"] ?: "TIME",
        clock.props["countdown_seconds"] ?: "60",
        clock.props["schedule_time"] ?: "07:30"
    ).joinToString("|")

    private fun scheduledAt(current: Long, text: String): Long? {
        val parts = text.split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return Calendar.getInstance(timeZone()).apply {
            timeInMillis = current
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        const val SCHEDULE_GRACE_MS = 5 * 60 * 1000L
    }
}
