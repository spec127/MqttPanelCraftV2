#ifndef MQTTPANEL_H
#define MQTTPANEL_H
// 上面這兩行是「Header Guard」(防護罩)。
// 防止這個檔案被重複引用(Include)兩次以上導致編譯錯誤。
// 如果 MQTTPANEL_H 還沒被定義過，就定義它，並執行下面的內容。

#include <Arduino.h>      // 引入 Arduino 核心庫 (為了能用 String, bool 等類型)
#include <PubSubClient.h> // 引入 MQTT 函式庫 (為了能操作 PubSubClient 物件)

// --- Configuration (設定區) ---
#define MPTP_MAX_CHANNELS 24   // 定義最大通道數：最多能註冊 24 個變數 (Switch/Dimmer...)
#define MPTP_MAX_TOPIC_LEN 120 // 定義 Topic 最大長度：避免 Topic 太長導致記憶體爆掉

// --- API (介面區) ---
// 這邊只宣告函數的「長相」(名字、參數、回傳值)，不寫具體邏輯。

class PubSubClient; // 前向宣告 (Forward Declaration)：告訴編譯器「有 PubSubClient 這個類別」，細節之後再說。

// 初始化函式：在 setup() 裡呼叫，把 MQTT client 的指揮權交給這個模組
void mqttpanel_begin(PubSubClient* client);

// 迴圈函式：在 loop() 裡呼叫，讓模組能持續檢查有沒有收到 MQTT 訊息
void mqttpanel_loop();

// --------------------------------------------------------------------------
// Subscription API (訂閱/註冊通道)
// 這些函式是用來把您的變數 (bool, int, String) 跟一個 Topic 綁定在一起。
// --------------------------------------------------------------------------

// 註冊開關 (Switch)
// topicSet: 監聽的 Topic (例如 ".../switch/1/set")
// varBool: 指向您程式中 bool 變數的指標 (地址)。當收到 "1" 時自動把該變數變 true。
bool mqttpanel_switch_sub(const char* topicSet, bool* varBool);

// 註冊調光器 (Dimmer)
// varInt: 指向 int 變數。收到 "50" 會把變數改成 50 (範圍限制 0-100)。
bool mqttpanel_dimmer_sub(const char* topicSet, int* varInt);

// 註冊選單 (Select)
// varInt: 指向 int 變數。收到 "0", "1", "2"... 自動更新。
bool mqttpanel_select_sub(const char* topicSet, int* varInt);

// 註冊文字 (Text)
// varString: 指向 String 變數。收到什麼文字就存進去。
bool mqttpanel_text_sub(const char* topicSet, String* varString);

// 註冊同步訊號 (Sync)
// 這是特殊的，沒有綁定變數。收到訊號後會自動觸發「全體廣播」。
bool mqttpanel_sync_sub(const char* topicSet);

// --------------------------------------------------------------------------
// Publish API (發布/回報狀態)
// 當您的變數改變時 (例如手動按了開關)，呼叫這些函式通知手機 App。
// --------------------------------------------------------------------------

bool mqttpanel_switch_pub(const char* topicVal, bool varBool);
bool mqttpanel_dimmer_pub(const char* topicVal, int varInt);
bool mqttpanel_select_pub(const char* topicVal, int varInt);
bool mqttpanel_number_pub(const char* topicVal, float varFloat); // 數字只有發布功能，沒有訂閱
bool mqttpanel_text_pub(const char* topicVal, const String& varString);

// 全體廣播：強制把目前所有註冊的變數數值，全部發送一次給 MQTT Broker。
// 通常配合 Sync 功能使用 (App 一連線就叫 ESP32 全部報數)。
void mqttpanel_publish_all_vals();

#endif // 結束 #ifndef 的範圍
