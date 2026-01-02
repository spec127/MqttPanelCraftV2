#include "sys_manager.h"
#include "style.h" // 引入我們漂亮的 CSS 樣式

// --- Platform Includes (根據晶片型號引入不同函式庫) ---
#ifdef ESP32
  #include <WiFi.h>
  #include <FS.h>
  #include <LittleFS.h>
#elif defined(ESP8266)
  #include <ESP8266WiFi.h>
  #include <LittleFS.h>
#endif

#include <WiFiManager.h> // 管理 WiFi 連線的核心
#include <ArduinoJson.h> // 用來存取設定檔

// --- Global Definitions (全域變數定義) ---
// 這裡真正分配記憶體給這些變數
char mqtt_server[40] = "broker.hivemq.com";
char mqtt_port[6] = "1883";
char mqtt_topic[40] = "test/topic";

// 內部用的旗標
bool shouldSaveConfig = false;

// --- Private Prototypes (內部函式宣告) ---
// 這些函式前面加了底線，代表建議只在這個檔案內部使用
void _loadConfig();           // 讀檔
void _saveConfig();           // 存檔
void _saveConfigCallback();   // WiFiManager 存檔通知

// --- API Implementation (實作公開函式) ---

void sys_begin() {
  Serial.begin(115200);
  Serial.println("\n\n[System] Booting...");

  pinMode(SYS_TRIGGER_PIN, INPUT_PULLUP); // 設定按鈕
  pinMode(SYS_LED_PIN, OUTPUT);           // 設定 LED
  
  // 1. 掛載檔案系統 (LittleFS)
  if (LittleFS.begin()) {
    Serial.println("[FS] Mounted");
    _loadConfig(); // 如果掛載成功，就讀取設定
  } else {
    Serial.println("[FS] Failed, formatting...");
    #ifdef ESP32
      LittleFS.format(); // 第一次使用可能需要格式化
    #endif
    if(LittleFS.begin()) Serial.println("[FS] Formatted.");
  }

  // 1.5 開機救援模式 (Boot Button Rescue)
  // 如果開機瞬間按著按鈕不放
  if (digitalRead(SYS_TRIGGER_PIN) == LOW) {
    Serial.println("[System] Boot Button Detected!");
    delay(2000);
    // 兩秒後還按著...
    if (digitalRead(SYS_TRIGGER_PIN) == LOW) {
       Serial.println("[System] Erasing Settings...");
       WiFiManager wm;
       wm.resetSettings(); // 清除 WiFi
       LittleFS.remove("/config.json"); // 清除設定檔
       
       // LED 快閃給回應
       for(int i=0; i<10; i++) { 
         digitalWrite(SYS_LED_PIN, !digitalRead(SYS_LED_PIN)); 
         delay(100); 
       }
       Serial.println("[System] Reset Done. Restarting...");
       ESP.restart(); // 重開機
    }
  }

  // 2. WiFiManager 設定
  WiFiManager wm;
  wm.setCustomHeadElement(custom_style); // 注入 CSS 美化
  wm.setSaveConfigCallback(_saveConfigCallback); // 設定存檔回呼

  // 定義自訂參數 (Server, Port, Topic)
  WiFiManagerParameter p_server("server", "MQTT Server", mqtt_server, 40);
  WiFiManagerParameter p_port("port", "MQTT Port", mqtt_port, 6);
  WiFiManagerParameter p_topic("topic", "MQTT Topic", mqtt_topic, 40);

  wm.addParameter(&p_server);
  wm.addParameter(&p_port);
  wm.addParameter(&p_topic);
  
  wm.setConnectTimeout(30); // 30秒連不上就放棄

  // 開始自動連線 (AP 名稱: Antigravity_Setup)
  if (!wm.autoConnect("Antigravity_Setup")) {
    Serial.println("[WiFi] Failed to connect.");
    delay(3000);
    ESP.restart(); // 連不上就重開機再試
  }

  Serial.println("[WiFi] Connected!");
  
  // 連線成功後，更新變數值
  strcpy(mqtt_server, p_server.getValue());
  strcpy(mqtt_port, p_port.getValue());
  strcpy(mqtt_topic, p_topic.getValue());

  // 如果有更動，寫入檔案
  if (shouldSaveConfig) _saveConfig();
}

void sys_loop() {
  // 檢查按鈕邏輯 (On-Demand Portal)
  static unsigned long pressStart = 0;
  
  if (digitalRead(SYS_TRIGGER_PIN) == LOW) {
    // 按下中...
    if (pressStart == 0) pressStart = millis(); // 開始計時
    unsigned long duration = millis() - pressStart;
    
    // 根據按下時間改變 LED 閃爍速度 (越久越快)
    int blinkSpeed = (duration > 10000) ? 50 : (duration > 3000 ? 200 : 1000);
    digitalWrite(SYS_LED_PIN, (millis() / blinkSpeed) % 2);

  } else {
    // 放開按鈕...
    if (pressStart > 0) {
      unsigned long duration = millis() - pressStart;
      pressStart = 0; // 重置

      if (duration > 10000) {
        // 按超過 10 秒 -> 恢復原廠
        Serial.println("[System] FACTORY RESET!");
        WiFiManager wm;
        wm.resetSettings();
        LittleFS.remove("/config.json");
        digitalWrite(SYS_LED_PIN, LOW); delay(2000);
        ESP.restart();
      } 
      else if (duration > 3000) {
        // 按超過 3 秒 -> 開啟設定入口 (On-Demand Portal)
        Serial.println("[System] Starting On-Demand Portal...");
        WiFiManager wm;
        wm.setCustomHeadElement(custom_style); // 別忘了 CSS
        
        // 重新加入參數，不然手動模式看不到欄位
        WiFiManagerParameter p_server("server", "MQTT Server", mqtt_server, 40);
        WiFiManagerParameter p_port("port", "MQTT Port", mqtt_port, 6);
        WiFiManagerParameter p_topic("topic", "MQTT Topic", mqtt_topic, 40);
        
        wm.addParameter(&p_server);
        wm.addParameter(&p_port);
        wm.addParameter(&p_topic);
        wm.setSaveConfigCallback(_saveConfigCallback);

        // 啟動 AP (名稱: Antigravity_OnDemand)
        if (!wm.startConfigPortal("Antigravity_OnDemand")) {
          Serial.println("[WiFi] Portal timeout.");
          ESP.restart();
        } else {
           // 設定完成，更新變數
           strcpy(mqtt_server, p_server.getValue());
           strcpy(mqtt_port, p_port.getValue());
           strcpy(mqtt_topic, p_topic.getValue());
           if (shouldSaveConfig) _saveConfig();
           Serial.println("[WiFi] Portal done.");
        }
      }
    }
  }
}

// 簡單回傳 WiFi 狀態
bool sys_wifi_connected() {
  return WiFi.status() == WL_CONNECTED;
}

// --- Private Helpers (內部輔助函式) ---

void _saveConfigCallback() {
  shouldSaveConfig = true; // 標記需要存檔
}

// 從 config.json 讀取設定
void _loadConfig() {
  if (LittleFS.exists("/config.json")) {
    File configFile = LittleFS.open("/config.json", "r");
    if (configFile) {
      JsonDocument doc;
      DeserializationError error = deserializeJson(doc, configFile);
      if (!error) {
        // 使用 strlcpy 安全地複製字串
        strlcpy(mqtt_server, doc["mqtt_server"] | "", sizeof(mqtt_server));
        strlcpy(mqtt_port, doc["mqtt_port"] | "", sizeof(mqtt_port));
        strlcpy(mqtt_topic, doc["mqtt_topic"] | "", sizeof(mqtt_topic));
      }
      configFile.close();
    }
  }
}

// 寫入設定到 config.json
void _saveConfig() {
  JsonDocument doc;
  doc["mqtt_server"] = mqtt_server;
  doc["mqtt_port"] = mqtt_port;
  doc["mqtt_topic"] = mqtt_topic;

  File configFile = LittleFS.open("/config.json", "w");
  if (!configFile) return;
  serializeJson(doc, configFile);
  configFile.close();
}
