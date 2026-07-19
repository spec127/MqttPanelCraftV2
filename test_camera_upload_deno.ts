import mqtt from "npm:mqtt";
import { encodeBase64 } from "jsr:@std/encoding/base64";

// 解析命令列參數
const args = Deno.args;
let broker = "mqtt://mqttgo.io";
let topic = "p50/yihj1zyj74/cam_1";
let filePath = `C:\\Users\\jinab\\OneDrive\\圖片\\Screenshots\\螢幕擷取畫面 2026-07-05 213356.png`;
const chunkSize = 3000;

for (let i = 0; i < args.length; i++) {
  if (args[i] === "--broker" && args[i + 1]) {
    broker = args[i + 1];
    if (!broker.startsWith("mqtt://") && !broker.startsWith("mqtts://")) {
      broker = "mqtt://" + broker;
    }
  } else if (args[i] === "--topic" && args[i + 1]) {
    topic = args[i + 1];
  } else if (args[i] === "--file" && args[i + 1]) {
    filePath = args[i + 1];
  }
}

try {
  console.log(`正在讀取圖片檔案: ${filePath}`);
  const fileBytes = await Deno.readFile(filePath);
  const b64Data = encodeBase64(fileBytes);
  console.log(`圖片讀取成功。Base64 長度: ${b64Data.length} 字元`);

  console.log(`正在連線至 MQTT Broker: ${broker} ...`);
  const client = mqtt.connect(broker);

  client.on("connect", async () => {
    console.log("MQTT 連線成功！");
    
    const totalLen = b64Data.length;
    if (totalLen <= chunkSize) {
      console.log(`直接發送單包完整影像至 ${topic}...`);
      await client.publishAsync(topic, b64Data, { qos: 1 });
    } else {
      const chunks = [];
      for (let i = 0; i < totalLen; i += chunkSize) {
        chunks.push(b64Data.substring(i, i + chunkSize));
      }
      const totalChunks = chunks.length;
      console.log(`影像長度超過單次限制，切割為 {totalChunks} 包發送...`);
      
      for (let idx = 1; idx <= totalChunks; idx++) {
        const payload = `CHUNK:${idx}/${totalChunks}:${chunks[idx - 1]}`;
        await client.publishAsync(topic, payload, { qos: 1 });
        console.log(`  已發送分片 ${idx}/${totalChunks} (${payload.length} bytes)`);
        await new Promise((resolve) => setTimeout(resolve, 50));
      }
    }
    
    console.log("發送完成！正在斷開連線...");
    client.end();
    setTimeout(() => Deno.exit(0), 1000);
  });

  client.on("error", (err) => {
    console.error("MQTT 連線出錯:", err);
    Deno.exit(1);
  });
} catch (e) {
  console.error("執行失敗:", e.message);
  Deno.exit(1);
}
