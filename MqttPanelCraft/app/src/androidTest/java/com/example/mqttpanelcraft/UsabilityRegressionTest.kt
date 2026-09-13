package com.example.mqttpanelcraft

import android.view.ContextThemeWrapper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mqttpanelcraft.data.ProjectImportNormalizer
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry
import com.example.mqttpanelcraft.ui.views.WebBoxView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Local-only regressions. Never opens a broker or changes the user's existing projects. */
@RunWith(AndroidJUnit4::class)
class UsabilityRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Helloworld)

    @Test
    fun webKeepsJavascriptAndFormStateAcrossUnrelatedUpdates() {
        lateinit var box: WebBoxView
        lateinit var web: WebView
        val loaded = CountDownLatch(1)
        val loadCount = AtomicInteger()
        val html = "<html><body><input id='field'><script>window.marker='initial';</script></body></html>"
        instrumentation.runOnMainSync {
            box = WebBoxView(context)
            web = box.getChildAt(0) as WebView
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    loadCount.incrementAndGet()
                    loaded.countDown()
                }
            }
            box.applyContent("HTML", "", html)
        }
        try {
            assertTrue("Initial page did not load", loaded.await(15, TimeUnit.SECONDS))
            evaluate(web, "window.marker='kept';document.getElementById('field').value='typed';")
            instrumentation.runOnMainSync {
                repeat(10) {
                    box.applyContent("HTML", "", html)
                    box.refreshIntervalSec = 0
                }
                // Updating the inactive URL must not disturb an HTML page either.
                box.applyContent("HTML", "https://example.invalid", html)
            }
            assertEquals("\"kept:typed\"", evaluate(web, "window.marker+':'+document.getElementById('field').value"))
            assertEquals(1, loadCount.get())
            val secondLoad = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loadCount.incrementAndGet()
                        secondLoad.countDown()
                    }
                }
                box.applyContent("HTML", "", html.replace("initial", "changed"))
            }
            assertTrue(secondLoad.await(15, TimeUnit.SECONDS))
            assertEquals("\"changed\"", evaluate(web, "window.marker"))
            assertEquals(2, loadCount.get())
        } finally {
            instrumentation.runOnMainSync { web.destroy() }
        }
    }

    @Test
    fun exportImportPreservesMetadataAndValidLinks() {
        val project = Project("test", "Test", "", type = ProjectType.HOME,
                clientId = "client", createdAt = 123, lastOpenedAt = 456,
                customCode = "<p>user code</p>", keepMqttInBackground = true,
                components = mutableListOf(
                    ComponentData(50, "CLOCK", 1f, 2f, 100, 60, "clock",
                            props = mutableMapOf("linked_components" to "70")),
                    ComponentData(70, "BUTTON", 3f, 4f, 100, 60, "button", "custom/topic")))
        val parsed = requireNotNull(ProjectRepository.parseProjectJson(ProjectRepository.exportProjectToJson(project)))
        assertEquals(project, parsed)
        val repaired = ProjectImportNormalizer.normalize(parsed).project
        assertEquals("2", repaired.components[0].props["linked_components"])
        assertEquals("custom/topic", repaired.components[1].topicConfig)
    }

    @Test
    fun allRegisteredComponentsCanRenderDefaultsOnThisDevice() {
        instrumentation.runOnMainSync {
            val types = ComponentDefinitionRegistry.getAllTypes()
            assertEquals(22, types.size)
            types.forEachIndexed { index, type ->
                val definition = requireNotNull(ComponentDefinitionRegistry.get(type))
                val props = definition.getDefaultProps(context).toMutableMap()
                if (type == "WEB_BOX") props["source_type"] = "HTML"
                val data = ComponentData(index + 1, type, 0f, 0f, 300, 200, type, props = props)
                val view = definition.createView(context, true)
                definition.onUpdateView(view, data)
                view.measure(android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY))
                view.layout(0, 0, 300, 200)
                if (type == "WEB_BOX") {
                    val webBox = view.findViewWithTag<WebBoxView>("target_web")
                    (webBox?.getChildAt(0) as? WebView)?.destroy()
                }
            }
        }
    }

    private fun evaluate(web: WebView, script: String): String? {
        val finished = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            web.evaluateJavascript(script) { value -> result = value; finished.countDown() }
        }
        assertTrue("Javascript callback timed out", finished.await(10, TimeUnit.SECONDS))
        return result
    }
}
