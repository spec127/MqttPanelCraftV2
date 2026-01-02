/**
 * ESP32/ESP8266 Universal IoT Configuration Portal (重構解說版)
 * 
 * 這是「重構後」的主程式。
 * 您會發現這裡變得很乾淨，因為所有的連線雜事都丟給 `sys_manager` 去了。
 */

#include <Arduino.h>
#include "sys_manager.h" // 系統管理員 (負責 WiFi, FS, 按鈕)
#include "mqttpanel.h"   // 應用面板 (負責 App 邏輯)

// --- Platform Specifics (判斷晶片) ---
#if defined(ESP32)
  #include <WiFi.h>
#elif defined(ESP8266)
  #include <ESP8266WiFi.h>
#endif

// --- Objects (建立物件) ---
WiFiClient espClient;
PubSubClient client(espClient);

// --- Demo Variables (您的應用變數) ---
bool demoSwitch = false;          // 開關
int demoDimmer = 50;              // 調光器
int demoSelect = 0;               // 選單
String demoText = "Hello Refactor"; // 文字

// --- Helper Functions (函式宣告) ---
void setupMqttChannels();  // 設定 MQTT 通道
void mqttReconnect();      // 負責斷線重連
void networkTask(void* p); // 雙核心用的 Task (Core 0)

// --- Main Setup (開機初始化) ---
void setup() {
  // 1. 初始化系統 (WiFi, FS, 按鈕)
  // 這行就搞定一切硬體與網路設定了！
  sys_begin(); 

  // 2. 設定 MQTT Client
  // 注意：mqtt_server 和 mqtt_port 是從 sys_manager 借來用的 (extern)
  int port = atoi(mqtt_port);
  client.setServer(mqtt_server, port);

  // 3. 啟動雙核心任務 (僅限 ESP32)
  #ifdef ESP32
    Serial.println("[System] ESP32 Dual-Core Mode: Active");
    // 建立一個 Task 叫做 "NetTask"，跑在核心 0
    xTaskCreatePinnedToCore(networkTask, "NetTask", 8192, NULL, 1, NULL, 0);
  #else
    Serial.println("[System] ESP8266 Single-Core Mode");
  #endif
}

// --- Main Loop (主程式迴圈) ---
// 這裡跑在核心 1 (Core 1)
void loop() {
  // 1. 每一圈都要檢查系統事件 (例如有沒有人長按 Reset 鍵)
  sys_loop();

  // 2. 如果是 ESP8266 (單核心)，必須在這裡處理網路
  #ifdef ESP8266
  if (sys_wifi_connected()) {
      if (!client.connected()) mqttReconnect();
      mqttpanel_loop(); // 處理 MQTT 訊息
  }
  #endif

  // --- USER LOGIC HERE (您的程式碼) ---
  // 這裡變得超乾淨！！！
  // 您可以直接寫您的邏輯，例如：
  
  /*
  if (demoSwitch) {
     digitalWrite(...); // 開燈
  }
  */
}


// --- Network Task (網路任務 - Core 0) ---
// 這裡專門負責「保持連線」，不會被主程式卡住
#ifdef ESP32
void networkTask(void* p) {
  Serial.println("[NetTask] Started on Core 0");
  static unsigned long lastPub = 0;

  while(1) { // 無窮迴圈
       if (sys_wifi_connected()) { // 如果 WiFi 有通
           if (!client.connected()) {
               mqttReconnect(); // 沒通 MQTT 就重連
           }
           
           mqttpanel_loop(); // 處理 App 傳來的指令

           // 心跳包 (每 3 秒發一次)
           if (millis() - lastPub > 3000) {
              lastPub = millis();
              if (client.connected()) {
                String hb = "Core0_HB: " + String(millis());
                client.publish(mqtt_topic, hb.c_str());
              }
           }
       }
       vTaskDelay(10 / portTICK_PERIOD_MS); // 休息一下，避免 CPU 過熱當機
  }
}
#endif

// --- Connectivity Logic (連線邏輯) ---
void mqttReconnect() {
  if (!client.connected()) {
    Serial.print("[MQTT] Connecting...");
    String clientId = "ESP32-" + String(random(0xffff), HEX); // 隨機 ID
    
    if (client.connect(clientId.c_str())) {
      Serial.println("done.");
      setupMqttChannels(); // 【重點】連上後，立刻重新註冊所有變數
    } else {
      Serial.print("fail rc=");
      Serial.print(client.state());
      Serial.println();
      #ifdef ESP32
      vTaskDelay(2000 / portTICK_PERIOD_MS); // Task 內用 vTaskDelay
      #else
      delay(2000); // 傳統 delay
      #endif
    }
  }
}

// 設定 MQTT 通道
void setupMqttChannels() {
  mqttpanel_begin(&client); // 啟動面板
  
  // 自動產生前綴: "test/topic" -> "test/topic"
  String prefix = String(mqtt_topic);
  if (prefix.endsWith("/")) prefix.remove(prefix.length()-1);
  
  // 註冊變數 (綁定到 .../switch/1/set 等 Topic)
  mqttpanel_switch_sub((prefix + "/switch/1/set").c_str(), &demoSwitch);
  mqttpanel_dimmer_sub((prefix + "/dimmer/1/set").c_str(), &demoDimmer);
  mqttpanel_select_sub((prefix + "/select/1/set").c_str(), &demoSelect);
  mqttpanel_text_sub((prefix + "/text/1/set").c_str(), &demoText);
  mqttpanel_sync_sub((prefix + "/sync/1/set").c_str());
  
  // 註冊完立刻回報一次現況
  mqttpanel_publish_all_vals();
  Serial.println("[MP] Channels Ready");
}
