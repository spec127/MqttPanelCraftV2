#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MqttPanelCraft - 折線圖 (CHART) 元件 MQTT 測試腳本
支援單線數值傳輸與多線序列 (JSON 格式) 傳輸

相依套件安裝：
    pip install paho-mqtt
"""

import time
import math
import json
import argparse
import sys

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("請先安裝 paho-mqtt 套件： pip install paho-mqtt")
    sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description="MqttPanelCraft 折線圖元件測試發送工具")
    parser.add_argument("--broker", default="broker.emqx.io", help="MQTT Broker 地址 (預設: broker.emqx.io)")
    parser.add_argument("--port", type=int, default=1883, help="MQTT Broker 埠號 (預設: 1883)")
    parser.add_argument("--topic", default="home/chart/data", help="目標 CHART 元件訂閱的 Topic (預設: home/chart/data)")
    parser.add_argument("--mode", choices=["single", "multi"], default="multi", help="測試模式：single (單一數字) 或 multi (JSON 多序列，例如 value 與 series2)")
    parser.add_argument("--interval", type=float, default=1.0, help="發送時間間隔秒數 (預設: 1.0 秒)")
    args = parser.parse_args()

    print(f"[*] 正在連線至 MQTT Broker: {args.broker}:{args.port} ...")
    client = mqtt.Client()
    try:
        client.connect(args.broker, args.port, 60)
    except Exception as e:
        print(f"[!] 連線失敗: {e}")
        sys.exit(1)

    client.loop_start()
    print(f"[+] 連線成功！準備持續發送測試數據至 Topic: {args.topic}")
    print(f"[*] 測試模式: {args.mode.upper()} (按 Ctrl+C 可停止發送)")

    step = 0
    try:
        while True:
            # 使用正弦波與餘弦波模擬真實物聯網動態數據 (如溫度、濕度、電壓波動)
            val1 = round(50 + 30 * math.sin(step * 0.3) + (step % 5), 1)
            val2 = round(40 + 25 * math.cos(step * 0.25), 1)
            val3 = round(60 + 15 * math.sin(step * 0.5), 1)

            if args.mode == "single":
                # 單純發送單浮點數 (對應單線序列)
                payload = str(val1)
            else:
                # 發送 JSON 格式多序列數據 (預設對應 series_key_1=value, series_key_2=series2, series_key_3=series3)
                payload = json.dumps({
                    "value": val1,
                    "series2": val2,
                    "series3": val3,
                    "t": time.strftime("%H:%M:%S")
                })

            client.publish(args.topic, payload, qos=0)
            print(f"[{time.strftime('%H:%M:%S')}] 已發送至 {args.topic} -> {payload}")
            
            step += 1
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("\n[*] 接收到停止指令，關閉連線中...")
    finally:
        client.loop_stop()
        client.disconnect()
        print("[+] 測試腳本已結束")

if __name__ == "__main__":
    main()
