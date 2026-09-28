package com.example.mqttpanelcraft.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import com.example.mqttpanelcraft.*
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.data.TutorialProjectFactory
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.tutorial.*
import com.google.android.material.button.MaterialButton

class TutorialOverlayController(
    private val host: FrameLayout,
    private val activity: Activity,
    private val viewModel: ProjectViewModel,
    private val componentView: (Int) -> View?,
    private val selectComponent: (Int) -> Unit
) {
    private val session = TutorialSessionStore.load(activity, requireNotNull(viewModel.project.value).id)
    private val card = LayoutInflater.from(activity).inflate(R.layout.layout_tutorial_overlay, host, false)
    private val title = card.findViewById<TextView>(R.id.tvTutorialTitle)
    private val body = card.findViewById<TextView>(R.id.tvTutorialBody)
    private val counter = card.findViewById<TextView>(R.id.tvTutorialStep)
    private val next = card.findViewById<MaterialButton>(R.id.btnTutorialNext)
    private val root = activity.window.decorView as ViewGroup
    private val spotlight = TutorialSpotlightDrawable(activity.resources.displayMetrics.density)
    private var lastTarget: View? = null
    private var lastHint: Int? = null
    private var components = viewModel.components.value.orEmpty()
    private var editMode = true
    private var dismissed = false
    private var collapsed = false
    private var spaceCompact = false
    private var placementKey: List<Int>? = null
    private var textAtEntry = ""
    private var selectedId: Int? = null
    private val drawListener = ViewTreeObserver.OnPreDrawListener { updateHighlight(); true }

    init {
        host.addView(card)
        host.visibility = View.VISIBLE
        root.overlay.add(spotlight)
        root.viewTreeObserver.addOnPreDrawListener(drawListener)
        body.movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
        card.findViewById<MaterialButton>(R.id.btnTutorialSkip).setOnClickListener { dismiss() }
        card.findViewById<MaterialButton>(R.id.btnTutorialPrevious).setOnClickListener {
            if (session.step.ordinal > 0) enter(TutorialStep.entries[session.step.ordinal - 1])
        }
        card.findViewById<MaterialButton>(R.id.btnTutorialRetry).setOnClickListener { retry() }
        counter.setOnClickListener {
            collapsed = !collapsed
            spaceCompact = false
            updateHighlight()
        }
        next.setOnClickListener {
            if (!ready()) return@setOnClickListener
            if (session.step == TutorialStep.FINISH) finish()
            else enter(TutorialStep.entries[session.step.ordinal + 1])
        }
        prepareStep()
        bind()
    }

    fun onCanvasState(list: List<ComponentData>, isEditMode: Boolean) {
        components = list
        editMode = isEditMode
        if (!dismissed) bind()
    }

    fun onEvent(event: TutorialEvent) {
        if (dismissed) return
        if (event is TutorialEvent.Selected) selectedId = event.id
        if (event is TutorialEvent.Added) {
            selectedId = event.id
            val role = when (session.step) {
                TutorialStep.ADD_BUTTON -> "button"
                TutorialStep.ADD_SWITCH -> "switch"
                TutorialStep.ADD_SLIDER -> "slider"
                else -> null
            }
            if (role != null && role(role) == null) session.roles.remove(role)
        }
        val previous = session.satisfied
        session.event(event, !editMode)
        if (event is TutorialEvent.Added && session.roles.values.contains(event.id)) {
            val data = components.find { it.id == event.id }
            if (data != null) {
                val density = activity.resources.displayMetrics.density
                val y = when (data.type) { "BUTTON", "SWITCH" -> 205; else -> 310 }
                val x = if (data.type == "SWITCH") 165 else 16
                viewModel.updateComponent(data.copy(x = x * density, y = y * density))
            }
            TutorialSessionStore.save(activity, session)
        }
        if (previous != session.satisfied) TutorialSessionStore.save(activity, session)
        bind()
    }

    private fun enter(step: TutorialStep) {
        spaceCompact = false
        placementKey = null
        lastTarget = null
        lastHint = null
        session.enter(step)
        prepareStep()
        TutorialSessionStore.save(activity, session)
        bind()
        card.post { target()?.let { it.requestRectangleOnScreen(Rect(0, 0, it.width, it.height), true) } }
    }
    private fun prepareStep() {
        val project = viewModel.project.value ?: return
        if (session.step.ordinal >= TutorialStep.ADD_SLIDER.ordinal && !session.roles.containsKey("meter")) {
            TutorialProjectFactory.addRole(activity, project, session, "meter")
            TutorialProjectFactory.addRole(activity, project, session, "chart")
            TutorialSessionStore.save(activity, session)
            ProjectRepository.updateProject(project)
        }
        textAtEntry = role("text")?.props?.get("default_text").orEmpty()
    }
    private fun role(name: String) = components.firstOrNull { it.id == session.roles[name] }
    private fun activeRole(): String? = when (session.step) {
        TutorialStep.ADD_BUTTON, TutorialStep.SELECT_BUTTON, TutorialStep.TOPIC_BUTTON, TutorialStep.RUN_BUTTON -> "button"
        TutorialStep.ADD_SWITCH, TutorialStep.TOPIC_SWITCH, TutorialStep.RUN_SWITCH -> "switch"
        TutorialStep.ADD_SLIDER, TutorialStep.TOPIC_SLIDER, TutorialStep.RUN_SLIDER -> "slider"
        TutorialStep.EDIT_TEXT -> "text"
        TutorialStep.MOVE_GRAPHIC, TutorialStep.RESIZE_GRAPHIC, TutorialStep.DELETE_GRAPHIC, TutorialStep.UNDO -> "graphic"
        else -> null
    }
    private fun ready(): Boolean = when (session.step) {
        TutorialStep.WELCOME, TutorialStep.FINISH -> true
        TutorialStep.ADD_BUTTON, TutorialStep.ADD_SWITCH, TutorialStep.ADD_SLIDER -> role(activeRole()!!) != null
        TutorialStep.TOPIC_BUTTON, TutorialStep.TOPIC_SWITCH, TutorialStep.TOPIC_SLIDER -> {
            val numeric = session.step == TutorialStep.TOPIC_SLIDER
            val expected = TutorialProjectFactory.topic(session, numeric)
            val receivers = if (numeric) listOf("meter", "chart") else listOf("led", "receiver")
            role(activeRole()!!)?.topicConfig == expected && receivers.all { role(it)?.topicConfig == expected }
        }
        TutorialStep.EDIT_TEXT -> session.satisfied ||
            role("text")?.props?.get("default_text")?.let { it.isNotBlank() && it != textAtEntry } == true
        TutorialStep.UNDO -> session.satisfied && role("graphic") != null
        else -> session.satisfied
    }

    private fun bind() {
        if (dismissed) return
        counter.text = activity.getString(R.string.tutorial_step_counter, session.step.ordinal + 1, TutorialStep.entries.size) +
            " · " + activity.getString(R.string.guide_collapse)
        title.setText(titles[session.step.ordinal])
        val expected = TutorialProjectFactory.topic(session, session.step == TutorialStep.TOPIC_SLIDER)
        body.text = activity.getString(bodies[session.step.ordinal], expected)
        next.isEnabled = ready()
        card.findViewById<TextView>(R.id.tvTutorialStatus).apply {
            visibility = if (ready() && session.step !in listOf(TutorialStep.WELCOME, TutorialStep.FINISH)) View.VISIBLE else View.GONE
            setText(R.string.guide_ready)
        }
        next.setText(if (session.step == TutorialStep.FINISH) R.string.tutorial_btn_done else R.string.common_btn_next)
        card.findViewById<MaterialButton>(R.id.btnTutorialPrevious).isEnabled = session.step.ordinal > 0
        // Editing success is saved once, not on every keystroke or frame.
        if (session.step == TutorialStep.EDIT_TEXT && ready() && !session.satisfied) {
            session.satisfied = true
            TutorialSessionStore.save(activity, session)
        }
        next.contentDescription = activity.getString(if (ready()) R.string.guide_ready else R.string.guide_waiting)
        lastHint = null
        updateActionHint(target())
    }

    private fun target(): View? {
        val runStep = session.step in listOf(TutorialStep.RUN_BUTTON, TutorialStep.RUN_SWITCH, TutorialStep.RUN_SLIDER)
        val needsEdit = session.step !in listOf(TutorialStep.WELCOME, TutorialStep.FINISH) && !runStep
        if ((runStep && editMode) || (needsEdit && !editMode)) return activity.findViewById(R.id.fabMode)
        return when (session.step) {
            TutorialStep.ADD_BUTTON, TutorialStep.ADD_SWITCH, TutorialStep.ADD_SLIDER ->
                addTarget()
            TutorialStep.TOPIC_BUTTON, TutorialStep.TOPIC_SWITCH, TutorialStep.TOPIC_SLIDER ->
                activity.findViewById<View>(R.id.etTopicName)?.takeIf { it.isShown && selectedId == session.roles[activeRole()] }
                    ?: session.roles[activeRole()]?.let(componentView)
            TutorialStep.UNDO -> activity.findViewById(R.id.btnUndo)
            TutorialStep.EDIT_TEXT ->
                activity.findViewById<View>(R.id.etTextContent)?.takeIf { it.isShown && selectedId == session.roles["text"] }
                    ?: session.roles["text"]?.let(componentView)
            TutorialStep.RESIZE_GRAPHIC -> session.roles["graphic"]?.let(componentView)?.let {
                it.findViewWithTag<View>("RESIZE_HANDLE")?.takeIf { handle -> handle.isShown } ?: it
            }
            else -> session.roles[activeRole()]?.let(componentView)
        }
    }

    private fun addTarget(): View? {
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        val drawer = activity.findViewById<DrawerLayout>(R.id.drawerLayout)
        if (!drawer.isDrawerVisible(GravityCompat.START)) {
            return toolbar.children.filterIsInstance<ImageButton>()
                .firstOrNull { it.drawable === toolbar.navigationIcon }
        }
        val item = root.findViewWithTag<View>(TutorialProjectFactory.type(activeRole()!!))
        if (item?.visibility == View.GONE) return activity.findViewById(R.id.etSearchComponents)
        return item?.takeIf { it.isShown }
            ?: root.findViewWithTag("component-group:CONTROL")
    }

    private fun updateActionHint(target: View?) {
        val adding = session.step in listOf(TutorialStep.ADD_BUTTON, TutorialStep.ADD_SWITCH, TutorialStep.ADD_SLIDER)
        val hint = when {
            ready() -> null
            adding && !editMode -> R.string.guide_action_edit
            adding && target is ImageButton -> R.string.guide_action_open_library
            adding && target?.id == R.id.etSearchComponents -> R.string.guide_action_clear_search
            adding && target?.tag == "component-group:CONTROL" -> R.string.guide_action_open_controls
            adding -> R.string.guide_action_add_component
            else -> null
        }
        if (hint == lastHint) return
        lastHint = hint
        val expected = TutorialProjectFactory.topic(session, session.step == TutorialStep.TOPIC_SLIDER)
        val explanation = activity.getString(bodies[session.step.ordinal], expected)
        body.text = if (hint == null) explanation else activity.getString(hint) + "\n\n" + explanation
    }

    private fun updateHighlight() {
        if (dismissed) return
        val visible = Rect()
        root.getWindowVisibleDisplayFrame(visible)
        val hostLocation = IntArray(2)
        host.getLocationOnScreen(hostLocation)
        val density = activity.resources.displayMetrics.density
        val margin = (16 * density).toInt()
        val gap = (8 * density).toInt()
        val top = maxOf(visible.top, hostLocation[1]) + margin
        val bottom = minOf(visible.bottom, hostLocation[1] + host.height) - margin
        val ime = androidx.core.view.ViewCompat.getRootWindowInsets(root)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true ||
            root.height - visible.height() > (120 * density).toInt()
        // The focused editor takes precedence while typing, including non-Topic steps.
        val target = if (ime) activity.currentFocus?.takeIf { it is EditText } ?: target() else target()
        updateActionHint(target)
        if (target !== lastTarget) {
            lastTarget = target
            target?.post {
                if (!dismissed && lastTarget === target && !ready()) {
                    target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true)
                }
            }
        }
        val rect = Rect()
        val hasTarget = target != null && target.getGlobalVisibleRect(rect) && rect.intersect(visible)
        val rootLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        val key = listOf(top, bottom, rect.top, rect.bottom, host.width, session.step.ordinal)
        if (placementKey != key) { placementKey = key; spaceCompact = false }
        val compact = ime || collapsed || spaceCompact
        body.visibility = if (compact) View.GONE else View.VISIBLE
        title.visibility = if (compact) View.GONE else View.VISIBLE
        card.findViewById<View>(R.id.tutorialSecondaryActions).visibility =
            if (compact) View.GONE else View.VISIBLE
        card.findViewById<View>(R.id.tvTutorialStatus).visibility =
            if (!compact && ready() && session.step !in listOf(TutorialStep.WELCOME, TutorialStep.FINISH))
                View.VISIBLE else View.GONE
        val maxBodyHeight = ((bottom - top).coerceAtLeast(0) * 0.22f).toInt()
        if (body.maxHeight != maxBodyHeight) body.maxHeight = maxBodyHeight
        val position = TutorialCardPlacement.top(top, bottom,
            rect.top.takeIf { hasTarget }, rect.bottom.takeIf { hasTarget }, card.height, gap)
        if (position == null) {
            // Re-measure compact on the next layout. If even that cannot fit, don't cover input.
            if (!compact) { spaceCompact = true; card.requestLayout() }
            card.visibility = View.INVISIBLE
        } else {
            card.visibility = View.VISIBLE
            val params = card.layoutParams as FrameLayout.LayoutParams
            val offset = (position - hostLocation[1] - params.topMargin).toFloat()
            if (card.translationY != offset) card.translationY = offset
        }
        fun overlayRect(view: View?): Rect = Rect().apply {
            if (view == null || !view.isShown || !view.getGlobalVisibleRect(this) || !intersect(visible)) {
                setEmpty()
            } else {
                offset(-rootLocation[0], -rootLocation[1])
            }
        }
        val viewport = Rect(visible).apply { offset(-rootLocation[0], -rootLocation[1]) }
        val highlightedView = if (ready() && card.visibility == View.VISIBLE && !ime) next else target
        val highlight = overlayRect(highlightedView)
        if (!highlight.isEmpty) {
            highlight.inset(-(4 * density).toInt(), -(4 * density).toInt())
            if (!highlight.intersect(viewport)) highlight.setEmpty()
        }
        spotlight.update(viewport, highlight, overlayRect(card))
    }

    private fun retry() {
        val project = viewModel.project.value ?: return
        val step = if (session.step == TutorialStep.UNDO) TutorialStep.DELETE_GRAPHIC else session.step
        val required = mutableListOf("led", "receiver", "graphic", "text")
        if (step.ordinal >= TutorialStep.ADD_SLIDER.ordinal) required.addAll(listOf("meter", "chart"))
        activeRole()?.let { if (step !in listOf(TutorialStep.ADD_BUTTON, TutorialStep.ADD_SWITCH, TutorialStep.ADD_SLIDER)) required.add(it) }
        required.forEach { TutorialProjectFactory.addRole(activity, project, session, it) }
        val numeric = step in listOf(TutorialStep.TOPIC_SLIDER, TutorialStep.RUN_SLIDER)
        if (step in listOf(TutorialStep.TOPIC_BUTTON, TutorialStep.TOPIC_SWITCH, TutorialStep.TOPIC_SLIDER,
                TutorialStep.RUN_BUTTON, TutorialStep.RUN_SWITCH, TutorialStep.RUN_SLIDER)) {
            val receivers = if (numeric) listOf("meter", "chart") else listOf("led", "receiver")
            receivers.forEach { name -> project.components.firstOrNull { it.id == session.roles[name] }?.topicConfig =
                TutorialProjectFactory.topic(session, numeric) }
        }
        ProjectRepository.updateProject(project)
        enter(step)
        session.roles[activeRole()]?.let { if (editMode) selectComponent(it) }
    }

    private fun finish() {
        session.finished = true
        TutorialSessionStore.save(activity, session)
        dismiss()
        AlertDialog.Builder(activity).setTitle(R.string.guide_finish_title).setMessage(R.string.guide_real_broker)
            .setPositiveButton(R.string.guide_create_project) { _, _ -> activity.startActivity(Intent(activity, SetupActivity::class.java)) }
            .setNegativeButton(R.string.guide_explore, null).show()
    }
    fun dismiss() {
        if (dismissed) return
        TutorialSessionStore.save(activity, session)
        dismissed = true
        host.removeView(card)
        host.visibility = View.GONE
        root.overlay.remove(spotlight)
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(drawListener)
    }

    companion object {
        private val titles = intArrayOf(
            R.string.guide_welcome_title, R.string.guide_add_button_title, R.string.guide_select_title,
            R.string.guide_topic_title, R.string.guide_run_button_title, R.string.guide_switch_title,
            R.string.guide_topic_title, R.string.guide_run_switch_title, R.string.guide_slider_title,
            R.string.guide_topic_title, R.string.guide_run_slider_title, R.string.guide_text_title,
            R.string.guide_move_title, R.string.guide_resize_title, R.string.guide_delete_title,
            R.string.guide_undo_title, R.string.guide_finish_title)
        private val bodies = intArrayOf(
            R.string.guide_welcome_body, R.string.guide_add_button_body, R.string.guide_select_body,
            R.string.guide_topic_body, R.string.guide_run_button_body, R.string.guide_switch_body,
            R.string.guide_topic_body, R.string.guide_run_switch_body, R.string.guide_slider_body,
            R.string.guide_topic_body, R.string.guide_run_slider_body, R.string.guide_text_body,
            R.string.guide_move_body, R.string.guide_resize_body, R.string.guide_delete_body,
            R.string.guide_undo_body, R.string.guide_real_broker)
    }
}
