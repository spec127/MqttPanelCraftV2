package com.example.mqttpanelcraft.utils

import android.content.Context
import com.example.mqttpanelcraft.model.Project
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object ArduinoCodeGenerator {

    fun generate(context: Context, project: Project): String {
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
