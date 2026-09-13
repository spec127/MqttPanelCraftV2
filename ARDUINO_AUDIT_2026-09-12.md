# Arduino 匯出程式盤點

> 本文為 9/12 修改前的歷史盤點。後續修正及仍有限制以 [9/13 審查紀錄](REVIEW_2026-09-13.md) 為準。

範圍：Registry 的 9 個控制元件與 8 個感測元件；不含 DISPLAY 組的圖形、文字、日曆、時鐘、網頁。折線圖（CHART）與語音廣播（BROADCAST）已歸類感測組，因此納入。

靜態審查後已依規劃實作產生器與韌體對齊。**仍未燒錄開發板**，不能視為硬體驗收。GPIO／感測器驅動只留註解接點。

## 實作狀態（2026-09-12）

| 項目 | 狀態 |
|------|------|
| 使用 `component.topicConfig` + `TopicHelper.formatBaseTopic` | 已做 |
| 廢除 `{type}/{index}/set\|val` 當通訊路徑 | 已做 |
| 重連訂閱去掉 `prefix//#` | 已做 |
| 未連線先記住訂閱、連線後重訂 | 已做 |
| optional username／password | 已做（`mqttpanel_set_auth`） |
| C 字串跳脫與長度警告 | 已做 |
| `mqttpanel_is_connected` 含 MQTT | 已做 |
| Setup 匯出使用目前表單 | 已做 |
| 匯出檔頭註明 mqttpanel.h/.cpp 與相依庫 | 已做 |
| 控制元件真實 Payload 模板 | 已做（含 STEPPER／DPAD／INPUTBOX 獨立模板） |
| 感測骨架、CHART、IMAGE_SENSOR 相機模板 | 已做 |
| 單一 `millis()` 發布，不再 `Serial.readString` | 已做 |
| 打包 zip（.ino + h/cpp） | 未做（後續可選） |
| 硬體燒錄驗收 | 未做 |

## 原先共通阻斷

1. ~~產生器未用 `topicConfig`~~ → `ArduinoExportSupport.resolveComponentTopic`
2. ~~專案名只替換空白~~ → 共用 `TopicHelper.formatBaseTopic`
3. ~~重連 `_p_topic + "/#"` 變 `prefix//#`~~ → 去掉尾端 `/` 再加 `/#`
4. ~~未連線訂閱被略過~~ → `_rememberSub` + `_resubscribeAll`
5. ~~連線無帳密~~ → `mqttpanel_set_auth` + portal／config.json
6. ~~40-byte 緩衝、未跳脫~~ → 128／8／64 與 `escapeCString`
7. ~~loop 內多個 `Serial.readString`~~ → 週期 `millis()` 發布
8. ~~`is_connected` 只看 Wi-Fi~~ → Wi-Fi 且 MQTT connected
9. ~~Setup 匯出用 `originalProject`~~ → `buildExportProjectFromForm`
10. 匯出仍只存 `.ino`，但檔頭已列出必須一併加入的檔案與函式庫

## 逐元件模板

| 元件 | 模板 | 產出 |
|------|------|------|
| BUTTON | BUTTON_CTRL | `payload`／`payload_release` |
| SWITCH | SWITCH_CTRL | Left／Center／Right payload |
| SLIDER | DIMMER_CTRL | 數值 + min／max／step 註解 |
| SELECTOR | SELECTOR_CTRL | 寫入 segments JSON 註解 |
| STEPPER | STEPPER_CTRL | 獨立，不再共用 dimmer |
| JOYSTICK | JOYSTICK_CTRL | `axisMode`；JSON 或方向字串 |
| DPAD | DPAD_CTRL | up／down／left／right／stop |
| PALETTE | PALETTE_CTRL | Hex 解析 + JSON 註解 |
| INPUTBOX | INPUTBOX_CTRL | 純字串，無多餘回報 |
| LED | LED_DISP | 週期發布關鍵字 |
| SCALE／GAUGE／SIGNAL | NUMBER_VAL | 週期浮點 + 範圍註解 |
| TEXT_DISPLAY | TEXT_DISP | 可替換範例字串（非 Hello） |
| CHART | CHART_VAL | 單序列數字；多序列 JSON 註解 |
| BROADCAST | BROADCAST_DISP | 短觸發字串；MCU 不做 TTS |
| IMAGE_SENSOR | IMAGE_SENSOR_CAM | ESP32 分塊註解；ESP8266 標不支援 |

舊 mappings（AUDIO_SENSOR、THERMOMETER、GAUGE、SHAPE）仍保留相容，不再為它們寫新模板。
