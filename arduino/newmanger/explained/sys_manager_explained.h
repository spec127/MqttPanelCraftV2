#ifndef SYS_MANAGER_H
#define SYS_MANAGER_H
// 這是 Header Guard，防止重複引用導致編譯錯誤

#include <Arduino.h>

// --- Configuration (硬體設定) ---
// 這裡定義腳位，方便以後如果要改板子，只要改這裡就好
#define SYS_TRIGGER_PIN 0  // BOOT 按鈕 (通常是 GPIO 0)
#define SYS_LED_PIN 4      // 內建 LED (通常是 GPIO 4)

// --- Shared Globals (共享的全域變數) ---
// extern 關鍵字的意思是：「這些變數是在別的地方 (sys_manager.cpp) 定義的，
// 但我允許大家 (例如 main.cpp) 也可以讀取或修改它們。」
extern char mqtt_server[40]; // 存放 MQTT Server 網址
extern char mqtt_port[6];    // 存放 MQTT Port
extern char mqtt_topic[40];  // 存放 MQTT Topic

// --- API (對外公開的函式) ---

// 1. 系統初始化
// 請在 setup() 的第一行呼叫它。
// 它會負責：掛載檔案系統、檢查是否要重置、連線 WiFi。
void sys_begin();

// 2. 系統迴圈檢查
// 請在 loop() 裡持續呼叫它。
// 它會負責：偵測按鈕長按，來決定是否要進入設定頁面或恢復原廠。
void sys_loop();

// 3. 輔助函式 (選用)
// 簡單告訴你現在 WiFi 是不是連線中
bool sys_wifi_connected();

#endif
