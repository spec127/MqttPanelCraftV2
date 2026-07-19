/**
 * MqttPanelCraft - 折線圖 (CHART) 元件 MQTT Node.js 測試腳本
 * 由於系統中未檢測到 Python 執行環境，本腳本使用 Node.js 進行測試。
 * 支援單線數值傳輸與多線序列 (JSON 格式) 傳輸。
 *
 * 使用說明與相依安裝：
 *   npm install mqtt minimist
 *   node test_chart_mqtt.js --topic "home/chart/data" --mode multi
 */

const mqtt = require('mqtt');

// 解析命令列參數
const args = process.argv.slice(2);
function getArg(name, defaultValue) {
    const idx = args.indexOf(`--${name}`);
    if (idx !== -1 && idx + 1 < args.length) {
        return args[idx + 1];
    }
    return defaultValue;
}

const broker = getArg('broker', 'broker.emqx.io');
const port = parseInt(getArg('port', '1883'), 10);
const topic = getArg('topic', 'home/chart/data');
const mode = getArg('mode', 'multi'); // 'single' 或 'multi'
const interval = parseFloat(getArg('interval', '1.0')) * 1000;

const brokerUrl = `mqtt://${broker}:${port}`;
console.log(`[*] 正在連線至 MQTT Broker: ${brokerUrl} ...`);

const client = mqtt.connect(brokerUrl);

client.on('connect', () => {
    console.log(`[+] 連線成功！準備持續發送測試數據至 Topic: ${topic}`);
    console.log(`[*] 測試模式: ${mode.toUpperCase()} (按 Ctrl+C 可停止發送)\n`);

    let step = 0;
    setInterval(() => {
        // 使用正弦波與餘弦波模擬真實物聯網動態數據 (如溫度、濕度、電壓波動)
        const val1 = Math.round((50 + 30 * Math.sin(step * 0.3) + (step % 5)) * 10) / 10;
        const val2 = Math.round((40 + 25 * Math.cos(step * 0.25)) * 10) / 10;
        const val3 = Math.round((60 + 15 * Math.sin(step * 0.5)) * 10) / 10;

        let payload = '';
        if (mode === 'single') {
            payload = String(val1);
        } else {
            const now = new Date();
            const timeStr = now.toTimeString().split(' ')[0];
            payload = JSON.stringify({
                value: val1,
                series2: val2,
                series3: val3,
                t: timeStr
            });
        }

        client.publish(topic, payload, { qos: 0 });
        const timeLog = new Date().toTimeString().split(' ')[0];
        console.log(`[${timeLog}] 已發送至 ${topic} -> ${payload}`);

        step++;
    }, interval);
});

client.on('error', (err) => {
    console.error(`[!] MQTT 連線錯誤: ${err.message}`);
    process.exit(1);
});
