package com.example.mqttpanelcraft.tutorial

/** Screen coordinates throughout, including adjustPan and an IME-reduced visible frame. */
object TutorialCardPlacement {
    fun top(visibleTop: Int, visibleBottom: Int, targetTop: Int?, targetBottom: Int?,
            cardHeight: Int, gap: Int): Int? {
        if (cardHeight <= 0 || visibleBottom - visibleTop < cardHeight) return null
        val bottomPosition = visibleBottom - cardHeight
        if (targetTop == null || targetBottom == null) return bottomPosition
        if (bottomPosition >= targetBottom + gap) return bottomPosition
        if (visibleTop + cardHeight <= targetTop - gap) return visibleTop
        return null
    }
}
