#include "mqttpanel.h" // 引入我們定義好的 Header 檔

// --- Private Types (私有型別) ---
// 定義這套系統支援哪幾種類型
enum MpType {
  MP_SWITCH, // 開關 (0/1)
  MP_DIMMER, // 調光 (0-100)
  MP_SELECT, // 選單 (0-N)
  MP_NUMBER, // 數字 (僅回報)
  MP_TEXT,   // 文字
  MP_SYNC    // 同步訊號
};

// 定義一個「通道」長什麼樣子
struct MpChannel {
  bool active;                       // 這個位置是否已經被使用了？
  MpType type;                       // 這條通道是什麼類型 (Switch? Dimmer?)
  char topicSet[MPTP_MAX_TOPIC_LEN]; // 監聽的 Topic (App -> ESP32)
  char topicVal[MPTP_MAX_TOPIC_LEN]; // 回報的 Topic (ESP32 -> App)
  void* varPtr;                      // 【關鍵】指標：指向使用者真正的變數地址 (void* 表示它可以存任何類型的地址)
};

// --- Globals (全域變數) ---
// 這裡用 static 表示這些變數「只在這個檔案內部看得到」，外面的人不能亂動。
static PubSubClient* _mqttClient = NULL;      // 存下來的 MQTT Client 指標
static MpChannel _channels[MPTP_MAX_CHANNELS]; // 產生 24 個空格的陣列，用來存通道資料
static int _channelCount = 0;                 // 目前用了幾個通道

// --- Private Prototypes (私有函式宣告) ---
void mqttpanel_router_callback(char* topic, byte* payload, unsigned int length);
bool _register_channel(MpType type, const char* topicSet, void* varPtr);

// --- Core Implementation (核心實作) ---

// 初始化：把 MQTT Client 存起來，並清空通道表
void mqttpanel_begin(PubSubClient* client) {
  _mqttClient = client;
  // 清空陣列
  for(int i=0; i<MPTP_MAX_CHANNELS; i++) {
    _channels[i].active = false;
  }
  _channelCount = 0;
  
  if (_mqttClient) {
    // 【最重要的一步】設定 Callback
    // 告訴 MQTT Library：以後收到訊息，全部交給 `mqttpanel_router_callback` 這個函式處理！
    _mqttClient->setCallback(mqttpanel_router_callback);
  }
}

// 讓 MQTT 保持運作
void mqttpanel_loop() {
  if (_mqttClient) {
    _mqttClient->loop();
  }
}


// --- Router & Handler (路由器與處理器) ---
// 這是整個模組的大腦。當收到 MQTT 訊息時，這個函式會被呼叫。
void mqttpanel_router_callback(char* topic, byte* payload, unsigned int length) {
  // 1. 把收到的 Payload (byte陣列) 轉成乾淨的字串 (String)
  char msg[length + 1];
  memcpy(msg, payload, length);
  msg[length] = '\0'; // 補上結尾符號，確保是標準字串
  
  // 2. 搜尋：這個 Topic 對應到我們哪一個通道？
  // 如果通道數很多，這邊可以用 Hash Map 優化，但 24 個用 for 迴圈跑就夠快了。
  for(int i=0; i<MPTP_MAX_CHANNELS; i++) {
    // 如果這個通道有啟用，且 Topic 字串完全一樣
    if (_channels[i].active && strcmp(_channels[i].topicSet, topic) == 0) {
       
       // 3. 找到了！準備更新變數
       MpType t = _channels[i].type;  // 取出類型
       void* ptr = _channels[i].varPtr; // 取出變數的地址
       
       // 4. 根據不同類型，做不同的解析
       if (t == MP_SWITCH) {
          // 如果是開關
          bool* v = (bool*)ptr; // 把 void* 轉回 bool* 才能操作
          if (strcmp(msg, "1") == 0) { *v = true; }      // 收到 "1" -> 變數設為 true
          else if (strcmp(msg, "0") == 0) { *v = false; } // 收到 "0" -> 變數設為 false
       }
       else if (t == MP_DIMMER || t == MP_SELECT) {
          // 如果是數字類
          int* v = (int*)ptr; // 轉回 int*
          int val = atoi(msg); // 把字串轉成整數 (atoi = ASCII to Integer)
          
          if (t == MP_DIMMER) {
             // 調光器要限制在 0-100 之間，防止錯誤數據
             if (val < 0) val = 0;
             if (val > 100) val = 100;
          }
          *v = val; // 更新變數
       }
       else if (t == MP_TEXT) {
          // 如果是文字
          String* v = (String*)ptr; // 轉回 String*
          *v = String(msg); // 更新變數
       }
       else if (t == MP_SYNC) {
          // 如果是同步訊號 (通常 payload 是 "1")
          if (msg[0] == '1') {
             mqttpanel_publish_all_vals(); // 呼叫全體廣播
          }
       }
       
       return; // 任務完成，收工離開
    }
  }
}

// --- Subscription Impl (註冊邏輯實作) ---
// 這是內部共用的註冊函式
bool _register_channel(MpType type, const char* topicSet, void* varPtr) {
   if (_channelCount >= MPTP_MAX_CHANNELS) return false; // 滿了就不收
   if (!_mqttClient) return false;

   int idx = _channelCount;
   _channels[idx].active = true;
   _channels[idx].type = type;
   _channels[idx].varPtr = varPtr; // 把變數地址存起來
   
   // 複製 Topic 字串進去
   strncpy(_channels[idx].topicSet, topicSet, MPTP_MAX_TOPIC_LEN);
   _channels[idx].topicSet[MPTP_MAX_TOPIC_LEN-1] = '\0'; // 確保結尾安全

   // 自動產生 topicVal (也就是把 .../set 改成 .../val)
   // 這樣你就不用手動指定兩個 Topic 了
   strncpy(_channels[idx].topicVal, topicSet, MPTP_MAX_TOPIC_LEN);
   char* ptr = strstr(_channels[idx].topicVal, "/set"); // 找 "/set" 在哪
   if (ptr) {
      strcpy(ptr, "/val"); // 替換成 "/val"
   } else {
      // 如果原本沒寫 /set，就在後面硬加 _val，避免重複
      strncat(_channels[idx].topicVal, "_val", MPTP_MAX_TOPIC_LEN - strlen(_channels[idx].topicVal) - 1);
   }

   _channelCount++; // 用量+1

   // 【立刻訂閱】這就是為什麼 setup 呼叫一次就好的原因
   _mqttClient->subscribe(topicSet);
   return true;
}

// 以下都是轉呼叫上面的共用函式，只是為了型別安全 (Type Safety)
bool mqttpanel_switch_sub(const char* topicSet, bool* varBool) {
  return _register_channel(MP_SWITCH, topicSet, (void*)varBool);
}

bool mqttpanel_dimmer_sub(const char* topicSet, int* varInt) {
  return _register_channel(MP_DIMMER, topicSet, (void*)varInt);
}

bool mqttpanel_select_sub(const char* topicSet, int* varInt) {
  return _register_channel(MP_SELECT, topicSet, (void*)varInt);
}

bool mqttpanel_text_sub(const char* topicSet, String* varString) {
  return _register_channel(MP_TEXT, topicSet, (void*)varString);
}

bool mqttpanel_sync_sub(const char* topicSet) {
  return _register_channel(MP_SYNC, topicSet, NULL);
}

// --- Publish Impl (發信邏輯實作) ---

bool mqttpanel_switch_pub(const char* topicVal, bool varBool) {
  if (!_mqttClient || !_mqttClient->connected()) return false;
  return _mqttClient->publish(topicVal, varBool ? "1" : "0");
}

bool mqttpanel_dimmer_pub(const char* topicVal, int varInt) {
  if (!_mqttClient || !_mqttClient->connected()) return false;
  char buf[16];
  itoa(varInt, buf, 10); // 整數轉字串 (Integer to ASCII)
  return _mqttClient->publish(topicVal, buf);
}

bool mqttpanel_select_pub(const char* topicVal, int varInt) {
  if (!_mqttClient || !_mqttClient->connected()) return false;
  char buf[16];
  itoa(varInt, buf, 10);
  return _mqttClient->publish(topicVal, buf);
}

bool mqttpanel_number_pub(const char* topicVal, float varFloat) {
  if (!_mqttClient || !_mqttClient->connected()) return false;
  char buf[32];
  dtostrf(varFloat, 1, 2, buf); // Arduino 獨家的浮點數轉字串函式 (值, 最小寬度, 小數點位數, buffer)
  return _mqttClient->publish(topicVal, buf);
}

bool mqttpanel_text_pub(const char* topicVal, const String& varString) {
  if (!_mqttClient || !_mqttClient->connected()) return false;
  return _mqttClient->publish(topicVal, varString.c_str());
}

// 全體廣播
void mqttpanel_publish_all_vals() {
  if (!_mqttClient) return;

  // 跑迴圈，看哪個通道是啟用的，就發送它的目前數值
  for(int i=0; i<MPTP_MAX_CHANNELS; i++) {
     if (_channels[i].active && _channels[i].type != MP_SYNC) {
        
        MpType t = _channels[i].type;
        void* ptr = _channels[i].varPtr;
        const char* tVal = _channels[i].topicVal; // 自動產生好的 /val topic

        if (t == MP_SWITCH) {
           mqttpanel_switch_pub(tVal, *(bool*)ptr);
        }
        else if (t == MP_DIMMER) {
           mqttpanel_dimmer_pub(tVal, *(int*)ptr);
        }
        else if (t == MP_SELECT) {
           mqttpanel_select_pub(tVal, *(int*)ptr);
        }
        else if (t == MP_TEXT) {
           mqttpanel_text_pub(tVal, *(String*)ptr);
        }
        
        delay(10); // 稍微暫停 10ms，避免瞬間塞爆網路緩衝區
     }
  }
}
