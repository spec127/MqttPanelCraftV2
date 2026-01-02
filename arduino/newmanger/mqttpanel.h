#ifndef MQTTPANEL_H
#define MQTTPANEL_H

#include <Arduino.h>
#include <PubSubClient.h>

// --- Callback Type ---
typedef void (*MqttCallback)(String topic, String msg);

// --- API ---

/**
 * 系統初始化
 * @param client: PubSubClient 物件
 * @param cb: 您的接收函式
 * @param srv: 存放 Server 的變數指標
 * @param port: 存放 Port 的變數指標
 * @param topic: 存放 Topic 的變數指標
 * @param portal_sec: 按幾秒進入設定頁面? (例如 3)
 * @param factory_sec: 按幾秒恢復原廠? (例如 10)
 * @param trigger_pin: 按鈕腳位 (e.g. 0)
 * @param led_pin: LED 腳位 (e.g. 4)
 * @param check_wifi_sec: WiFi 斷線幾秒後重啟 AP (e.g. 20)
 */
void mqttpanel_begin(PubSubClient* client, MqttCallback cb, 
              char* srv, char* port, char* topic,
              int portal_sec, int factory_sec,
              int trigger_pin, int led_pin,
              int check_wifi_sec);

// 系統迴圈 (必須在 loop 呼叫)
void mqttpanel_loop();

// MQTT 操作
void mqttpanel_pub(String topic, String payload);
void mqttpanel_sub(String topic);

// 狀態查詢
bool mqttpanel_is_connected();

#endif
