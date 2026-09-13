package com.example.mqttpanelcraft.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.google.android.material.button.MaterialButton

class TutorialOverlayController(host: FrameLayout) {

    private val card: View = LayoutInflater.from(host.context)
        .inflate(R.layout.layout_tutorial_overlay, host, false)
    private val tvStep = card.findViewById<TextView>(R.id.tvTutorialStep)
    private val tvTitle = card.findViewById<TextView>(R.id.tvTutorialTitle)
    private val tvBody = card.findViewById<TextView>(R.id.tvTutorialBody)
    private val btnSkip = card.findViewById<MaterialButton>(R.id.btnTutorialSkip)
    private val btnNext = card.findViewById<MaterialButton>(R.id.btnTutorialNext)

    private val steps = listOf(
        Step(R.string.tutorial_step1_title, R.string.tutorial_step1_body, Wait.NONE),
        Step(R.string.tutorial_step2_title, R.string.tutorial_step2_body, Wait.GROUP1),
        Step(R.string.tutorial_step3_title, R.string.tutorial_step3_body, Wait.RUN_MODE),
        Step(R.string.tutorial_step4_title, R.string.tutorial_step4_body, Wait.GROUP1_TOPICS),
        Step(R.string.tutorial_step5_title, R.string.tutorial_step5_body, Wait.GROUP2),
        Step(R.string.tutorial_step6_title, R.string.tutorial_step6_body, Wait.DELETED),
        Step(R.string.tutorial_step7_title, R.string.tutorial_step7_body, Wait.NONE)
    )

    private var index = 0
    private var components: List<ComponentData> = emptyList()
    private var isEditMode = true
    private var peakInteractive = 0
    private var deleteArmed = false

    init {
        host.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
            )
        )
        host.visibility = View.VISIBLE
        host.isClickable = false
        btnSkip.setOnClickListener { dismiss(host) }
        btnNext.setOnClickListener {
            if (index >= steps.lastIndex) {
                dismiss(host)
            } else if (steps[index].wait == Wait.NONE || conditionMet(steps[index].wait)) {
                index++
                advanceIfReady()
            }
        }
        bind()
    }

    fun onCanvasState(components: List<ComponentData>, isEditMode: Boolean) {
        this.components = components
        this.isEditMode = isEditMode
        val interactive = interactiveCount()
        if (interactive > peakInteractive) peakInteractive = interactive
        if (steps.getOrNull(index)?.wait == Wait.DELETED && interactive < peakInteractive) {
            deleteArmed = true
        }
        advanceIfReady()
    }

    private fun advanceIfReady() {
        while (
            index < steps.lastIndex &&
                steps[index].wait != Wait.NONE &&
                conditionMet(steps[index].wait)
        ) {
            index++
        }
        bind()
    }

    private fun bind() {
        val context = card.context
        val step = steps[index]
        tvStep.text = context.getString(R.string.tutorial_step_counter, index + 1, steps.size)
        tvTitle.setText(step.titleRes)
        tvBody.setText(step.bodyRes)
        val waiting = step.wait != Wait.NONE && !conditionMet(step.wait)
        btnNext.isEnabled = !waiting
        btnNext.alpha = if (waiting) 0.4f else 1f
        btnNext.setText(
            if (index >= steps.lastIndex) R.string.tutorial_btn_done else R.string.common_btn_next
        )
    }

    private fun conditionMet(wait: Wait): Boolean = when (wait) {
        Wait.NONE -> true
        Wait.GROUP1 -> hasTypes("BUTTON", "LED", "TEXT_DISPLAY")
        Wait.RUN_MODE -> !isEditMode
        Wait.GROUP1_TOPICS -> group1TopicsMatch()
        Wait.GROUP2 -> hasTypes("SLIDER", "SCALE_METER") &&
            components.count { it.type == "TEXT_DISPLAY" } >= 2 &&
            group2TopicsMatch()
        Wait.DELETED -> deleteArmed || interactiveCount() < peakInteractive && peakInteractive > 0
    }

    private fun hasTypes(vararg types: String): Boolean =
        types.all { type -> components.any { it.type == type } }

    private fun group1TopicsMatch(): Boolean {
        val button = components.firstOrNull { it.type == "BUTTON" } ?: return false
        val led = components.firstOrNull { it.type == "LED" } ?: return false
        val box = components.firstOrNull { it.type == "TEXT_DISPLAY" } ?: return false
        val topic = button.topicConfig.trim()
        return topic.isNotBlank() &&
            topic == led.topicConfig.trim() &&
            topic == box.topicConfig.trim()
    }

    private fun group2TopicsMatch(): Boolean {
        val slider = components.firstOrNull { it.type == "SLIDER" } ?: return false
        val meter = components.firstOrNull { it.type == "SCALE_METER" } ?: return false
        val box = components.lastOrNull { it.type == "TEXT_DISPLAY" } ?: return false
        val topic = slider.topicConfig.trim()
        return topic.isNotBlank() &&
            topic == meter.topicConfig.trim() &&
            topic == box.topicConfig.trim()
    }

    private fun interactiveCount(): Int = components.count { it.type != "GRAPHIC" }

    private fun dismiss(host: FrameLayout) {
        host.removeView(card)
        host.visibility = View.GONE
    }

    private data class Step(val titleRes: Int, val bodyRes: Int, val wait: Wait)

    private enum class Wait {
        NONE,
        GROUP1,
        RUN_MODE,
        GROUP1_TOPICS,
        GROUP2,
        DELETED
    }
}
