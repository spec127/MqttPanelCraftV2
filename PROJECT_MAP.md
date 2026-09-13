# MqttPanelCraft — 專案地圖（初期基準文件）

> **用途**：後續所有功能修改、除錯、重構的共同依據。  
> **掃描基準日**：2026-09-12（已對齊 [掃描專案整體結構](c22d6a98-a51d-4211-8371-d5e58a63e894)、[掃描UI元件與功能](95e395d7-e8d4-4056-8008-e536c89328e1)）  
> **證據來源**：原始碼、`AndroidManifest.xml`、`build.gradle.kts`、`README.md`、`ARDUINO_AUDIT_2026-09-12.md`  
> **規則**：文中標「未確認」者不得當成事實；有路徑者皆經靜態讀檔核對。

---

## 1. 產品是什麼

**MqttPanelCraft** 是一個開發中的 **Android MQTT 物聯網面板工具**。

| 面向 | 內容 |
|------|------|
| 核心價值 | 在手機上用視覺化畫布拖放元件，組成可即時收發 MQTT 的控制／監控面板 |
| 目標使用者 | IoT 學生、創客、開發者、教育場景 |
| 主要能力 | MQTT 連線、畫布編輯器、元件屬性設定、Arduino 範例匯出、WebView／HTML 擴充面板 |
| 狀態 | README 標示「開發中」；App `versionName = 0.15.2`、`versionCode = 213` |

**不是**：雲端後端服務、完整帳號系統（Mock Login/Register 已移除，Launcher 直接進 Dashboard）、完整硬體韌體產品（Arduino 僅為匯出／輔助範本）。

---

## 2. 倉庫頂層結構

```
MqttPanelCraftV2/
├── MqttPanelCraft/          # ★ Android 主專案（Gradle）
├── arduino/                 # Arduino/ESP 輔助庫與範例（newmanger）
├── web_prototype/           # 獨立 HTML/JS 原型（非 App 建置依賴）
├── readmefile/              # README 截圖與 logo
├── node_modules/            # 根目錄 Node 相依（測試腳本用；非 Android 依賴）
├── .agent/                  # Agent skills／參考文件（開發輔助，非 App 執行時）
├── README.md / README_EN.md
├── ARDUINO_AUDIT_2026-09-12.md   # Arduino 匯出與 Topic 對齊盤點
├── test_chart_mqtt.js / .py / test_camera_upload_deno.ts  # 外部 MQTT 測試腳本
├── MqttPanelCraft-v0.9.2.0-*.apk # 舊版 APK 產物（版本號與目前 build 不同）
└── PROJECT_MAP.md           # 本文件
```

**真正要改 App 行為時，幾乎都在**：`MqttPanelCraft/app/src/main/`。  
注意：未編譯的 `MqttPanelCraft/graphic.kt` 草稿已刪除；正式圖形仍由 GraphicDefinition 提供。

---

## 3. 技術棧與建置身分

| 項目 | 值 | 證據 |
|------|-----|------|
| 語言 | Kotlin | `*.kt` |
| UI | Android XML Views + ViewBinding（非 Jetpack Compose 為主） | `buildFeatures.viewBinding = true` |
| 建置 | AGP 8.13.1、Kotlin 2.0.21、Gradle Version Catalog | `MqttPanelCraft/gradle/libs.versions.toml` |
| namespace / applicationId | `com.example.mqttpanelcraft` | `app/build.gradle.kts` |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 | 同上 |
| versionName / versionCode | `0.15.2` / `213` | 同上 |
| MQTT 客戶端 | Eclipse Paho `org.eclipse.paho.client.mqttv3:1.2.5` | `app/build.gradle.kts` |
| 非同步 | Kotlin Coroutines | 同上 |
| 廣告 | Google Play Services Ads 23.0.0 | libs + Manifest meta-data |
| JVM | Java 11 | compileOptions / jvmTarget |
| 資料庫 | **無 Room／SQLite**；專案持久化為 JSON 檔 | `ProjectRepository` → `filesDir/projects.json` |

---

## 4. 系統架構（必讀）

### 4.1 架構模式

1. **MVVM（僅畫布畫面）**  
   - **只有** `ProjectViewActivity` 使用 `ProjectViewModel`（選取、Undo≤20、格線／輔助線、元件工廠）。  
   - `DashboardActivity` / `SetupActivity` / `WebViewActivity` **直接**操作 `ProjectRepository` 單例。  
   - 無 DI 框架；狀態多為 Kotlin `object` 單例。

2. **Registry Pattern（元件系統核心）**  
   - `IComponentDefinition` = 單一元件的「身分 + 建立 View + 屬性綁定 + MQTT 行為」。  
   - `ComponentDefinitionRegistry` 以 `type` 字串註冊全部正式元件（**22**，由 `UsabilityRegressionTest` 斷言鎖定）。  
   - **新增／修改元件行為，優先改對應 `*Definition.kt`，不要散落改 Activity。**

3. **單向資料流（畫布）**  
   - `ComponentData` → `ComponentRenderer`（viewCache diff；runtime 可用 `updateRuntimeComponent` 避免重建整個畫布，尤其 WebView）。  
   - 所有元件擴充設定都在 `props: MutableMap<String,String>`。

4. **前景 Service 管 MQTT Session**  
   - UI 透過 `MqttSessionClient` 發 Intent Action。  
   - `MqttSessionService` 實際連線／訂閱／發布／背景保活（`keepMqttInBackground`）。  
   - `MqttRepository` 為訊息／快照／log 的記憶體中樞（非磁碟 DB）。

5. **God Activity → Managers**  
   - `ProjectViewActivity` 委派：`ProjectUIManager`、`SidebarManager`、`PropertiesSheetManager`、`LogConsoleManager`、`IdleAdController`、`CanvasInteractionManager`、`ComponentRenderer`、`ComponentBehaviorManager`。

### 4.2 執行時資料流（簡圖）

```
DashboardActivity
    └─ ProjectRepository (projects.json)
         ├─ SetupActivity（建立／編輯／匯入匯出／Arduino）
         └─ ProjectViewActivity / WebViewActivity
              ├─ ProjectViewModel
              ├─ ComponentRenderer + CanvasInteractionManager
              ├─ ComponentBehaviorManager → IComponentDefinition
              ├─ PropertiesSheetManager / SidebarManager
              └─ MqttSessionClient ──Intent──► MqttSessionService
                                                 ├─ Paho MqttClient
                                                 └─ MqttRepository (messages / snapshots / logs)
```

### 4.3 編輯模式 vs 執行模式

`ProjectViewActivity` 內 `isEditMode`：

| 模式 | 行為 |
|------|------|
| Edit | 可拖曳、縮放、刪除、開屬性面板、側邊欄加元件；`MqttSessionClient.setRuntime(..., false)` |
| Runtime | 元件行為啟用、MQTT 互動；idle 廣告計時；`setRuntime(..., true)` |

---

## 5. 畫面與導航（Activities）

| 類別 | 路徑 | 職責 | Manifest |
|------|------|------|----------|
| `MyApplication` | `.../MyApplication.kt` | 語系、主題、Repository 初始化、全域 crash log | `android:name` |
| `BaseActivity` | `.../BaseActivity.kt` | Edge-to-edge、MQTT 通知權限 helper | 父類 |
| **`DashboardActivity`** | `.../DashboardActivity.kt` | **Launcher**；專案列表、排序、側邊抽屜、進 Setup／開專案 | MAIN/LAUNCHER |
| `SetupActivity` | `.../SetupActivity.kt` | 建立／編輯專案（broker、port、帳密、類型、方向）、匯入 JSON、匯出 JSON、Arduino 匯出、連線測試 | 非 exported |
| `ProjectViewActivity` | `.../ProjectViewActivity.kt` | **核心畫布**（HOME/FACTORY 等非 WEBVIEW） | 非 launcher |
| `WebViewActivity` | `.../WebViewActivity.kt` | **整頁 Web 專案**；JS Bridge `mqtt.publish/subscribe` | 非 launcher |
| `AboutActivity` | `.../AboutActivity.kt` | 關於／隱私等 | 非 exported |
| `LoginActivity` | 已刪除 | 移除未接後端的 Mock 登入 | 已移除 |
| `RegisterActivity` | 已刪除 | 移除 Mock 註冊 | 已移除 |

**Fragment**：`CodeExportDialogFragment`（模式：`EXPORT_ARDUINO` / `EXPORT_JSON` / `IMPORT_JSON`）。

**Service**：`service/MqttSessionService`（foreground，`connectedDevice`，`stopWithTask=false`）。

**BroadcastReceiver**：Manifest 中**無**自訂 Receiver（網路改用 `ConnectivityManager.NetworkCallback`）。

**導航慣例（由程式推得）**：

- Dashboard → Setup（新增／編輯）→ 存檔回 Dashboard  
- Dashboard → 依 `ProjectType`：`WEBVIEW` → `WebViewActivity`，其餘 → `ProjectViewActivity`  
- Login／Register 及其 XML、Manifest 入口已刪除；正常啟動維持 Dashboard  
- 非 Premium 時，專案數 ≥ 1 則 FAB 新增被擋（`DashboardActivity.setupFab` + `PremiumManager`）  
- Setup UI 目前主要暴露 `HOME` / `WEBVIEW`；`FACTORY` / `OTHER` 仍在 enum（舊資料相容——細節未全部確認）

---

## 6. 資料層

### 6.1 模型

| 檔案 | 內容 |
|------|------|
| `model/Project.kt` | `id, name, broker, port, username, password, clientId, type, components, customCode, orientation, createdAt, lastOpenedAt, keepMqttInBackground` |
| `model/ComponentData.kt` | `id, type, x, y, width, height, label, topicConfig, props: MutableMap<String,String>` |
| `ProjectType` enum | `HOME, FACTORY, WEBVIEW, OTHER` |

### 6.2 持久化

| 機制 | 位置 | 說明 |
|------|------|------|
| 專案 JSON | `ProjectRepository` → `context.filesDir/projects.json` | AtomicFile；`ProjectSaveCoordinator`（debounce）+ `DeduplicatingTextWriter` |
| 匯出格式 | `exportProjectToJson` | `{schemaVersion:1, meta:{exportedAt,appVersion}, project:{...}}`；**不匯出 password** |
| 匯入正規化 | `ProjectImportNormalizer` | 重編 component id 為 1..N、清理無效 `linked_components` |
| 專案 ID | `ProjectIdentity` + `generateId()` | 新建／編輯統一；ID 為 SecureRandom 10 字元 `[0-9a-z]` |
| 影像 runtime | `updateRuntimeProperty` | `IMAGE_SENSOR`／舊 `IMAGE` **不落盤** |
| 色票歷史 | `ColorHistoryManager` + prefs `global_colors` | 最近使用色 |
| App 設定 SP | `AppSettings` | `dark_mode`、`ads_disabled`（Premium）、`language_code`、`sort_mode` |
| 畫布 prefs | `ProjectPrefs` | `GRID_VISIBLE`、`GUIDES_VISIBLE` |
| MQTT Service SP | `MqttSession` | 背景專案、clientId 持久化、時鐘狀態 |
| Crash log | `MyApplication` / `CrashLogger` | `getExternalFilesDir` → `crash_log.txt` |

**Repository API（精要）**：`initialize`、`getAllProjects`、`getProjectById`、`addProject`、`updateProject`、`deleteProject`、`updateRuntimeProperty`、`generateId`、`isProjectNameTaken`、`exportProjectToJson`、`parseProjectJson`、`swapProjects`、`sortProjects`。

---

## 7. MQTT 系統

### 7.1 檔案分工

| 檔案 | 職責 |
|------|------|
| `mqtt/MqttSessionClient.kt` | UI 端 API：activate / visibility / runtime / publish / subscribe / stop |
| `service/MqttSessionService.kt` | 真實 Paho 連線、訂閱集合、發布、網路回呼重連、時鐘自動化、前景通知 |
| `MqttRepository.kt` | 訊息處理、topic 狀態快取、snapshot、image buffer、log |
| `mqtt/MqttSnapshotCache.kt` | UI 離開後背景訊息快照 |
| `mqtt/MqttConnectionState.kt` | 連線狀態枚舉 |
| `mqtt/ClockAutomationEngine.kt` | CLOCK 元件排程／自動化 |
| `utils/TopicHelper.kt` | Topic 正規化與訂閱集合計算 |

### 7.2 Topic 規則（App 現行）

- **專案基底**：`{normalizedProjectName}/{projectId}`  
  - 正規化：trim → lowercase → 空白變 `_` → 移除非 `[a-z0-9_]`  
  - 見 `TopicHelper.formatBaseTopic`
- **新元件預設 topic**：`ProjectViewModel.generateSmartTopic` → `{base}/{label_with_underscore}`（例：`…/button_1`）
- **專案萬用訂閱**：`{base}/#`（`formatProjectWildcard`）
- **額外訂閱**：元件 `topicConfig` 若不在專案前綴下，會額外加入（`collectSubscriptionTopics`）
- **改名／改 ID**：`rewriteGeneratedProjectTopic` 只重寫落在舊 base 下的 topic；自訂 topic 不動
- **元件實際 topic**：存在 `ComponentData.topicConfig`（屬性面板可編輯；可 reset）

> Arduino 匯出現已對齊 App：`ArduinoCodeGenerator` 使用 `topicConfig` + `TopicHelper.formatBaseTopic`，韌體重連不再產生 `prefix//#`。細節見 `ARDUINO_AUDIT_2026-09-12.md`（已標實作狀態）。

### 7.3 QoS／連線選項（靜態證據）

- `publish`／`subscribe`：**未顯式 `setQos`**；依 Paho 預設推導為 QoS **1**（與 README 一致，但非程式明文）。  
- Connect：`cleanSession = false`、timeout 30、keepalive 60、**關閉** Paho `automaticReconnect`；Service 自行 backoff `[1,2,5,10,30,60]` 秒。  
- 帳密錯誤等 reasonCode ∈ {2,4,5} → `FAILED` 不再重試。  
- URI：`broker` 已含 `tcp://` 則直用，否則 `tcp://{broker}:{port}`。  
- clientId：專案有設則用；否則 `MPC_{UUID}` 並寫入 prefs 重用。  
- `keepMqttInBackground`：離開畫面是否維持前景 session／通知。

### 7.4 背景快照與時鐘

- `MqttSnapshotCache`：UI `onStop`→`markUiDetached`；`onStart`→`consumeBackgroundSnapshots` 補播；排除 image 類 topic。  
- `ClockAutomationEngine`：`CLOCK` 的 TIME／COUNTDOWN／SCHEDULE；Service 每秒 tick；到期對 `linked_components` 發訊。

### 7.5 WebView JS Bridge

`WebViewActivity` 注入 `window.mqtt`：

- `mqtt.publish(topic, message)`
- `mqtt.subscribe(topic)`

畫布內嵌網頁元件則走 `WebBoxView`（DISPLAY 組），與整頁 `WebViewActivity` 不同路徑。

---

## 8. 元件系統（功能地圖核心）

### 8.1 協作鏈

```
SidebarManager（選元件加入）
    → ProjectViewModel 新增 ComponentData
    → ComponentRenderer 依 type 找 Registry.createView
         → ComponentContainer.createEndpoint（邊框／resize handle）
         → 具體 View
    → ComponentBehaviorManager.attachBehavior
    → 點選 → PropertiesSheetManager
         → 通用屬性（名稱、尺寸、topic、label…）
         → inflate definition.propertiesLayoutId + bindPropertiesPanel
```

關鍵檔案：

- `ui/components/IComponentDefinition.kt` — 契約（含 `LocalComponentTriggerSource` 本地連動）
- `ui/components/ComponentDefinitionRegistry.kt` — **唯一正式註冊表**
- `ui/components/ComponentContainer.kt` / `InterceptableFrameLayout.kt`
- `ui/components/ComponentGroup.kt` — CONTROL / SENSOR / DISPLAY
- `ui/components/prop/CommonPropBinder.kt`
- `ui/ComponentRenderer.kt`
- `ui/ComponentBehaviorManager.kt`
- `ui/PropertiesSheetManager.kt`
- `ui/CanvasInteractionManager.kt` — 拖曳、縮放、對齊、刪除區
- `ui/AlignmentOverlayView.kt` / `GridPatternView.kt`
- `ui/SidebarManager.kt` / `ProjectUIManager.kt` / `LogConsoleManager.kt`
- `ui/IdleAdController.kt` + `utils/AdManager.kt` + `utils/PremiumManager.kt`

### 8.2 正式註冊元件一覽（Registry 順序 = 側邊欄順序）

#### CONTROL（控制器）

| type | Definition | 主要 View／實作 | 屬性 layout | MQTT | 備註 |
|------|------------|-----------------|-------------|------|------|
| `BUTTON` | `ButtonDefinition.kt` | `AppCompatButton`（`target`） | `layout_prop_button` | 只發布 | `trigger_mode`=tap/hold/timer；payload／payload_release |
| `SWITCH` | `SwitchDefinition.kt` | `layout_component_switch_tristate` | `layout_prop_switch` | 發布＋訂閱 | 二段／三段；持久化 `state` |
| `SLIDER` | `SliderDefinition.kt` | `PanelSliderView` | `layout_prop_slider` | 發布＋訂閱 | `sendMode`=release/continuous |
| `SELECTOR` | `SelectorDefinition.kt` | 動態分段按鈕 | `layout_prop_selector` | 發布＋訂閱 | |
| `STEPPER` | `StepperDefinition.kt` | `StepperView`（`target_stepper_view`） | `layout_prop_stepper` | 發布＋訂閱 | 放手才 commit |
| `JOYSTICK` | `JoystickDefinition.kt` | `JoystickView`（`target`） | `layout_prop_joystick` | 只發布 | 見下方已知不一致 |
| `DPAD` | `DpadDefinition.kt` | `JoystickView`（Buttons） | **共用** `layout_prop_joystick` | 只發布 | 恆鎖比例 |
| `PALETTE` | `ColorPaletteDefinition.kt` | `ColorPaletteView` | `layout_prop_color_palette` | 只發布 | Hex/JSON RGB/HSV |
| `INPUTBOX` | `InputBoxDefinition.kt` | `InputBoxView` | `layout_prop_input_box` | 只發布 | labelPrefix=`sendbox`；單向 |

#### SENSOR（感測／顯示回報）

| type | Definition | 主要 View | 屬性 layout | MQTT | 備註 |
|------|------------|-----------|-------------|------|------|
| `LED` | `LedDefinition.kt` | `LedView` | `layout_prop_led` | 只訂閱 | STANDARD/RGB；關鍵字／計時熄滅 |
| `SCALE_METER` | `ScaleMeterDefinition.kt` | `ScaleMeterView` | `layout_prop_scale_meter` | 只訂閱 | 純顯示 |
| `GAUGE_METER` | `GaugeMeterDefinition.kt` | `GaugeMeterView` | `layout_prop_gauge_meter` | 只訂閱 | 純顯示 |
| `SIGNAL_INDICATOR` | `SignalIndicatorDefinition.kt` | `SignalIndicatorView` | `layout_prop_signal_indicator` | 只訂閱 | |
| `TEXT_DISPLAY` | `TextDisplayDefinition.kt` | `TextDisplayView` | `layout_prop_text_display` | 只訂閱＋連動接收 | `onLinkedMqttMessage` |
| `IMAGE_SENSOR` | `ImageSensorDefinition.kt` | `ImageDisplayView` | `layout_prop_image` | 只訂閱 | 不落盤；snapshot no-op |
| `CHART` | `LineChartDefinition.kt` | 內部 `LineChartCompositeView` | `layout_prop_line_chart` | 只訂閱 | 單／多序列 |
| `BROADCAST` | `BroadcastDefinition.kt` | `BroadcastView` | `layout_prop_broadcast` | 只訂閱＋連動接收 | TTS；snapshot 不重播語音 |

#### DISPLAY（多媒體顯示）— 屬性面板隱藏 topic 列

| type | Definition | 主要 View | 屬性 layout | MQTT | 備註 |
|------|------------|-----------|-------------|------|------|
| `GRAPHIC` | `GraphicDefinition.kt` | `GraphicCompositeView` | `layout_prop_graphic` | 無 | SHAPE/LINE/IMAGE＋裁切 |
| `TEXT` | `TextDefinition.kt` | `TextView`（`target_text`） | `layout_prop_text` | 理論可收訊但無 topic UI | 靜態為主 |
| `CALENDAR` | `CalendarClockDefinition.kt` | `CalendarDisplayView` | `layout_prop_calendar` | 無 | |
| `CLOCK` | `ClockDefinition.kt` | `ClockTriggerView` | `layout_prop_clock` | Service 代發 | TIME/COUNTDOWN/SCHEDULE→連動 CONTROL |
| `WEB_BOX` | `WebBoxDefinition.kt` | `WebBoxView` | `layout_prop_web` | 無 | URL/HTML；內容原子比對防重載 |

### 8.3 存在但未註冊／死碼／易踩坑

| 項目 | 狀態 |
|------|------|
| `GAUGE` / `GaugeDefinition.kt` | 未註冊的 stub 已刪除；正式 GAUGE_METER 保留。templates 仍有舊映射。 |
| `CalendarClockView.kt` | 無引用的舊 View 已刪除（CALENDAR 用 `CalendarDisplayView`）。 |
| `MqttPanelCraft/graphic.kt` | 不在 source set 的草稿已刪除。 |
| `supportsGenericPayload` | 介面與 PropertiesSheet 已接；**無 Definition 覆寫為 true** → generic payload UI 死碼。 |
| `LocalComponentTriggerSource` | BehaviorManager／測試已備；**無 Definition 實作**（CLOCK 連動走 Service）。 |
| DISPLAY 組 | 屬性面板隱藏 topic 列。 |
| Arduino templates | 可能含舊 type（`AUDIO_SENSOR`、`THERMOMETER`、`GAUGE` 等）。 |
| strings `component_label_*` | 部分殘留 key（gauge／audio_sensor／shape…）無對應註冊元件。 |

### 8.4 元件連動

- `props["linked_components"]`：逗號分隔目標 component id。  
- 來源可實作 `LocalComponentTriggerSource`（目前無人用）。  
- 目標走 `onLinkedTrigger`（payload：`payload` > `payloadRight` > `value` > 觸發值）。  
- `TEXT_DISPLAY`／`BROADCAST` 另有 `onLinkedMqttMessage`（由 `ProjectViewActivity.handleMqttMessage` 直接呼叫）。  
- CLOCK 自動化在 Service 側觸發連動。

### 8.5 掃描確認的已知不一致（改功能前先看）

1. **JOYSTICK 比例鎖失效**：屬性寫 `axisMode`，`isFixedAspectRatio` 卻讀 `props["axes"]`。  
2. **BUTTON hold 模式**：`ACTION_DOWN` 會發 main payload，但 `ACTION_UP` **未**發送 `payload_release`（timer 模式才會延遲發 release）。  
3. **LogConsoleManager** 自算 topic 前綴，與 `TopicHelper.formatBaseTopic` 正規化**不完全一致**。  
4. Arduino 審查修正詳見 `REVIEW_2026-09-13.md`；相機擷取、硬體接點、動態 JS Topic 與完整打包仍有限制，不能當成已燒板驗收。  
5. Setup Arduino 匯出現讀目前表單；未儲存時會 Toast 提醒。

---

## 9. 功能 → 檔案對照（常用入口）

| 功能 | 主要檔案 |
|------|----------|
| 專案列表／排序／刪除／開專案 | `DashboardActivity.kt`, `adapter/ProjectAdapter.kt`, `ProjectRepository.kt` |
| 建立／編輯 MQTT 設定 | `SetupActivity.kt`, `res/layout/activity_setup.xml` |
| 畫布編輯／執行 | `ProjectViewActivity.kt`, `ProjectViewModel.kt` |
| 加元件側邊欄 | `SidebarManager.kt`, `layout_sidebar_components.xml` |
| 屬性面板 | `PropertiesSheetManager.kt`, `layout_prop_*.xml`, 各 `*Definition.bindPropertiesPanel` |
| 拖曳縮放對齊刪除 | `CanvasInteractionManager.kt`, `AlignmentOverlayView.kt`, `GridPatternView.kt` |
| MQTT 連線生命週期 | `MqttSessionClient.kt`, `MqttSessionService.kt` |
| Topic 規則 | `TopicHelper.kt`（+ 單元測試 `TopicHelperTest.kt`） |
| 訊息 log 面板 | `LogConsoleManager.kt`, `MqttRepository`, `adapter/LogAdapter.kt` |
| Arduino 匯出 | `ArduinoCodeGenerator.kt`, `ArduinoExportSupport.kt`, `ArduinoExportManager.kt`, `arduino_templates.json`, `arduino/newmanger/*` |
| JSON 匯入匯出 | `SetupActivity` + `ProjectRepository.exportProjectToJson` / `parseProjectJson` + `ProjectImportNormalizer` |
| 整頁 Web 專案 | `WebViewActivity.kt`, `utils/HtmlTemplates.kt` |
| 主題／語系 | `ThemeManager.kt`, `LocaleManager.kt`, `values` / `values-night` / `values-zh-rTW` / `values-zh-rCN` |
| 廣告／Premium 模擬 | `AdManager.kt`, `PremiumManager.kt`, `IdleAdController.kt` |
| 關於頁 | `AboutActivity.kt`, `res/raw*/about_content.txt`, `privacy_policy.txt` |

---

## 10. Arduino 與週邊目錄

| 路徑 | 說明 |
|------|------|
| `arduino/newmanger/mqttpanel.h/.cpp` | 裝置端 MQTT helper |
| `arduino/newmanger/newmanger.ino` | 主 sketch |
| `arduino/newmanger/explained/` | 註解版說明 |
| `MqttPanelCraft/.../assets/arduino_templates.json` | App 內模板映射 |
| `ARDUINO_AUDIT_2026-09-12.md` | Topic／韌體／模板實作狀態（靜態完成，未燒板） |

`web_prototype/`：獨立前端原型，**不是** Android 模組。  
根目錄 `node_modules/` + `test_*.js/py/ts`：外部 MQTT／相機測試，**不參與** APK 建置。

---

## 11. 資源與多語系

```
res/
├── layout/           # Activity + layout_prop_* + sidebar + dialog
├── values/           # strings, colors, themes, dimens, ids
├── values-night/     # 暗色 colors/themes
├── values-zh-rTW/    # 繁中 strings
├── values-zh-rCN/    # 簡中 strings
├── raw/ + raw-zh-*   # about / privacy 文本
├── drawable/         # 圖示、選取框、resize handle…
└── xml/              # backup / data extraction rules
```

`build.gradle.kts` 啟用 `generateLocaleConfig = true`。

---

## 12. 測試地圖

### Unit（`app/src/test/...`）

| 測試 | 關注點 |
|------|--------|
| `TopicHelperTest` | Topic 格式 |
| `ProjectIdentityTest` | 專案 ID 解析 |
| `ProjectImportNormalizerTest` | 匯入 id／連動修復 |
| `ProjectPersistenceTest` | 持久化 |
| `ComponentRegistryTest` | Registry |
| `LocalComponentTriggerTest` | 本地連動 |
| `GraphicDefinitionTest` | 圖形元件 |
| `SelectorCompatibilityTest` | 選擇器相容 |
| `MqttSnapshotCacheTest` | 快照 |
| `ClockAutomationEngineTest` | 時鐘自動化 |
| `CanvasInteractionGeometryTest` | 畫布幾何 |
| `ComponentDataDeepCopyTest` | 深拷貝 |
| `ArduinoExportSupportTest` | Topic 對齊、跳脫、IMAGE_SENSOR 非 Hello |
| `ResourceConsistencyTest` | **架構守門**：三語 string 對齊、禁硬編字串、屬性 dropdown／樣式數量等 |

**測試鎖定數字（新增元件／屬性時會打破）**：正式元件 **22**；`ResourceConsistencyTest`／`PropertyControlsRegressionTest` 另鎖定屬性面板 dropdown／toggle 組數（改 UI 前先跑測）。

### Instrumented（`app/src/androidTest/...`）

| 測試 | 關注點 |
|------|--------|
| `UsabilityRegressionTest` | WebBox 不重載、匯出入 round-trip、22 元件可渲染 |
| `PropertyControlsRegressionTest` | 日／夜屬性面板一致性＋截圖 |
| `MqttSessionServiceInstrumentedTest` | 背景保活／通知／CLOCK／快照（需模擬器 broker `10.0.2.2:1884`） |
| `ExampleInstrumentedTest` | 範本 |

---

## 13. 權限與產品設定（Manifest）

- `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`
- 儲存／媒體讀取（依 API level 限制）
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `POST_NOTIFICATIONS`
- `usesCleartextTraffic="true"`（允許明文 HTTP／部分 broker）
- AdMob APPLICATION_ID 已寫入 meta-data

---

## 14. 後續修改守則（依本文件）

1. **改元件行為／屬性／預設值** → 對應 `ui/components/definitions/*Definition.kt`（必要時同步 `layout_prop_*.xml` 與 strings）。  
2. **改畫布互動** → `CanvasInteractionManager` / `ComponentRenderer` / `ProjectViewActivity`。  
3. **改 MQTT 連線／訂閱策略** → `MqttSessionService` + `TopicHelper` + 測試。  
4. **改專案存檔／匯入** → `ProjectRepository` + `ProjectImportNormalizer` + 測試。  
5. **改 Arduino 匯出** → `ArduinoCodeGenerator` + `ArduinoExportSupport` + `assets/arduino_templates.json` + `arduino/newmanger`；Setup 匯出走表單 `buildExportProjectFromForm`。  
6. `GaugeDefinition`／`CalendarClockView`／`MqttPanelCraft/graphic.kt` 為已刪除的舊檔；正式元件以 Registry 為準。  
7. **Mock Login/Register 已移除**；免費版專案上限為 1（Premium SP 模擬）。  
8. 新增元件／屬性面板時，同步跑 `ResourceConsistencyTest` 與 `UsabilityRegressionTest`（硬數字會擋）。  
9. 動 BUTTON／JOYSTICK／LogConsole／Arduino 前先查 **§8.5 已知不一致**。  
10. 本文件若與程式衝突，**以程式為準**，並應同步更新本文件。

---

## 15. 掃描覆蓋聲明

已核對：

- Android Manifest 全部 Activity／Service  
- Registry 22 元件：type、View、prop layout、**MQTT 方向**、主要行為  
- 畫布 Managers／側欄／屬性面板／刪除區／idle 廣告協作鏈  
- 資料層 Repository／JSON 匯出入／ImportNormalizer／Identity／prefs  
- MQTT Client／Service／Repository／TopicHelper／快照／時鐘  
- 單元與 instrumented 測試關鍵斷言  
- Arduino 匯出已對齊 topicConfig；硬體燒錄與 zip 打包仍未做  

未做／未確認：

- 完整動態執行路徑（真機逐步點擊）  
- 每個元件全部 `props` key 字典（改特定元件時再精讀該 Definition）  
- 真實 AdMob／付費上線狀態（目前 Premium = `ads_disabled` SP）  
- `ProjectType.FACTORY`／`OTHER` 是否僅舊資料相容  
- `node_modules` 來源；arduino C++ **未逐行**複核（以 AUDIT 為準）  

---

*文件結束。之後每次重大架構變更，請在本檔頂部更新日期並修訂對應章節。*
