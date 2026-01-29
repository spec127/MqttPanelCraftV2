package com.example.mqttpanelcraft.utils

import android.content.Context
import com.example.mqttpanelcraft.model.Project
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object ArduinoCodeGenerator {

    fun generate(context: Context, project: Project): String {
        if (project.type == com.example.mqttpanelcraft.model.ProjectType.WEBVIEW) {
            return generateForWebView(context, project)
        }
        
        try {
            // 1. Load Templates
            val jsonStr = loadJSONFromAsset(context, "arduino_templates.json") ?: return "// Error: Template not found"
            val templates = JSONObject(jsonStr) // Clean JSON
            val base = templates.getJSONObject("base")
            val compTemplates = templates.getJSONObject("components")
            val mappings = templates.getJSONObject("mappings")

            // 2. Prepare Builders
            val sbGlobals = StringBuilder(base.optString("globals", ""))
            val sbSetup = StringBuilder(base.optString("setup_start", ""))
            val sbSetupMid = base.optString("setup_mid", "")
            val sbLoop = StringBuilder(base.optString("loop_start", ""))
            val sbReceiver = StringBuilder(base.optString("receiver_head", ""))

            // Mapping Table Builder
            val sbMapping = StringBuilder()
            sbMapping.append("/*\n [Auto-Generated Mapping Table]\n")
            sbMapping.append(" Label | Type | Topic (Relative) | Variable\n")
            sbMapping.append(" --------------------------------------------------\n")

            // 3. Iterate Components
            val typeIndexes = mutableMapOf<String, Int>()

            project.components.forEach { comp ->
                val templateKey = mappings.optString(comp.type)
                if (templateKey.isNotEmpty() && compTemplates.has(templateKey)) {
                    val tmpl = compTemplates.getJSONObject(templateKey)
                    
                    val specType = tmpl.optString("type", "unknown")
                    val indexKey = tmpl.optString("index_key", specType)
                    
                    val idx = (typeIndexes[indexKey] ?: 0) + 1
                    typeIndexes[indexKey] = idx

                    val relTopicBase = "$specType/$idx"
                    val relTopicSet = "$relTopicBase/set"
                    val relTopicVal = "$relTopicBase/val"

                    // --- Variable Declaration ---
                    var varDecl = tmpl.optString("var_decl", "")

                    // Extract variable name for Mapping (e.g. btn_{{INDEX}})
                    val varMatch = Regex("\\b\\w+_\\{\\{INDEX\\}\\}").find(varDecl)
                    val varNameRaw = varMatch?.value ?: "unknown"
                    val varNameFinal = varNameRaw.replace("{{INDEX}}", idx.toString())

                    // Append to Mapping
                    sbMapping.append(" ${comp.label} | ${comp.type} | $relTopicBase | $varNameFinal\n")

                    varDecl = varDecl.replace("{{INDEX}}", idx.toString())
                                     .replace("{{LABEL}}", comp.label)
                    if (varDecl.isNotEmpty()) sbGlobals.append(varDecl).append("\n")

                    // --- Setup (Subscribe) ---
                    var setupSub = tmpl.optString("setup_sub", "")
                    setupSub = setupSub.replace("{{REL_TOPIC_SET}}", relTopicSet)
                    if (setupSub.isNotEmpty()) sbSetup.append("  ").append(setupSub).append("\n")

                    // --- Loop Logic (Publish) ---
                    var loopLogic = tmpl.optString("loop_logic", "")
                    loopLogic = loopLogic.replace("{{INDEX}}", idx.toString())
                                         .replace("{{REL_TOPIC_VAL}}", relTopicVal)
                                         .replace("{{TYPE_KEY}}", indexKey)
                    if (loopLogic.isNotEmpty()) sbLoop.append(loopLogic).append("\n")
                    
                    // --- Receiver Logic ---
                    var recvLogic = tmpl.optString("receiver_logic", "")
                    recvLogic = recvLogic.replace("{{INDEX}}", idx.toString())
                                         .replace("{{REL_TOPIC_SET}}", relTopicSet)
                                         .replace("{{REL_TOPIC_VAL}}", relTopicVal)
                                         .replace("{{TYPE_KEY}}", indexKey)
                    if (recvLogic.isNotEmpty()) sbReceiver.append(recvLogic).append("\n")
                }
            }
            
            sbMapping.append("*/\n\n")

            // 4. Assemble
            var header = base.optString("header", "")
            
            val cleanProjName = project.name.replace(" ", "_")
            val baseTopic = "$cleanProjName/${project.id}"  

            header = header.replace("{{BROKER}}", project.broker)
                           .replace("{{PORT}}", project.port.toString())
                           .replace("{{BASE_TOPIC}}", baseTopic)

            val fullCode = StringBuilder()
            fullCode.append(header)
            fullCode.append(sbMapping) // Inject Mapping Table
            
            fullCode.append(sbGlobals).append("\n")
            
            fullCode.append(sbSetup)
            fullCode.append(sbSetupMid) 
            fullCode.append(base.optString("setup_end", ""))
            
            fullCode.append(sbLoop)
            fullCode.append(base.optString("loop_end", ""))
            
            fullCode.append(sbReceiver)
            fullCode.append(base.optString("receiver_tail", ""))

            return fullCode.toString()

        } catch (e: Exception) {
            e.printStackTrace()
            return "// Error generating code: ${e.message}"
        }
    }

    private fun generateForWebView(context: Context, project: Project): String {
        try {
             // 1. Load Base Templates
            val jsonStr = loadJSONFromAsset(context, "arduino_templates.json") ?: return "// Error: Template not found"
            val templates = JSONObject(jsonStr)
            val base = templates.getJSONObject("base")

            // FIX: If customCode is empty (e.g. user never saved), fallback to Default Template
            var htmlContent = project.customCode
            var usedFallback = false
            if (htmlContent.isEmpty()) {
                 htmlContent = HtmlTemplates.generateDefaultHtml(project)
                 usedFallback = true
            }
            
            // 2. Scan HTML for Topics (Smart Handling)
            // Strategy:
            // A. Find "const VAR_NAME = TOPIC_BASE + '/relative/path';"
            // B. Find "mqtt.subscribe(VAR_NAME)" OR "mqtt.subscribe('LITERAL')" (App Input -> Arduino Output)
            // C. Find "mqtt.publish(VAR_NAME)" OR "mqtt.publish('LITERAL')" (App Output -> Arduino Input)
            
            val topicVarMap = mutableMapOf<String, String>()
            
            // Refined Regex to be more robust:
            // Matches: const/let/var VAR = VAR + "STRING"
            val varDefRegex = Regex("(?:const|let|var)\\s+(\\w+)\\s*=\\s*\\w+\\s*\\+\\s*['\"]([^'\"]+)['\"]")
            
            varDefRegex.findAll(htmlContent).forEach { match ->
                topicVarMap[match.groupValues[1]] = match.groupValues[2]
            }

            // Regex for Subscribe & Publish
            val subRegex = Regex("mqtt\\.subscribe\\s*\\(\\s*([^)]+)\\s*\\)")
            val pubRegex = Regex("mqtt\\.publish\\s*\\(\\s*([^,)]+)")  // Capture first arg (topic)
            
            val appSubs = mutableSetOf<String>() // Arduino should PUBLISH (Output)
            val appPubs = mutableSetOf<String>() // Arduino should SUBSCRIBE (Input)

            val rawMatchesSub = mutableListOf<String>()
            val rawMatchesPub = mutableListOf<String>()
            
            // Process App Subs
            subRegex.findAll(htmlContent).forEach { match ->
                val arg = match.groupValues[1].trim()
                rawMatchesSub.add(arg)
                val resolved = resolveTopic(arg, topicVarMap)
                if (resolved.isNotEmpty()) appSubs.add(resolved)
            }

            // Process App Pubs
            pubRegex.findAll(htmlContent).forEach { match ->
                val arg = match.groupValues[1].trim()
                rawMatchesPub.add(arg)
                val resolved = resolveTopic(arg, topicVarMap)
                if (resolved.isNotEmpty()) appPubs.add(resolved)
            }

            val sb = StringBuilder()
            
            // Header
            var header = base.optString("header", "")
            val cleanProjName = project.name.replace(" ", "_")
            val baseTopic = "$cleanProjName/${project.id}" 
            
            header = header.replace("{{BROKER}}", project.broker)
                           .replace("{{PORT}}", project.port.toString())
                           .replace("{{BASE_TOPIC}}", baseTopic)
            
            sb.append(header)
            sb.append("\n/* [WebView Analysis]\n")
            sb.append("   Source: ${if (usedFallback) "Default Template (Project code was empty)" else "Custom Code"}\n")
            sb.append("   Found Variables: ${topicVarMap.keys.joinToString(", ")}\n")
            sb.append("   App Publishes (Arduino Subscribes): ${appPubs.joinToString(", ")}\n")
            sb.append("   App Subscribes (Arduino Publishes): ${appSubs.joinToString(", ")}\n")
            sb.append("*/\n\n")

            // Global Vars (Stub)
            sb.append(base.optString("globals", "")).append("\n")
            
            // Setup
            var setupStart = base.optString("setup_start", "")
            sb.append(setupStart)
            
            // Setup (Subscribe)
            if (appPubs.isEmpty()) {
                sb.append("  // INFO: No 'mqtt.publish' detected in App code.\n")
                sb.append("  // If the App sends commands, ensure it uses mqtt.publish('topic', ...)\n")
            }

            appPubs.forEach { relPath ->
                val cleanPath = relPath.removePrefix("/")
                sb.append("  mqttpanel_sub(String(mqtt_topic_head) + \"$cleanPath\");\n")
            }
            sb.append(base.optString("setup_mid", ""))
            sb.append(base.optString("setup_end", ""))

            // Loop
            sb.append(base.optString("loop_start", ""))
            
            // Generate Loop Logic (Derived from App SUBS -> Arduino PUBLISH)
            if (appSubs.isEmpty()) {
                 sb.append("  // INFO: No 'mqtt.subscribe' detected in App code.\n")
            } else {
                 sb.append("  // --- Examples: Sending Data to App ---\n")
                  appSubs.forEach { relPath ->
                      val cleanPath = relPath.removePrefix("/")
                      sb.append("  // To send to '$cleanPath':\n")
                      sb.append("  // mqttpanel_pub(String(mqtt_topic_head) + \"$cleanPath\", \"Hello App\");\n")
                  }
                 sb.append("  // -------------------------------------\n")
            }
            
            sb.append(base.optString("loop_end", ""))
            
            // Receiver
            sb.append(base.optString("receiver_head", ""))
            
            // Receiver Logic (Derived from App PUBS)
            appPubs.forEach { relPath ->
                val cleanPath = relPath.removePrefix("/")
                
                sb.append("  // Match: BASE + /$cleanPath\n")
                sb.append("  if (topic == String(mqtt_topic_head) + \"$cleanPath\") {\n")
                sb.append("      Serial.print(\"[$cleanPath] \"); Serial.println(msg);\n")
                sb.append("      // TODO: Act on command (e.g. if msg is 'ON')...\n")
                sb.append("  }\n")
            }
            
            sb.append(base.optString("receiver_tail", ""))
            
            return sb.toString()
            
        } catch (e: Exception) {
            return "// Error generating WebView code: ${e.message}"
        }
    }

    private fun resolveTopic(arg: String, varMap: Map<String, String>): String {
        val trimmed = arg.trim()
        
        // 1. Check if it's a known variable
        if (varMap.containsKey(trimmed)) {
            return varMap[trimmed]!!
        }
        
        // 2. Check if it's a literal string
        if (trimmed.startsWith("'") || trimmed.startsWith("\"")) {
            return trimmed.trim('\'', '"')
        }
        
        // 3. Check for Concatenation Pattern: PREFIX + 'string'
        // Regex: Any identifier + plus + quoted string
        val concatRegex = Regex("\\w+\\s*\\+\\s*['\"]([^'\"]+)['\"]")
        val match = concatRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1]
        }
        
        return ""
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

