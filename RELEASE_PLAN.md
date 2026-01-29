# App 正式發布準備企劃 (Release Preparation Plan)

本企劃書列出了將 MqttPanelCraft 從開發階段轉為正式發布階段所需的資料、技術調整與發布流程。

## 1. 您需要提供的資料 (User Requirements)

為了將 App 設定為正式版，我需要您提供以下具體資訊。請將這些資料準備好，我們將在後續步驟中填入程式碼或設定檔。

### A. Google AdMob (廣告)
目前的測試廣告 ID 需要替換為您的正式 ID。
*   **AdMob App ID**: 格式如 `ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy`
*   **Banner Ad Unit ID (橫幅廣告)**: 格式如 `ca-app-pub-xxxxxxxxxxxxxxxx/zzzzzzzzzz`
*   **Rewarded Ad Unit ID (獎勵廣告)**: 格式如 `ca-app-pub-xxxxxxxxxxxxxxxx/wwwww`
    *   *用途：當免費用戶保存或匯出專案時顯示。*

### B. Google Play Billing (應用程式內購)
如果您希望 "Premium" 功能是透過 Google Play 付費解鎖 (IAP)，我們需要整合 Google Play Billing Library。
*   **Product ID (商品 ID)**: 您在 Google Play Console 設定的商品 ID (例如 `com.example.mqttpanelcraft.premium_lifetime`)。
    *   **類型**: Managed Product (管理式商品 / 一次性買斷)。
*   **授權金鑰 (Base64 Encoded Public Key)**: 從 Google Play Console 的 "Monetization setup" 頁面獲取。

### C. 發布簽署 (Release Signing)
正式版 APK/AAB 必須使用私鑰簽署。
*   **Keystore File (.jks 或 .keystore)**: 您是否已有現成的 Keystore？如果沒有，我們可以建立一個新的。
*   **Key Alias (金鑰別名)**: 例如 `key0`。
*   **Password (密碼)**: Keystore 密碼與 Key 密碼。

### D. 隱私權政策 (Privacy Policy)
*   **隱私權政策 URL**: Google Play 上架必須提供。您需要一個網頁連結 (可使用 GitHub Pages 或 Google Sites 免費建立)。

---

## 2. 程式修改規劃 (Technical Implementation)

在收到上述資料後，我們將進行以下程式碼調整：

### A. 廣告系統切換 (AdManager)
*   將 `AdManager.kt` 中的測試與正式邏輯分離。
*   在 `AndroidManifest.xml` 中填入正式的 AdMob App ID。
*   在 `res/values/strings.xml` (或專屬 `ads.xml`) 中替換 Unit IDs。

### B. 接 Google Play 結帳系統 (Billing Integration)
*   **目前狀態**: `PremiumManager` 目前可能只是簡單的本地紀錄 (儲存在 SharedPrefs)。
*   **修改目標**:
    1.  引入 `com.android.billingclient:billing` 函式庫。
    2.  改造 `PremiumManager`，使其連線至 Google Play 有效性驗證。
    3.  實作「購買流程」：點擊 Premium 徽章或移除廣告按鈕 -> 喚起 Google Play 付費視窗 -> 付費成功 -> 更新 `PremiumManager` 狀態。

### C. 建置設定 (Build Configuration)
*   **build.gradle (Module)**:
    *   更新 `versionCode` (整數，每次上架+1) 與 `versionName` (字串，如 "1.0.0")。
    *   設定 `signingConfigs` 以使用您的 Keystore。
    *   啟用 `minifyEnabled true` (ProGuard/R8) 來混淆程式碼，保護您的 App 不被輕易反編譯，並縮減體積。

---

## 3. 發布流程建議 (Release Workflow)

1.  **Alpha/Internal Test**: 先將整合好 Billing 和廣告的 APK 上傳至 Google Play Console 的「內部測試軌道 (Internal Access)」。
    *   *注意：測試內購需要將測試帳號加入 "License Testers" 清單，才不用真的花錢。*
2.  **Open Beta**: 確認無嚴重崩潰後，開放至公開測試版。
3.  **Production**: 正式發布。

## 4. 接下來的行動 (Next Steps)

1.  請確認您是否已經擁有 **Google Play Developer Account** ($25 USD 一次性費用)。
2.  請確認您是否已註冊 **Google AdMob** 帳號。
3.  請告訴我您希望的付費模式是 **「一次性買斷 (Lifetime)」** 還是 **「訂閱制 (Subscription)」**？這會影響程式寫法。
    *   **已確認需求**: 採用「一次性買斷 (Lifetime)」模式。

## 5. 開發測試期間的廣告干擾解決方案 (Developer Experience)

為了讓您在開發測試時不被廣告干擾，同時又能模擬正式版環境，我們將採取以下策略：

### A. Debug 版本自動豁免 (Recommended)
我們可以在程式碼中加入判斷：
```kotlin
if (BuildConfig.DEBUG) {
   // 如果是 Debug 建置 (您在 Android Studio 跑的版本)，直接視為 Premium 用戶，不顯示廣告
   return true 
}
```
這樣您在開發時完全不會看到廣告。

### B. 隱藏的開發者開關 (Developer Toggle)
如果需要測試「由免費轉付費」的流程，單純自動豁免是不夠的。我們可以：
1.  在 **About (關於)** 頁面，長按版本號碼 5 次。
2.  彈出一個隱藏選單，讓您手動切換 `Premium Status` (模擬已購買/未購買)。
3.  此功能只在 `DEBUG` 版開放，正式版會自動移除。
