package com.example.mqttpanelcraft

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ResourceConsistencyTest {
    private val mainDir: File by lazy {
        listOf(File("src/main"), File("app/src/main")).firstOrNull(File::isDirectory)
            ?: error("Cannot locate app/src/main")
    }

    @Test
    fun `all locales contain the same string keys and format arguments`() {
        val files = listOf(
            mainDir.resolve("res/values/strings.xml"),
            mainDir.resolve("res/values-zh-rTW/strings.xml"),
            mainDir.resolve("res/values-zh-rCN/strings.xml")
        )
        val resources = files.associateWith(::readStrings)
        val base = resources.getValue(files.first())

        resources.forEach { (file, strings) ->
            assertEquals("String keys differ in ${file.parentFile.name}", base.keys, strings.keys)
            base.forEach { (key, value) ->
                assertEquals(
                    "Format arguments differ for $key in ${file.parentFile.name}",
                    formatArguments(value),
                    formatArguments(strings.getValue(key))
                )
            }
        }
    }

    @Test
    fun `english fallback has no accidental Chinese text`() {
        val base = readStrings(mainDir.resolve("res/values/strings.xml"))
        val accidental = base.filterValues { HAN.containsMatchIn(it) }
        assertTrue("Chinese text found in English resources: ${accidental.keys}", accidental.isEmpty())
    }

    @Test
    fun `property dropdowns use shared height and container styles`() {
        val layoutDir = mainDir.resolve("res/layout")
        val allowedStyles = setOf(
            "@style/Widget.App.TextInputLayout.Properties.Dropdown",
            "@style/Widget.App.TextInputLayout.Properties.Compact",
            "@style/Widget.App.TextInputLayout.Properties.DenseDropdown"
        )
        val allowedHeights = setOf(
            "@dimen/prop_input_height_inner",
            "@dimen/prop_input_height_compact_inner"
        )
        var count = 0

        layoutDir.listFiles { file -> file.name.startsWith("layout_prop_") && file.extension == "xml" }
            .orEmpty()
            .forEach { file ->
                val document = parse(file)
                val dropdowns = document.getElementsByTagName("AutoCompleteTextView")
                for (index in 0 until dropdowns.length) {
                    count++
                    val dropdown = dropdowns.item(index) as Element
                    val parent = dropdown.parentNode as Element
                    val id = dropdown.getAttributeNS(ANDROID_NS, "id")
                    assertTrue("$file $id has a one-off height", dropdown.getAttributeNS(ANDROID_NS, "layout_height") in allowedHeights)
                    assertTrue("$file $id has a one-off container style", parent.getAttribute("style") in allowedStyles)
                    assertFalse(
                        "$file $id overrides shared corner radii",
                        listOf("boxCornerRadiusTopStart", "boxCornerRadiusTopEnd", "boxCornerRadiusBottomStart", "boxCornerRadiusBottomEnd")
                            .any { parent.hasAttributeNS(APP_NS, it) }
                    )
                    val textSize = dropdown.getAttributeNS(ANDROID_NS, "textSize")
                    assertTrue("$file $id has a one-off text size", textSize.isEmpty() || textSize == "@dimen/prop_dropdown_text_size")
                }
            }
        assertEquals("Unexpected number of property dropdowns", 38, count)
    }

    @Test
    fun `layouts do not add unapproved visible literal text`() {
        val allowed = setOf(
            "", " ", "ON", "L", "C", "R", "prefix/", "arduino_sketch.ino",
            "0", "1", "3", "100", "1883", "3000", "<html>...</html>"
        )
        val attributePattern = Regex("""android:(?:text|hint|contentDescription|label)=\"([^\"]*)\"""")
        val violations = mainDir.resolve("res/layout").walkTopDown()
            .filter { it.extension == "xml" }
            .flatMap { file ->
                attributePattern.findAll(file.readText()).mapNotNull { match ->
                    val value = match.groupValues[1]
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                    if (value.startsWith("@") || value.startsWith("?") || value in allowed || value.toDoubleOrNull() != null) null
                    else "${file.name}: $value"
                }
            }
            .toList()
        assertTrue("Hardcoded layout text: $violations", violations.isEmpty())
    }

    @Test
    fun `kotlin UI calls do not contain direct translatable text`() {
        val uiMarkers = listOf(
            "Toast.makeText", ".setTitle(", ".setMessage(", ".setPositiveButton(",
            ".setNegativeButton(", ".setNeutralButton("
        )
        val literal = Regex("\"([^\"]*[A-Za-z][^\"]*)\"")
        val allowed = setOf("arduino_sketch.ino", "project_config.json")
        val violations = mainDir.resolve("java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    if (uiMarkers.none(line::contains)) return@mapIndexedNotNull null
                    val values = literal.findAll(line).map { it.groupValues[1] }.filterNot { it in allowed }.toList()
                    if (values.isEmpty()) null else "${file.name}:${index + 1}: $values"
                }
            }
            .toList()
        assertTrue("Hardcoded Kotlin UI text: $violations", violations.isEmpty())
    }

    @Test
    fun `about and privacy documents exist in every supported locale`() {
        listOf("raw", "raw-zh-rTW", "raw-zh-rCN").forEach { directory ->
            assertTrue("Missing localized About document in $directory", mainDir.resolve("res/$directory/about_content.txt").isFile)
            assertTrue("Missing localized privacy document in $directory", mainDir.resolve("res/$directory/privacy_policy.txt").isFile)
        }
    }

    @Test
    fun `fixed property options store stable nonlocalized values`() {
        val strings = readStrings(mainDir.resolve("res/values/strings.xml")).keys
        val optionPattern = Regex("""PropertyOption\(\"([^\"]+)\",\s*R\.string\.([A-Za-z0-9_]+)\)""")
        var count = 0
        mainDir.resolve("java/com/example/mqttpanelcraft/ui/components/definitions").walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                optionPattern.findAll(file.readText()).forEach { match ->
                    count++
                    val value = match.groupValues[1]
                    val labelKey = match.groupValues[2]
                    assertFalse("Localized text used as JSON value in ${file.name}: $value", HAN.containsMatchIn(value))
                    assertTrue("Missing option label $labelKey from ${file.name}", labelKey in strings)
                }
            }
        assertTrue("No PropertyOption declarations were checked", count >= 20)
    }

    private fun readStrings(file: File): Map<String, String> {
        val nodes = parse(file).getElementsByTagName("string")
        return buildMap {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                put(element.getAttribute("name"), element.textContent)
            }
        }
    }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(file)

    private fun formatArguments(value: String): List<String> = FORMAT_ARGUMENT
        .findAll(value.replace("%%", ""))
        .map { it.value }
        .sorted()
        .toList()

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val APP_NS = "http://schemas.android.com/apk/res-auto"
        private val HAN = Regex("[\\u3400-\\u9FFF]")
        private val FORMAT_ARGUMENT = Regex("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]")
    }
}
