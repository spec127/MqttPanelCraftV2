package com.example.mqttpanelcraft.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.google.android.material.button.MaterialButton

class TutorialOverlayController(host: FrameLayout) {

    private val card: View = LayoutInflater.from(host.context)
        .inflate(R.layout.layout_tutorial_overlay, host, false)
    private val tvStep = card.findViewById<TextView>(R.id.tvTutorialStep)
    private val tvTitle = card.findViewById<TextView>(R.id.tvTutorialTitle)
    private val tvBody = card.findViewById<TextView>(R.id.tvTutorialBody)
    private val btnSkip = card.findViewById<MaterialButton>(R.id.btnTutorialSkip)
    private val btnBack = card.findViewById<MaterialButton>(R.id.btnTutorialBack)
    private val btnNext = card.findViewById<MaterialButton>(R.id.btnTutorialNext)

    private val steps = listOf(
        Step(R.string.tutorial_step1_title, R.string.tutorial_step1_body),
        Step(R.string.tutorial_step2_title, R.string.tutorial_step2_body),
        Step(R.string.tutorial_step3_title, R.string.tutorial_step3_body),
        Step(R.string.tutorial_step4_title, R.string.tutorial_step4_body),
        Step(R.string.tutorial_step5_title, R.string.tutorial_step5_body),
        Step(R.string.tutorial_step6_title, R.string.tutorial_step6_body),
        Step(R.string.tutorial_step7_title, R.string.tutorial_step7_body),
        Step(R.string.tutorial_step8_title, R.string.tutorial_step8_body),
        Step(R.string.tutorial_step9_title, R.string.tutorial_step9_body),
        Step(R.string.tutorial_step10_title, R.string.tutorial_step10_body)
    )

    private var index = 0

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
        btnBack.setOnClickListener {
            if (index > 0) {
                index--
                bind()
            }
        }
        btnNext.setOnClickListener {
            if (index >= steps.lastIndex) {
                dismiss(host)
            } else {
                index++
                bind()
            }
        }
        bind()
    }

    private fun bind() {
        val context = card.context
        val step = steps[index]
        tvStep.text = context.getString(R.string.tutorial_step_counter, index + 1, steps.size)
        tvTitle.setText(step.titleRes)
        tvBody.setText(step.bodyRes)
        btnBack.isEnabled = index > 0
        btnNext.setText(
            if (index >= steps.lastIndex) R.string.tutorial_btn_done else R.string.common_btn_next
        )
    }

    private fun dismiss(host: FrameLayout) {
        host.removeView(card)
        host.visibility = View.GONE
    }

    private data class Step(val titleRes: Int, val bodyRes: Int)
}
