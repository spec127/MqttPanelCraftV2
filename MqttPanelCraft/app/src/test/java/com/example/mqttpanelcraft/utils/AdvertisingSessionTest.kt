package com.example.mqttpanelcraft.utils

import org.junit.Assert.*
import org.junit.Test

class AdvertisingSessionTest {
    @Test fun requestsFromBeforeTutorialNeverReviveAfterLeaving() {
        val session = AdvertisingSession()
        val dashboardRequest = session.generation
        assertTrue(session.accepts(dashboardRequest))
        session.enterTutorial()
        assertFalse(session.accepts(dashboardRequest))
        assertFalse(session.accepts(session.generation))
        session.leaveTutorial()
        assertFalse(session.accepts(dashboardRequest))
        assertTrue(session.accepts(session.generation))
    }

    @Test fun resumingTutorialKeepsRequestsBlockedAndNormalScreenCanLoadAgain() {
        val session = AdvertisingSession()
        session.enterTutorial()
        val tutorialGeneration = session.generation
        assertFalse(session.enterTutorial())
        assertEquals(tutorialGeneration, session.generation)
        assertFalse(session.accepts(tutorialGeneration))
        session.leaveTutorial()
        val newRequest = session.generation
        assertTrue(session.accepts(newRequest))
        session.enterTutorial()
        session.leaveTutorial()
        assertFalse(session.accepts(newRequest))
    }
}
