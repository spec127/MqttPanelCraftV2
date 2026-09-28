package com.example.mqttpanelcraft

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import com.example.mqttpanelcraft.data.*
import com.example.mqttpanelcraft.tutorial.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchHardeningInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun withTutorial(block: (com.example.mqttpanelcraft.model.Project) -> Unit) {
        val prefs = context.getSharedPreferences("TutorialV2", Context.MODE_PRIVATE)
        val previous = prefs.getString("project", null)
        lateinit var project: com.example.mqttpanelcraft.model.Project
        instrumentation.runOnMainSync {
            project = TutorialProjectFactory.newTutorialProject(context)
        }
        try { block(project) } finally {
            instrumentation.runOnMainSync {
                com.example.mqttpanelcraft.mqtt.MqttSessionClient.stop(context)
                ProjectRepository.deleteProject(project.id)
                prefs.edit().remove("session_${project.id}").apply {
                    if (previous == null) remove("project") else putString("project", previous)
                }.commit()
            }
        }
    }

    @Test fun reopeningDoesNotClearTutorialEdits() = withTutorial { project ->
        instrumentation.runOnMainSync {
            project.components[0].props["default_text"] = "KEEP USER EDIT"
            val before = project.components.map { it.deepCopy() }
            ProjectRepository.updateProject(project)
            val resumed = TutorialProjectFactory.ensureTutorialProject(context)
            assertEquals(project.id, resumed.id)
            assertEquals(before, resumed.components)
        }
    }

    @Test fun spotlightDimsBackgroundButKeepsTargetAndOverlappingCardClear() {
        val image = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val canvas = android.graphics.Canvas(image)
            val spotlight = TutorialSpotlightDrawable(1f)
            spotlight.update(android.graphics.Rect(0, 0, 200, 200), android.graphics.Rect(20, 20, 80, 80),
                android.graphics.Rect(60, 60, 180, 150))
            image.eraseColor(android.graphics.Color.WHITE)
            spotlight.draw(canvas)
            assertTrue(android.graphics.Color.red(image.getPixel(10, 180)) < 120)
            for ((x, y) in listOf(40 to 40, 70 to 70, 130 to 100)) {
                assertEquals(android.graphics.Color.WHITE, image.getPixel(x, y))
            }
            spotlight.update(android.graphics.Rect(0, 0, 200, 200), android.graphics.Rect(20, 155, 80, 195),
                android.graphics.Rect(60, 60, 180, 150))
            image.eraseColor(android.graphics.Color.WHITE)
            spotlight.draw(canvas)
            assertTrue(android.graphics.Color.red(image.getPixel(40, 40)) < 120)
            assertEquals(android.graphics.Color.WHITE, image.getPixel(40, 175))
        } finally { image.recycle() }
    }

    @Test fun secondStepSpotlightAllowsRealLibraryAndComponentTaps() = withTutorial { project ->
        val intent = Intent(context, ProjectViewActivity::class.java).putExtra("PROJECT_ID", project.id)
            .putExtra(ProjectViewActivity.EXTRA_SHOW_TUTORIAL, true)
        ActivityScenario.launch<ProjectViewActivity>(intent).use { scenario ->
            onView(withId(R.id.btnTutorialNext)).perform(click())
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<android.widget.TextView>(R.id.tvTutorialBody).text
                    .contains(activity.getString(R.string.guide_action_open_library)))
            }
            onView(allOf(isAssignableFrom(android.widget.ImageButton::class.java),
                isDescendantOfA(withId(R.id.toolbar)))).perform(click())
            onView(withTagValue(`is`("BUTTON" as Any))).perform(click())
            scenario.onActivity { activity ->
                assertEquals(1, project.components.count { it.type == "BUTTON" })
                assertTrue(activity.findViewById<android.widget.Button>(R.id.btnTutorialNext).isEnabled)
            }
            onView(withId(R.id.btnTutorialNext)).perform(click())
            val buttonId = TutorialSessionStore.load(context, project.id).roles.getValue("button")
            onView(withId(buttonId)).perform(click())
            scenario.onActivity { activity ->
                assertTrue("The selected canvas component must also be tappable through the spotlight",
                    activity.findViewById<android.widget.Button>(R.id.btnTutorialNext).isEnabled)
            }
            onView(withId(R.id.btnTutorialSkip)).perform(click())
        }
    }

    @Test fun persistedStepAndRoleIdsSurviveReload() = withTutorial { project ->
        instrumentation.runOnMainSync {
            val session = TutorialSessionStore.load(context, project.id)
            session.enter(TutorialStep.MOVE_GRAPHIC)
            session.satisfied = true
            TutorialSessionStore.save(context, session)
            val loaded = TutorialSessionStore.load(context, project.id)
            assertEquals(session, loaded)
        }
    }

    @Test fun nineTypesCanBePreparedWithUniqueRoles() = withTutorial { project ->
        instrumentation.runOnMainSync {
            val session = TutorialSessionStore.load(context, project.id)
            listOf("button", "switch", "slider", "meter", "chart").forEach {
                TutorialProjectFactory.addRole(context, project, session, it)
            }
            assertEquals(9, project.components.map { it.type }.toSet().size)
            assertEquals(9, project.components.map { it.id }.toSet().size)
            val count = project.components.size
            TutorialProjectFactory.addRole(context, project, session, "chart")
            assertEquals(count, project.components.size)
            assertEquals(TutorialProjectFactory.topic(session, true),
                project.components.first { it.type == "CHART" }.topicConfig)
        }
    }

    @Test fun realTutorialActivityShowsExplicitNextAndPauseWithoutDeletingData() = withTutorial { project ->
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val intent = Intent(context, ProjectViewActivity::class.java).putExtra("PROJECT_ID", project.id)
            .putExtra(ProjectViewActivity.EXTRA_SHOW_TUTORIAL, true)
        ActivityScenario.launch<ProjectViewActivity>(intent).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val next = activity.findViewById<android.widget.Button>(R.id.btnTutorialNext)
                assertNotNull(next)
                assertTrue(next.isEnabled)
                next.performClick()
                assertFalse("Adding a Button must require a real action", next.isEnabled)
                assertEquals(TutorialStep.ADD_BUTTON, TutorialSessionStore.load(context, project.id).step)
                activity.findViewById<android.widget.Button>(R.id.btnTutorialSkip).performClick()
                assertEquals(android.view.View.GONE,
                    activity.findViewById<android.view.View>(R.id.tutorialOverlayHost).visibility)
                assertEquals(4, project.components.size)
            }
        }
    }
}
