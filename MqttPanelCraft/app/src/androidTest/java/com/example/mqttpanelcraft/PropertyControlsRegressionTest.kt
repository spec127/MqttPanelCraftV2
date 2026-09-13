package com.example.mqttpanelcraft

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PropertyControlsRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun everyPropertySegmentHasTheSameOuterCornersInBothThemes() {
        instrumentation.runOnMainSync {
            for (night in listOf(false, true)) {
                val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(config), R.style.Theme_Helloworld)
                val roots = ComponentDefinitionRegistry.getAllTypes().mapNotNull(ComponentDefinitionRegistry::get)
                        .map { it.propertiesLayoutId }.distinct()
                var groupsChecked = 0
                roots.forEach { layout ->
                    val root = LayoutInflater.from(context).inflate(layout, null)
                    descendants(root).filterIsInstance<MaterialButtonToggleGroup>().forEach { group ->
                        groupsChecked++
                        val buttons = (0 until group.childCount).map { group.getChildAt(it) as MaterialButton }
                        val expected = context.resources.getDimensionPixelSize(R.dimen.prop_toggle_corner_radius).toFloat()
                        buttons.forEach { assertEquals(expected, it.cornerRadius.toFloat(), 0.1f) }
                        measure(group, 900)
                        buttons.forEachIndexed { index, button ->
                            val shape = button.shapeAppearanceModel
                            val bounds = RectF(0f, 0f, button.width.toFloat(), button.height.toFloat())
                            val corners = listOf(shape.topLeftCornerSize, shape.topRightCornerSize,
                                    shape.bottomLeftCornerSize, shape.bottomRightCornerSize).map { it.getCornerSize(bounds) }
                            val expectedMax = if (index == 0 || index == buttons.lastIndex) expected else 0f
                            assertEquals(expectedMax, corners.maxOrNull()!!, 0.1f)
                        }
                    }
                }
                assertTrue(groupsChecked > 0)

                // Compact review image showing the Setup card and representative segmented controls.
                val preview = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 24, 24, 24)
                    setBackgroundColor(context.getColor(R.color.icy_background))
                }
                val setup = LayoutInflater.from(context).inflate(R.layout.activity_setup, null)
                val card = setup.findViewById<View>(R.id.itemKeepMqttInBackground)
                (card.parent as ViewGroup).removeView(card)
                card.findViewById<MaterialCheckBox>(R.id.cbKeepMqttInBackground).isChecked = true
                preview.addView(card)
                for (layout in listOf(R.layout.layout_prop_text, R.layout.layout_prop_graphic,
                        R.layout.layout_prop_input_box, R.layout.layout_prop_clock)) {
                    val panel = LayoutInflater.from(context).inflate(layout, null)
                    val group = descendants(panel).filterIsInstance<MaterialButtonToggleGroup>().first()
                    (group.parent as ViewGroup).removeView(group)
                    preview.addView(TextView(context).apply {
                        text = context.resources.getResourceEntryName(layout)
                        setTextColor(context.getColor(R.color.prop_text_primary))
                        setPadding(0, 16, 0, 8)
                    })
                    preview.addView(group)
                    group.check(group.getChildAt(0).id)
                }
                measure(preview, 900)
                val bitmap = Bitmap.createBitmap(preview.width, preview.height, Bitmap.Config.ARGB_8888)
                preview.draw(Canvas(bitmap))
                File(instrumentation.targetContext.getExternalFilesDir(null), "qa-controls-${if (night) "night" else "day"}.png")
                        .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun setupGenerateButtonChangesDisplayedIdAndWholeBackgroundCardToggles() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, SetupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SetupActivity
        try {
            instrumentation.runOnMainSync {
                val field = activity.findViewById<TextView>(R.id.etProjectId)
                val first = field.text.toString()
                activity.findViewById<TextInputLayout>(R.id.tilProjectId)
                        .findViewById<View>(com.google.android.material.R.id.text_input_end_icon).performClick()
                val second = field.text.toString()
                assertNotEquals(first, second)
                assertTrue(second.matches(Regex("[a-z0-9]{10}")))
                val checkbox = activity.findViewById<MaterialCheckBox>(R.id.cbKeepMqttInBackground)
                val original = checkbox.isChecked
                activity.findViewById<View>(R.id.itemKeepMqttInBackground).performClick()
                assertEquals(!original, checkbox.isChecked)
                activity.findViewById<View>(R.id.itemKeepMqttInBackground).performClick()
                assertEquals(original, checkbox.isChecked)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun measure(view: View, width: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}
