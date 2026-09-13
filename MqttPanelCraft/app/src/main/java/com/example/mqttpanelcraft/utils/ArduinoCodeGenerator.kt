package com.example.mqttpanelcraft.utils

import android.content.Context
import com.example.mqttpanelcraft.model.Project
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

object ArduinoCodeGenerator {

    fun generate(context: Context, project: Project): String {
        val jsonStr =
                loadJSONFromAsset(context, "arduino_templates.json")
                        ?: return "// Error: Template not found"
        return try {
            generateFromTemplates(project, ArduinoExportSupport.parseTemplates(jsonStr))
        } catch (_: Exception) {
            "// Error: Invalid Arduino template"
        }
    }

    fun generateFromTemplates(project: Project, templates: ArduinoTemplateBundle): String {
        if (project.type == com.example.mqttpanelcraft.model.ProjectType.WEBVIEW) {
            return generateForWebView(project, templates)
        }

        try {
            val warnings = mutableListOf<String>()
            val baseTopic = TopicHelper.formatBaseTopic(project.name, project.id)
            checkLength(warnings, "broker", project.broker, ArduinoExportSupport.CFG_LEN)
            checkLength(warnings, "base topic", baseTopic, ArduinoExportSupport.CFG_LEN)
            checkLength(warnings, "username", project.username, ArduinoExportSupport.AUTH_LEN)
            checkLength(warnings, "password", project.password, ArduinoExportSupport.AUTH_LEN)

            val sbGlobals = StringBuilder(templates.globals)
            val sbSetup = StringBuilder(templates.setupStart)
            val sbLoop = StringBuilder(templates.loopStart)
            val sbReceiver = StringBuilder(templates.receiverHead)

            val sbMapping = StringBuilder()
            sbMapping.append("/*\n [Auto-Generated Mapping Table]\n")
            sbMapping.append(" Label | Type | Topic | Variable\n")
            sbMapping.append(" --------------------------------------------------\n")

            val typeIndexes = mutableMapOf<String, Int>()

            project.components.forEach { comp ->
                val templateKey = templates.mappings[comp.type].orEmpty()
                val tmpl = templates.components[templateKey] ?: return@forEach
                val indexKey = tmpl.indexKey.ifEmpty { tmpl.type }
                val idx = (typeIndexes[indexKey] ?: 0) + 1
                typeIndexes[indexKey] = idx

                val fullTopic = ArduinoExportSupport.resolveComponentTopic(project, comp)
                checkLength(warnings, "${comp.label} topic", fullTopic, ArduinoExportSupport.CFG_LEN)

                var varDecl = tmpl.varDecl
                val varMatch = Regex("\\b\\w+_\\{\\{INDEX\\}\\}").find(varDecl)
                val varNameFinal =
                        (varMatch?.value ?: "unknown").replace("{{INDEX}}", idx.toString())
                sbMapping.append(" ${ArduinoExportSupport.escapeComment(comp.label)} | ${ArduinoExportSupport.escapeComment(comp.type)} | ${ArduinoExportSupport.escapeComment(fullTopic)} | $varNameFinal\n")

                fun processPlaceholders(original: String): String {
                    val values = mutableMapOf(
                            "INDEX" to idx.toString(),
                            "LABEL" to ArduinoExportSupport.escapeCString(comp.label),
                            "TOPIC" to ArduinoExportSupport.escapeCString(fullTopic),
                            "TYPE_KEY" to indexKey,
                            "PROP_PAYLOAD" to "ON", "PROP_PAYLOAD_RELEASE" to "OFF",
                            "PROP_PAYLOADLEFT" to "OFF", "PROP_PAYLOADRIGHT" to "ON",
                            "PROP_PAYLOADCENTER" to "1", "PROP_MSG_UP" to "up",
                            "PROP_MSG_DOWN" to "down", "PROP_MSG_LEFT" to "left",
                            "PROP_MSG_RIGHT" to "right", "PROP_MSG_RELEASE" to "stop",
                            "PROP_MIN" to "0", "PROP_MAX" to "100", "PROP_STEP" to "1",
                            "PROP_SERIES_MODE" to "SINGLE", "PROP_SERIES_KEY_1" to "value1",
                            "PROP_SERIES_KEY_2" to "value2"
                    )
                    comp.props.forEach { (k, v) ->
                        values["PROP_${k.uppercase(Locale.ROOT)}"] = ArduinoExportSupport.escapeCString(v)
                    }
                    val escapedTopic = ArduinoExportSupport.escapeCString(fullTopic)
                    values["CHART_PUBLISH"] = if (comp.props["series_mode"] == "MULTI") {
                        val count = comp.props["series_count"]?.toIntOrNull()?.coerceIn(1, 8) ?: 1
                        val fields = (1..count).joinToString("\n") { series ->
                            val key = ArduinoExportSupport.escapeCString(comp.props["series_key_$series"] ?: "value$series")
                            "      series[\"$key\"] = cht_$idx; // Replace with this series' sensor reading."
                        }
                        "JsonDocument series;\n$fields\n      String payload;\n      serializeJson(series, payload);\n      mqttpanel_pub(\"$escapedTopic\", payload);"
                    } else "mqttpanel_pub(\"$escapedTopic\", String(cht_$idx));"
                    return ArduinoExportSupport.fillTemplate(original, values)
                }

                varDecl = processPlaceholders(varDecl)
                // Prevent a user label ending in backslash from splicing the following C++ line.
                if (varDecl.isNotEmpty()) sbGlobals.append(varDecl).append(" \n")

                val setupSub = processPlaceholders(tmpl.setupSub)
                if (setupSub.isNotEmpty()) sbSetup.append("  ").append(setupSub).append("\n")

                val loopLogic = processPlaceholders(tmpl.loopLogic)
                if (loopLogic.isNotEmpty()) sbLoop.append(loopLogic).append("\n")

                val recvLogic = processPlaceholders(tmpl.receiverLogic)
                if (recvLogic.isNotEmpty()) sbReceiver.append(recvLogic).append("\n")
            }

            sbMapping.append("*/\n\n")

            val header = fillHeader(templates.header, project, baseTopic, warnings)

            val fullCode = StringBuilder()
            fullCode.append(header)
            fullCode.append(sbMapping)
            fullCode.append(sbGlobals).append("\n")
            fullCode.append(sbSetup)
            fullCode.append(templates.setupMid)
            fullCode.append(templates.setupEnd)
            fullCode.append(sbLoop)
            fullCode.append(templates.loopEnd)
            fullCode.append(sbReceiver)
            fullCode.append(templates.receiverTail)
            return fullCode.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return "// Error generating code: ${e.message}"
        }
    }

    private fun generateForWebView(project: Project, templates: ArduinoTemplateBundle): String {
        try {
            var htmlContent = project.customCode
            var usedFallback = false
            if (htmlContent.isEmpty()) {
                htmlContent = HtmlTemplates.generateDefaultHtml(project)
                usedFallback = true
            }

            val baseTopic = TopicHelper.formatBaseTopic(project.name, project.id)
            val topics = ArduinoWebTopics(baseTopic).apply { readVariables(htmlContent) }
            var unresolved = 0

            val subRegex = Regex("mqtt\\.subscribe\\s*\\(\\s*([^)]+)\\s*\\)")
            val pubRegex = Regex("mqtt\\.publish\\s*\\(\\s*([^,)]+)")
            val appSubs = mutableSetOf<String>()
            val appPubs = mutableSetOf<String>()
            subRegex.findAll(htmlContent).forEach { match ->
                val resolved = topics.resolve(match.groupValues[1])
                if (!resolved.isNullOrEmpty()) appSubs.add(resolved) else unresolved++
            }
            pubRegex.findAll(htmlContent).forEach { match ->
                val resolved = topics.resolve(match.groupValues[1])
                if (!resolved.isNullOrEmpty()) appPubs.add(resolved) else unresolved++
            }

            val warnings = mutableListOf<String>()
            if (unresolved > 0) warnings.add("$unresolved dynamic Topic expression(s) could not be resolved; add their handlers manually")
            val sb = StringBuilder()
            sb.append(fillHeader(templates.header, project, baseTopic, warnings))
            sb.append("\n/* [WebView Analysis]\n")
            sb.append(
                    "   Source: ${if (usedFallback) "Default Template (Project code was empty)" else "Custom Code"}\n"
            )
            sb.append("   App Publishes (Arduino Subscribes): ${ArduinoExportSupport.escapeComment(appPubs.joinToString(", "))}\n")
            sb.append("   App Subscribes (Arduino Publishes): ${ArduinoExportSupport.escapeComment(appSubs.joinToString(", "))}\n")
            sb.append("*/\n\n")
            sb.append(templates.globals).append("\n")
            sb.append(templates.setupStart)

            if (appPubs.isEmpty()) {
                sb.append("  // INFO: No 'mqtt.publish' detected in App code.\n")
            }
            appPubs.forEach { path ->
                val topic = path
                sb.append("  mqttpanel_sub(\"${ArduinoExportSupport.escapeCString(topic)}\");\n")
            }
            sb.append(templates.setupMid)
            sb.append(templates.setupEnd)
            sb.append(templates.loopStart)

            if (appSubs.isEmpty()) {
                sb.append("  // INFO: No 'mqtt.subscribe' detected in App code.\n")
            } else {
                sb.append("  // --- Examples: Sending Data to App ---\n")
                appSubs.forEach { path ->
                    val topic = path
                    sb.append("  // mqttpanel_pub(\"${ArduinoExportSupport.escapeCString(topic)}\", \"value\");\n")
                }
            }
            sb.append(templates.loopEnd)
            sb.append(templates.receiverHead)
            appPubs.forEach { path ->
                val topic = path
                sb.append("  if (topic == \"${ArduinoExportSupport.escapeCString(topic)}\") {\n")
                sb.append("      Serial.println(msg);\n")
                sb.append("      // TODO: write GPIO\n")
                sb.append("  }\n")
            }
            sb.append(templates.receiverTail)
            return sb.toString()
        } catch (e: Exception) {
            return "// Error generating WebView code: ${e.message}"
        }
    }

    private fun fillHeader(
            raw: String,
            project: Project,
            baseTopic: String,
            warnings: MutableList<String>
    ): String {
        // Firmware buffers count UTF-8 bytes plus NUL, not Kotlin UTF-16 characters.
        listOf(Triple("broker", project.broker, ArduinoExportSupport.CFG_LEN),
                Triple("base topic", baseTopic, ArduinoExportSupport.CFG_LEN),
                Triple("username", project.username, ArduinoExportSupport.AUTH_LEN),
                Triple("password", project.password, ArduinoExportSupport.AUTH_LEN)).forEach { (field, value, capacity) ->
            require(ArduinoExportSupport.lengthWarning(field, value, capacity) == null) {
                "$field exceeds firmware buffer capacity ($capacity bytes including terminator)"
            }
        }
        require(project.port in 1..65535) { "Invalid MQTT port" }
        var header = ArduinoExportSupport.fillTemplate(raw, mapOf(
                "BROKER" to ArduinoExportSupport.escapeCString(project.broker),
                "PORT" to project.port.toString(),
                "BASE_TOPIC" to ArduinoExportSupport.escapeCString(baseTopic),
                "USERNAME" to ArduinoExportSupport.escapeCString(project.username),
                "PASSWORD" to ArduinoExportSupport.escapeCString(project.password)
        ))
        if (warnings.isNotEmpty()) {
            header += "\n/* [Generator warnings]\n" + warnings.joinToString("\n") { " - ${ArduinoExportSupport.escapeComment(it)}" } + "\n */\n"
        }
        return header
    }

    private fun checkLength(warnings: MutableList<String>, field: String, value: String, max: Int) {
        ArduinoExportSupport.lengthWarning(field, value, max)?.let(warnings::add)
    }

    private fun loadJSONFromAsset(context: Context, fileName: String): String? {
        return try {
            val stream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(stream))
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                sb.append(line)
                line = reader.readLine()
            }
            reader.close()
            sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
