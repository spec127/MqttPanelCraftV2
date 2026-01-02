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
            val templates = JSONObject(jsonStr)
            val base = templates.getJSONObject("base")
            val compTemplates = templates.getJSONObject("components")
            val mappings = templates.getJSONObject("mappings")

            // 2. Prepare Builders
            val sbGlobals = StringBuilder(base.optString("globals", ""))
            val sbSetup = StringBuilder(base.optString("setup_start", ""))
            val sbSetupMid = base.optString("setup_mid", "")
            val sbLoop = StringBuilder(base.optString("loop_start", ""))
            val sbReceiver = StringBuilder(base.optString("receiver_head", ""))

            // 3. Iterate Components
            // Track indexes per type (Spec v0.3: index starts at 1)
            val typeIndexes = mutableMapOf<String, Int>()

            project.components.forEach { comp ->
                // Map App Component Tag to Spec Type Key
                val templateKey = mappings.optString(comp.type)
                if (templateKey.isNotEmpty() && compTemplates.has(templateKey)) {
                    val tmpl = compTemplates.getJSONObject(templateKey)
                    
                    // Determine Spec Type and Index
                    val specType = tmpl.optString("type", "unknown")
                    val indexKey = tmpl.optString("index_key", specType)
                    
                    val idx = (typeIndexes[indexKey] ?: 0) + 1
                    typeIndexes[indexKey] = idx

                    // Spec v0.3: Relative Topics (Appended to mqtt_topic base)
                    val relTopicBase = "$specType/$idx"
                    val relTopicSet = "$relTopicBase/set"
                    val relTopicVal = "$relTopicBase/val"

                    // --- Variable Declaration ---
                    var varDecl = tmpl.optString("var_decl", "")
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
                    if (loopLogic.isNotEmpty()) sbLoop.append(loopLogic).append("\n")
                    
                    // --- Receiver Logic ---
                    var recvLogic = tmpl.optString("receiver_logic", "")
                    recvLogic = recvLogic.replace("{{INDEX}}", idx.toString())
                                         .replace("{{REL_TOPIC_SET}}", relTopicSet)
                                         .replace("{{REL_TOPIC_VAL}}", relTopicVal)
                    if (recvLogic.isNotEmpty()) sbReceiver.append(recvLogic).append("\n")
                }
            }

            // 4. Assemble
            var header = base.optString("header", "")
            
            // Inject Global Configs
            // Spec: Topic = projectName/projectId (User doesn't input manually)
            val cleanProjName = project.name.replace(" ", "_")
            val baseTopic = "$cleanProjName/${project.id}"  

            header = header.replace("{{BROKER}}", project.broker)
                           .replace("{{PORT}}", project.port.toString())
                           .replace("{{BASE_TOPIC}}", baseTopic)

            val fullCode = StringBuilder()
            fullCode.append(header)
            
            fullCode.append(sbGlobals).append("\n")
            
            fullCode.append(sbSetup)
            fullCode.append(sbSetupMid) // Helper middleware (mqttpanel_begin)
            fullCode.append(base.optString("setup_end", "")) // Close setup()
            
            fullCode.append(sbLoop)
            fullCode.append(base.optString("loop_end", "")) // Close loop()
            
            fullCode.append(sbReceiver)
            fullCode.append(base.optString("receiver_tail", "")) // Close receiver()

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
