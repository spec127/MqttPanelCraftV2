package com.example.mqttpanelcraft.utils

import com.example.mqttpanelcraft.model.Project

object HtmlTemplates {

    private const val CSS =
            """
        :root { --primary: #2196F3; --bg: #ffffff; --text: #333; --border: #ddd; --panel: #f9f9f9; }
        body { margin: 0; padding: 20px; font-family: sans-serif; background: var(--bg); color: var(--text); height: 100vh; overflow: hidden; display: flex; flex-direction: column; }
        h1 { color: var(--primary); margin: 0 0 20px; text-align: center; }
        
        /* Layout */
        .box { background: var(--panel); padding: 20px; border-radius: 12px; margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; }
        .row { display: flex; gap: 15px; width: 100%; margin-bottom: 20px; }
        
        /* Inputs */
        input[type="text"] { flex: 1; padding: 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 1rem; }
        button { padding: 12px 24px; background: var(--primary); color: #fff; border: 0; border-radius: 8px; font-weight: bold; font-size: 1rem; cursor: pointer; }
        
        /* Chat */
        #msg { background: var(--panel); padding: 15px; border-radius: 8px; min-height: 40px; border-left: 5px solid var(--primary); display: flex; align-items: center; margin-bottom: 20px; font-size: 1.1rem; }

        /* Switch */
        .switch { position: relative; width: 50px; height: 28px; }
        .switch input { opacity: 0; width: 0; height: 0; }
        .slider { position: absolute; inset: 0; background: #ccc; border-radius: 24px; transition: .3s; }
        .slider:before { content: ""; position: absolute; height: 20px; width: 20px; left: 4px; bottom: 4px; background: #fff; border-radius: 50%; transition: .3s; }
        input:checked + .slider { background: var(--primary); } 
        input:checked + .slider:before { transform: translateX(22px); }
        .led { width: 20px; height: 20px; background: #ccc; border-radius: 50%; border: 2px solid #bbb; margin-left: 20px; }
        .led.on { background: #00E676; box-shadow: 0 0 8px #00E676; border-color: #00C853; }

        /* Instruction Block */
        .instructions { 
            background: #E3F2FD; padding: 15px; border-radius: 12px; 
            font-size: 0.85rem; line-height: 1.4; color: #1565C0; 
            border: 1px solid #BBDEFB; margin-bottom: 20px;
        }
        .instructions b { color: #0D47A1; }

        /* === Penguin (CSS Only) === */
        .scene { position: fixed; bottom: 10px; left: 10px; width: 100px; height: 100px; z-index: -1; }
        .base { width: 40px; height: 4px; background: #90A4AE; position: absolute; bottom: 10px; left: 50px; }
        .screen { width: 4px; height: 30px; background: #81D4FA; opacity: 0.7; position: absolute; bottom: 12px; left: 85px; transform: rotate(10deg); }
        .penguin { position: absolute; bottom: 15px; left: 10px; width: 50px; height: 50px; }
        .body { width: 40px; height: 50px; background: #000; position: absolute; border-radius: 4px; }
        .belly { width: 25px; height: 38px; background: #fff; position: absolute; left: 8px; top: 8px; border-radius: 4px; }
        .eye { width: 8px; height: 8px; background: #4FC3F7; position: absolute; top: 9px; border-radius: 50%; animation: blink 4s infinite; border: 1px solid #000; box-shadow: inset 3px 0 0 0 #000; }
        .e-l { left: 8px; } .e-r { left: 26px; }
        .beak { width: 8px; height: 4px; background: #333; position: absolute; left: 18px; top: 18px; }
        .arm { width: 8px; height: 20px; background: #000; position: absolute; top: 20px; border-radius: 2px; }
        .a-l { left: -4px; transform-origin: top; } .a-r { left: 36px; top: 25px; height: 10px; width: 20px; }
        .status { position: absolute; top: -20px; left: 10px; font-family: monospace; font-weight: bold; color: #546E7A; width: 100px; text-align: center;}
        
        @keyframes type { from { transform: translateY(0); } to { transform: translateY(-3px); } }
        @keyframes firework { 0% { opacity: 1; transform: scale(1); } 100% { opacity: 0; transform: scale(3); } }
        @keyframes blink { 0%, 90%, 100% { transform: scaleY(1); } 95% { transform: scaleY(0.1); } }
        .happy .a-r { animation: type 0.2s infinite alternate; }
    """

    fun generateDefaultHtml(project: Project?): String {
        val defaultBase = if (project != null) "${project.name}/${project.id}" else "test/project"

        val jsLogic =
                """
            const T_BASE = (window.app) ? window.app.getBaseTopic() : "$defaultBase";
            const T_CHAT = T_BASE + "/chat";
            const T_SW = T_BASE + "/switch";
            const el = (id) => document.getElementById(id);
    
            function pub(t, v) {
                if (!window.mqtt) return;
                if (t === 'chat') { 
                    const i = el('inChat'); 
                    if(i.value) mqtt.publish(T_CHAT, i.value); 
                    i.value=''; 
                } else mqtt.publish(T_SW, v);
            }
    
            function mqttOnMessage(t, p) {
                if (t.includes("chat")) el('msg').innerText = p;
                if (t.includes("switch")) {
                    const on = (p === "ON" || p === "1" || p === "true");
                    el('sw').checked = on; 
                    el('led').className = on ? 'led on' : 'led';
                    el('p').className = on ? 'penguin happy' : 'penguin';
                }
            }
    
            setTimeout(() => { 
                if(window.mqtt) { 
                    mqtt.subscribe(T_CHAT); 
                    mqtt.subscribe(T_SW); 
                } 
            }, 1000);
        """

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MqttPanelCraft</title>
    <style>$CSS</style>
</head>
<body>
    <h1>Mqtt Panel Preview</h1>
    
    <!-- Info Section -->
    <div class="instructions">
        <b> Welcome to MqttPanelCraft!</b> This is a live preview of your dashboard.<br><br>
        <b>1. Create:</b> Click the <b>(i) icon</b> in the top toolbar to copy the <b>AI Prompt</b>. Paste it into ChatGPT or Claude to generate your unique dashboard code.<br>
        <b>2. Test Connectivity:</b> 
        <ul>
            <li><b>Publish:</b> Type a message and hit "SEND", or toggle the "Switch".</li>
            <li><b>Subscribe:</b> If your MQTT Broker is connected, the "Log" and "LED" will update instantly to reflect the current topic state. This verifies two-way communication.</li>
        </ul>
        <b>3. Pure CSS Art:</b> The Penguin at the bottom is a <b>CSS-only widget</b> (no images!). It animates based on MQTT status, showing how you can use CSS logic to visualize your IoT data.
    </div>

    <!-- Chat Test -->
    <div class="row">
        <input type="text" id="inChat" placeholder="Type test message...">
        <button onclick="pub('chat')">SEND</button>
    </div>
    <div id="msg">MQTT log will appear here...</div>

    <!-- Switch Test -->
    <div class="box">
        <span>MQTT Switch & Status</span>
        <div style="display:flex; align-items:center;">
            <label class="switch">
                <input type="checkbox" id="sw" onchange="pub('switch', this.checked ? 'ON' : 'OFF')">
                <span class="slider"></span>
            </label>
            <div id="led" class="led"></div>
        </div>
    </div>

    <!-- Mini Penguin (CSS Art Example) -->
    <div class="scene">
        <div id="st" class="status">LIVE</div>
        <div class="base"></div><div class="screen"></div>
        <div id="p" class="penguin">
            <div class="body"></div><div class="belly"></div>
            <div class="eye e-l"></div><div class="eye e-r"></div>
            <div class="beak"></div><div class="arm a-l"></div><div class="arm a-r"></div>
        </div>
    </div>

    <script>$jsLogic</script>
</body>
</html>
        """.trimIndent()
    }
}
