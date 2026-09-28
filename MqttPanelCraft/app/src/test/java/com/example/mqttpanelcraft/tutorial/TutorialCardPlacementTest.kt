package com.example.mqttpanelcraft.tutorial

import org.junit.Assert.*
import org.junit.Test

class TutorialCardPlacementTest {
    @Test fun placesAboveEditorWithinKeyboardVisibleFrame() {
        assertEquals(80, TutorialCardPlacement.top(80, 950, 650, 720, 300, 20))
    }
    @Test fun placesBelowTargetWithoutCrossingKeyboard() {
        assertEquals(650, TutorialCardPlacement.top(80, 950, 100, 180, 300, 20))
    }
    @Test fun refusesToCoverInputWhenNeitherSideFits() {
        assertNull(TutorialCardPlacement.top(80, 550, 230, 330, 300, 20))
    }
    @Test fun compactCardFitsWhenExpandedCardDoesNot() {
        assertNull(TutorialCardPlacement.top(80, 950, 650, 720, 700, 20))
        assertEquals(80, TutorialCardPlacement.top(80, 950, 650, 720, 300, 20))
    }
    @Test fun noTargetStillRespectsVisibleFrame() {
        assertEquals(650, TutorialCardPlacement.top(80, 950, null, null, 300, 20))
        assertNull(TutorialCardPlacement.top(80, 250, null, null, 300, 20))
    }
}
