package com.example.mqttpanelcraft.tutorial

enum class TutorialStep {
    WELCOME, ADD_BUTTON, SELECT_BUTTON, TOPIC_BUTTON, RUN_BUTTON,
    ADD_SWITCH, TOPIC_SWITCH, RUN_SWITCH, ADD_SLIDER, TOPIC_SLIDER, RUN_SLIDER,
    EDIT_TEXT, MOVE_GRAPHIC, RESIZE_GRAPHIC, DELETE_GRAPHIC, UNDO, FINISH
}
sealed class TutorialEvent {
    data class Added(val id: Int, val type: String) : TutorialEvent()
    data class Selected(val id: Int) : TutorialEvent()
    data class Moved(val id: Int) : TutorialEvent()
    data class Resized(val id: Int) : TutorialEvent()
    data class Deleted(val id: Int) : TutorialEvent()
    data object Undo : TutorialEvent()
    data class Sent(val id: Int, val topic: String, val payload: String) : TutorialEvent()
    data class Received(val topic: String, val payload: String, val receiverIds: Set<Int>) : TutorialEvent()
}
/** Pure state machine: mode switches and unrelated/old messages cannot satisfy an action. */
data class TutorialSession(
    val projectId: String,
    var step: TutorialStep = TutorialStep.WELCOME,
    val roles: MutableMap<String, Int> = mutableMapOf(),
    var satisfied: Boolean = false,
    var finished: Boolean = false
) {
    private var pending: TutorialEvent.Sent? = null
    fun enter(next: TutorialStep) { step = next; satisfied = false; pending = null }
    fun event(event: TutorialEvent, running: Boolean) {
        val expectedRole = when (step) {
            TutorialStep.RUN_BUTTON -> "button"
            TutorialStep.RUN_SWITCH -> "switch"
            TutorialStep.RUN_SLIDER -> "slider"
            else -> null
        }
        when (event) {
            is TutorialEvent.Added -> {
                val type = when (step) {
                    TutorialStep.ADD_BUTTON -> "BUTTON"
                    TutorialStep.ADD_SWITCH -> "SWITCH"
                    TutorialStep.ADD_SLIDER -> "SLIDER"
                    else -> null
                }
                if (event.type == type) {
                    val role = type!!.lowercase()
                    if (roles[role] == null) roles[role] = event.id
                    if (roles[role] == event.id) satisfied = true
                }
            }
            is TutorialEvent.Selected -> if (step == TutorialStep.SELECT_BUTTON && event.id == roles["button"]) satisfied = true
            is TutorialEvent.Moved -> if (step == TutorialStep.MOVE_GRAPHIC && event.id == roles["graphic"]) satisfied = true
            is TutorialEvent.Resized -> if (step == TutorialStep.RESIZE_GRAPHIC && event.id == roles["graphic"]) satisfied = true
            is TutorialEvent.Deleted -> if (step == TutorialStep.DELETE_GRAPHIC && event.id == roles["graphic"]) satisfied = true
            TutorialEvent.Undo -> if (step == TutorialStep.UNDO) satisfied = true
            is TutorialEvent.Sent -> if (running && expectedRole != null && event.id == roles[expectedRole]) pending = event
            is TutorialEvent.Received -> {
                if (step == TutorialStep.RUN_SLIDER && event.payload.toDoubleOrNull()?.isFinite() != true) return
                val sent = pending
                val receivers = if (step == TutorialStep.RUN_SLIDER) listOf("meter", "chart") else listOf("led", "receiver")
                if (running && expectedRole != null && sent != null && sent.topic == event.topic &&
                    sent.payload == event.payload && receivers.all { roles[it] != null && roles[it] in event.receiverIds }) {
                    satisfied = true
                    pending = null
                }
            }
        }
    }
}
