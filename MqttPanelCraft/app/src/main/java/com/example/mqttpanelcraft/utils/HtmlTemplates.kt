package com.example.mqttpanelcraft.utils

import com.example.mqttpanelcraft.model.Project

object HtmlTemplates {

    private const val CSS = """
        :root { --primary: #2196F3; --bg: #ffffff; --text: #333; --border: #ddd; --panel: #f9f9f9; }
        body { margin: 0; padding: 20px; font-family: sans-serif; background: var(--bg); color: var(--text); height: 100vh; overflow: hidden; display: flex; flex-direction: column; }
        h1 { color: var(--primary); margin: 0 0 30px; text-align: center; }
        
        /* Layout - Spacing Increased */
        .box { background: var(--panel); padding: 20px; border-radius: 12px; margin-bottom: 25px; display: flex; justify-content: space-between; align-items: center; }
        .row { display: flex; gap: 15px; width: 100%; margin-bottom: 30px; }
        
        /* Inputs */
        input[type="text"] { flex: 1; padding: 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 1rem; }
        button { padding: 12px 24px; background: var(--primary); color: #fff; border: 0; border-radius: 8px; font-weight: bold; font-size: 1rem; }
        
        /* Chat */
        #msg { background: var(--panel); padding: 15px; border-radius: 8px; min-height: 50px; border-left: 5px solid var(--primary); display: flex; align-items: center; margin-bottom: 30px; font-size: 1.1rem; }

        /* Switch */
        .switch { position: relative; width: 50px; height: 28px; }
        .switch input { opacity: 0; width: 0; height: 0; }
        .slider { position: absolute; inset: 0; background: #ccc; border-radius: 24px; transition: .3s; }
        .slider:before { content: ""; position: absolute; height: 20px; width: 20px; left: 4px; bottom: 4px; background: #fff; border-radius: 50%; transition: .3s; }
        input:checked + .slider { background: var(--primary); } 
        input:checked + .slider:before { transform: translateX(22px); }
        .led { width: 20px; height: 20px; background: #ccc; border-radius: 50%; border: 2px solid #bbb; margin-left: 20px; }
        .led.on { background: #00E676; box-shadow: 0 0 8px #00E676; border-color: #00C853; }

        /* File Preview Area */
        #preview { 
            margin-top: 20px; text-align: center; min-height: 140px; 
            border: 2px dashed var(--border); border-radius: 12px; 
            display: flex; flex-direction: column; justify-content: center; align-items: center;
            color: #999; font-weight: bold; font-size: 1rem;
            overflow: hidden;
        }
        #preview img { max-width: 100%; max-height: 120px; border-radius: 8px; margin-top: 10px; }
        #preview .fname { 
            color: #333; margin-bottom: 8px; display: block; 
            white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 90%;
        }

        /* === Minimal Penguin (No Furniture) === */
        /* Scale 1.0, Z-Index -1 (Background) */
        .scene { position: fixed; bottom: 10px; left: 10px; width: 100px; height: 100px; transform: scale(1.0); pointer-events: none; z-index: -1; }
        .base { width: 40px; height: 4px; background: #90A4AE; position: absolute; bottom: 10px; left: 50px; }
        .screen { width: 4px; height: 30px; background: #81D4FA; opacity: 0.7; position: absolute; bottom: 12px; left: 85px; transform: rotate(10deg); }
        .penguin { position: absolute; bottom: 15px; left: 10px; width: 50px; height: 50px; }
        .body { width: 40px; height: 50px; background: #000; position: absolute; border-radius: 4px; }
        .belly { width: 25px; height: 38px; background: #fff; position: absolute; left: 8px; top: 8px; border-radius: 4px; }
        
        /* vivid Eyes */
        .eye { width: 8px; height: 8px; background: #4FC3F7; position: absolute; top: 9px; border-radius: 50%; animation: blink 4s infinite; box-shadow: inset -3px 0 0 0 #000; z-index: 1; border: 1px solid #000; }
        .e-l { left: 8px; } .e-r { left: 26px; }
        
        .beak { width: 8px; height: 4px; background: #333; position: absolute; left: 18px; top: 18px; }
        .arm { width: 8px; height: 20px; background: #000; position: absolute; top: 20px; border-radius: 2px; }
        .a-l { left: -4px; transform-origin: top; } .a-r { left: 36px; top: 25px; height: 10px; width: 20px; }
        .status { position: absolute; top: -20px; left: 10px; font-family: monospace; font-weight: bold; color: #546E7A; width: 100px; text-align: center;}

        @keyframes type { from { transform: translateY(0); } to { transform: translateY(-3px); } }
        @keyframes shake { 0%, 100% { transform: translateX(0); } 25% { transform: translateX(-2px); } 75% { transform: translateX(2px); } }
        @keyframes zzz { 0% { opacity: 0; transform: translateY(0); } 100% { opacity: 0; transform: translateY(-15px); } 50% { opacity: 1; } }
        @keyframes blink { 0%, 90%, 100% { transform: scaleY(1); } 95% { transform: scaleY(0.1); } }
        
        .happy .a-r { animation: type 0.2s infinite alternate; } .happy .eye { background: #4FC3F7; }
        .mad { animation: shake 0.1s infinite; } .mad .eye { top: 12px; height: 3px; width: 9px; background: #ff5252; box-shadow: inset -3px 0 0 0 #000; animation: none; }
        .sleep .eye { height: 1px; top: 14px; width: 8px; background: #000; border-radius: 0; box-shadow: none; animation: none; } .sleep::after { content: 'Zzz'; position: absolute; top: -15px; right: -5px; animation: zzz 2s infinite; font-weight: bold; color: #555; }
        .super-mad .body { background: #FF5252; } .super-mad { animation: shake 0.05s infinite; } .super-mad .eye { background: #d50000; transform: scale(1.1); box-shadow: inset -3px 0 0 0 #000; }
        .firework::before { content: '!'; position: absolute; top: -30px; left: 10px; color: red; font-size: 20px; font-weight: bold; } .firework .eye { height: 9px; width: 8px; top: 7px; background: #03A9F4; box-shadow: inset -3px 0 0 0 #000, inset 2px -2px 0 #fff; }
    """

    fun generateDefaultHtml(project: Project?): String {
        val baseTopic = if (project != null) "${project.name}/${project.id}" else "test/project"
        
        // JS Logic split to prevent Kotlin String Template issues and size limits
        val jsLogic = """
            const T_BASE = "$baseTopic";
            const T_CHAT = T_BASE + "/chat";
            const T_SW = T_BASE + "/switch";
            const el = (id) => document.getElementById(id);
    
            function pub(t, v) {
                if (!window.mqtt) return;
                if (t === 'chat') { const i = el('inChat'); if(i.value) mqtt.publish(T_CHAT, i.value); i.value=''; }
                else mqtt.publish(T_SW, v);
            }
    
            function receiveFile(n, t, d) {
                const p = el('preview'); p.innerHTML = ''; p.style.border = '2px solid #ddd'; // Solid border on load
                if(t.startsWith('image')) {
                     // Use concatenation to avoid Kotlin string template issues context
                    p.innerHTML = '<span class="fname">' + n + '</span><img src="' + d + '">';
                } else {
                    p.innerHTML = '<span class="fname">' + n + '</span>';
                }
            }
    
            function mqttOnMessage(t, p) {
                if (t.includes("chat")) el('msg').innerText = p;
                if (t.includes("switch")) {
                    const on = (p === "ON" || p === "1" || p === "true");
                    el('sw').checked = on; el('led').className = on ? 'led on' : 'led';
                }
            }
    
            // Loop: Code -> Angry -> Sleep -> Code -> Happy
            const seq = [
                { t: 'CODING', c: 'happy' }, { t: 'ANGRY', c: 'super-mad' },
                { t: 'SLEEP', c: 'sleep' }, { t: 'CODING', c: 'happy' }, { t: 'HAPPY', c: 'firework' }
            ];
            let i = 0;
            setInterval(() => {
                const s = seq[i];
                el('st').innerText = s.t; el('p').className = 'penguin ' + s.c;
                i = (i + 1) % seq.length;
            }, 2000);
    
            setTimeout(() => { if(window.mqtt) { mqtt.subscribe(T_CHAT); mqtt.subscribe(T_SW); } }, 1000);
        """

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MqttPanelCraft</title>
    <style>
        $CSS
    </style>
</head>
<body>
    <h1>MqttPanelCraft</h1>
    
    <!-- Chat -->
    <div class="row">
        <input type="text" id="inChat" placeholder="Type message...">
        <button onclick="pub('chat')">SEND</button>
    </div>
    <div id="msg">Waiting...</div>

    <!-- Controls -->
    <div class="box">
        <span>System Switch</span>
        <div style="display:flex; align-items:center;">
            <label class="switch"><input type="checkbox" id="sw" onchange="pub('switch', this.checked ? 'ON' : 'OFF')"><span class="slider"></span></label>
            <div id="led" class="led"></div>
        </div>
    </div>
    
    <!-- File Preview -->
    <div id="preview">
        <div>Load File Area</div>
        <div style="font-size:0.8rem; font-weight:normal;">(Click Upload Icon above)</div>
    </div>

    <!-- Mini Penguin Scene -->
    <div class="scene">
        <div id="st" class="status">INIT</div>
        <div class="base"></div><div class="screen"></div>
        <div id="p" class="penguin">
            <div class="body"></div><div class="belly"></div>
            <div class="eye e-l"></div><div class="eye e-r"></div>
            <div class="beak"></div><div class="arm a-l"></div><div class="arm a-r"></div>
        </div>
    </div>

    <script>
        $jsLogic
    </script>
</body>
</html>
        """.trimIndent()
    }
}