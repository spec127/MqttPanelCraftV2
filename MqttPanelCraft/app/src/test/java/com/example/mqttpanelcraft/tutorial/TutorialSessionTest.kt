package com.example.mqttpanelcraft.tutorial

import org.junit.Assert.*
import org.junit.Test

class TutorialSessionTest {
    private fun session() = TutorialSession("local", TutorialStep.RUN_BUTTON,
        mutableMapOf("button" to 1, "led" to 2, "receiver" to 3, "graphic" to 4))
    @Test fun modeAndUnsolicitedReceiveDoNotComplete() {
        val s = session()
        s.event(TutorialEvent.Received("a", "ON", setOf(2,3)), true)
        assertFalse(s.satisfied)
        s.event(TutorialEvent.Sent(1, "a", "ON"), false)
        s.event(TutorialEvent.Received("a", "ON", setOf(2,3)), true)
        assertFalse(s.satisfied)
    }
    @Test fun correctSourceAndBothReceiversAreRequired() {
        val s = session()
        s.event(TutorialEvent.Sent(99, "a", "ON"), true)
        s.event(TutorialEvent.Received("a", "ON", setOf(2,3)), true)
        assertFalse(s.satisfied)
        s.event(TutorialEvent.Sent(1, "a", "ON"), true)
        s.event(TutorialEvent.Received("b", "ON", setOf(2,3)), true)
        assertFalse(s.satisfied)
        s.event(TutorialEvent.Received("a", "ON", setOf(2)), true)
        assertFalse(s.satisfied)
        s.event(TutorialEvent.Received("a", "ON", setOf(2,3)), true)
        assertTrue(s.satisfied)
    }
    @Test fun actionsBeforeStepAndWrongIdsCannotCount() {
        val s = session()
        s.event(TutorialEvent.Deleted(4), false)
        s.enter(TutorialStep.DELETE_GRAPHIC)
        assertFalse(s.satisfied)
        s.event(TutorialEvent.Deleted(99), false)
        assertFalse(s.satisfied)
        s.event(TutorialEvent.Deleted(4), false)
        assertTrue(s.satisfied)
        s.enter(TutorialStep.UNDO)
        assertFalse(s.satisfied)
        s.event(TutorialEvent.Undo, false)
        assertTrue(s.satisfied)
    }
    @Test fun repeatedTypeDoesNotChangeBoundRole() {
        val s = TutorialSession("local", TutorialStep.ADD_BUTTON)
        s.event(TutorialEvent.Added(8, "BUTTON"), false)
        s.event(TutorialEvent.Added(9, "BUTTON"), false)
        assertEquals(8, s.roles["button"])
        s.enter(TutorialStep.SELECT_BUTTON)
        s.event(TutorialEvent.Selected(9), false)
        assertFalse(s.satisfied)
        s.event(TutorialEvent.Selected(8), false)
        assertTrue(s.satisfied)
    }
}
