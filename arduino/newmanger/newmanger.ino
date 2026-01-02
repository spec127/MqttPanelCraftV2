/**
 * ESP32/ESP8266 Main Sketch
 * 檔名: newmanger.ino
 * 
 * 使用 mqttpanel.h 模組，但設定權都在這裡！
 */

#include <Arduino.h>
#include "mqttpanel.h" 

// --- Platform Specifics ---
#if defined(ESP32)
  #include <WiFi.h>
#elif defined(ESP8266)
  #include <ESP8266WiFi.h>
#endif

// --- 1. 使用者設定區 (User Config) ---
// 這裡定義預設值。如果 LittleFS 有存檔，會優先讀取檔案內的數值。
char mqtt_server[40] = "your mqtt broker address";
char mqtt_port[6]    = "1883";
char mqtt_topic[40]  = "test_topic";

// 按鈕設定 (秒)
#define BTN_PORTAL_SEC 3   // 按 3 秒進設定頁面
#define BTN_RESET_SEC  10  // 按 10 秒恢復原廠
#define CHECK_WIFI_SEC 20  // WiFi 斷線 20 秒重啟 AP

// 硬體腳位
#define TRIGGER_PIN 0  // 按鈕 (Boot)
#define LED_PIN     4  // 指示燈

// --- Objects ---
WiFiClient espClient;
PubSubClient client(espClient);

// --- 接收函式宣告 ---
void mq_receiver(String topic, String msg);

// --- Setup ---
void setup() {
  // 1. Hardware Init (使用者的要求)
  Serial.begin(115200);
  Serial.println("\n[MP] System Booting...");
  pinMode(TRIGGER_PIN, INPUT_PULLUP);
  pinMode(LED_PIN, OUTPUT);

  // 2. 啟動 MQTT Panel
  // 把我們的變數指標傳進去，讓模組幫我們填值或儲存
  mqttpanel_begin(&client, mq_receiver, 
           mqtt_server, mqtt_port, mqtt_topic,
           BTN_PORTAL_SEC, BTN_RESET_SEC,
           TRIGGER_PIN, LED_PIN,
           CHECK_WIFI_SEC);
           
  Serial.println("[Main] System Configured:");
  Serial.printf(" - Server: %s\n", mqtt_server); // 這裡已經是最終確認的數值了
  Serial.printf(" - Port:   %s\n", mqtt_port);
  Serial.printf(" - Topic:  %s\n", mqtt_topic);
}

// --- Loop ---
void loop() {
  mqttpanel_loop(); // 模組會自動處理 WiFi 保活、按鈕偵測

  // --- 您的邏輯 ---
  static unsigned long lastTime = 0;
  if (millis() - lastTime > 10000) {
     lastTime = millis();
     
     if (mqttpanel_is_connected()) {
        String topic = String(mqtt_topic) + "/status";
        mqttpanel_pub(topic, "Running...");
     }
  }
}

// --- 接收回呼 ---
void mq_receiver(String topic, String msg) {
  Serial.println("[RX] " + topic + " : " + msg);
  
  if (msg == "RED") {
     // digitalWrite(LED_PIN, HIGH);
  }
}
