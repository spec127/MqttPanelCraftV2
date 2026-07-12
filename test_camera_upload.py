# -*- coding: utf-8 -*-
"""
MqttPanelCraft - CAM 元件影像上傳測試腳本 (Python)
支援 Base64 自動分片傳送 (CHUNK:index/total:data)
尺寸預設為 800x600 像素
"""

import sys
import time
import base64
import argparse
from io import BytesIO

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("請先安裝 paho-mqtt： pip install paho-mqtt")
    sys.exit(1)

try:
    from PIL import Image, ImageDraw, ImageFont
    HAS_PIL = True
except ImportError:
    HAS_PIL = False
    print("提示: 未安裝 Pillow (pip install pillow)，將使用內置預設測試圖案")

def create_test_image_base64(width=800, height=600):
    if HAS_PIL:
        img = Image.new("RGB", (width, height), color=(40, 53, 147))
        draw = ImageDraw.Draw(img)
        # 繪製裝飾邊框與文字
        draw.rectangle([10, 10, width-10, height-10], outline=(255, 255, 255), width=3)
        draw.line([0, 0, width, height], fill=(63, 81, 181), width=2)
        draw.line([0, height, width, 0], fill=(63, 81, 181), width=2)
        
        text = f"MqttPanelCraft CAM Test\n{width} x {height}\n{time.strftime('%Y-%m-%d %H:%M:%S')}"
        draw.text((40, 40), text, fill=(255, 255, 255))
        
        buffer = BytesIO()
        img.save(buffer, format="JPEG", quality=80)
        return base64.b64encode(buffer.getvalue()).decode('utf-8')
    else:
        # 1x1 紅色 JPEG Base64 備用
        return "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="

def send_image(broker, port, topic, b64_data, chunk_size=3000):
    client = mqtt.Client()
    print(f"正在連線至 MQTT Broker: {broker}:{port} ...")
    client.connect(broker, port, 60)
    client.loop_start()
    time.sleep(1)

    total_len = len(b64_data)
    print(f"準備發送影像至 Topic: {topic} (Base64 總長度: {total_len} 字元)")

    if total_len <= chunk_size:
        print("直接發送單包完整影像...")
        client.publish(topic, b64_data, qos=1)
    else:
        chunks = [b64_data[i:i+chunk_size] for i in range(0, total_len, chunk_size)]
        total_chunks = len(chunks)
        print(f"影像長度超過單次限制，切割為 {total_chunks} 包發送 (分片格式 CHUNK:index/total:data)...")
        for idx, chunk in enumerate(chunks, 1):
            payload = f"CHUNK:{idx}/{total_chunks}:{chunk}"
            client.publish(topic, payload, qos=1)
            print(f"  已發送分片 {idx}/{total_chunks} ({len(payload)} bytes)")
            time.sleep(0.05)  # 短暫間隔避免 Broker 壅塞

    time.sleep(1)
    client.loop_stop()
    client.disconnect()
    print("發送完成！")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="MqttPanelCraft CAM 影像上傳測試工具")
    parser.add_argument("--broker", default="broker.emqx.io", help="MQTT Broker 地址 (預設: broker.emqx.io)")
    parser.add_argument("--port", type=int, default=1883, help="MQTT Broker 埠號 (預設: 1883)")
    parser.add_argument("--topic", required=True, help="目標 CAM 元件的 Topic (例: myproject/1/cam1)")
    parser.add_argument("--file", help="欲發送的本地圖片路徑 (未指定則自動生成 800x600 測試圖)")
    parser.add_argument("--chunk-size", type=int, default=3000, help="單次分片最大字元數 (預設: 3000)")

    args = parser.parse_args()

    if args.file:
        with open(args.file, "rb") as f:
            b64_data = base64.b64encode(f.read()).decode('utf-8')
    else:
        b64_data = create_test_image_base64(800, 600)

    send_image(args.broker, args.port, args.topic, b64_data, args.chunk_size)
