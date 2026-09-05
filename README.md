<p align="center"><img src="docs/images/icon.png" width="112" alt="MobiMark 圖示"></p>

# MobiMark

**把手機日常使用，化為可記錄、可重複的續航測試。**

繁體中文（預設） · [English](README.en.md) · [下載 APK](https://github.com/ahui3c/MobiMark/releases/latest) · [測試規範](docs/TEST_PROTOCOL.zh-TW.md) · [素材授權](THIRD_PARTY_ASSETS.md)

MobiMark 是 3C 達人廖阿輝的 Android 行動設備續航測試工具，依固定順序循環執行遊戲、網頁、影片、錄影與文書工作，記錄電量變化，換算等效 **100% 電量續航**。原名 Ahuimark，沿用套件名稱 `tw.ahuimark.battery`，保留既有安裝與資料相容性。

> **v0.3.1 為預覽版 / debug APK**，不是正式簽章的商店版本。換算成績不是實際從 100% 使用到 0% 的保證，也不是 PCMark 或 BAPCo MobileMark 成績；本專案與這些產品及廠商無關。

## 下載與開始

1. 從 [Release](https://github.com/ahui3c/MobiMark/releases/latest) 下載 `MobiMark-v0.3.1-debug.apk`，允許下載來源安裝應用程式。可用同頁 `SHA256SUMS.txt` 核對檔案。
2. 使用 Android 8.0（API 26）以上設備；完整執行仍須符合 3D 引擎、後鏡頭 4K30、儲存空間等要求，最低 Android 版本不保證硬體相容。
3. 設定內容、授予相機權限；自動勿擾開啟時，另需勿擾存取權。網頁與影片安裝後預設均為**線上模式**。
4. 用白畫面與外部亮度計校正至 **200 nits**，關閉自動亮度、拔除充電器，電量**高於 80%** 後開始。
5. 先進行不計分的預備耗電；首次讀到電量 ≤80% 時，重設正式計時與工作順序，從 3D 遊戲重新開始。

自行編譯的 debug 簽章可能與 Release 不同，不能保證覆蓋安裝。**不要為更新直接清除資料或解除安裝，請先匯出需要的成績。**

## 軟體畫面

以下是程式實際擷取畫面，不是 AI 介面示意圖。首頁來自 v0.3.0 模擬器；計時畫面來自 v0.2.9 實際 Compose 元件測試，使用固定示範資料，**不是續航實測結果**。v0.3.1 的主要變更為放大圖示本體，最新圖示見頁首。

| 首頁與模式選擇 | 直向測試資訊與文書畫面 |
| --- | --- |
| <img src="docs/images/home.png" width="300" alt="MobiMark 首頁"> | <img src="docs/images/test-portrait.png" width="300" alt="直向測試畫面，固定示範計時"> |

橫向模式採緊湊資訊列，同時顯示已測試時間與快速模式剩餘時間：

![橫向快速測試與辦公畫面，數字為示範資料](docs/images/test-landscape.png)

Godot 3D 工作負載開發版的實際渲染擷取，並非 v0.3.1 完整測試 UI；畫面中的 FPS 標示不代表固定或實測幀率：

![Godot 森林戰鬥開發畫面](docs/images/godot-scene.png)

## 五種工作負載

**3D 遊戲 → 網頁瀏覽 → 影音播放 → 後鏡頭錄影 → 文書工作**，每項 3 分鐘，每循環 15 分鐘。視訊通話測試已移除。

| 項目 | 執行內容 |
| --- | --- |
| 3D 遊戲 | Godot 4.7.2 第三人稱戰鬥；人類戰士、幻想怪物、步槍／火箭／光劍，森林、都市、海岸與天氣變化。骨架動畫、材質、陰影、粒子與後製；橫向、內部 1920×1080 渲染。 |
| 網頁瀏覽 | 線上或離線可選。最多 3 個網址，可留空但至少 1 個有效網址；每分鐘輪換、連續捲動，到底重新整理。離線頁含大量文字、圖片及表格。 |
| 影音播放 | 線上影片或下載後的標準本地影片，橫向播放。本地素材為 1080p30 MP4；線上畫質受服務、網路與裝置影響，並非固定解析度。 |
| 後鏡頭錄影 | CameraX 要求 3840×2160、30 fps，顯示預覽與錄影介面；不以 1080p 悄悄替代 4K。實際硬體與輸出仍須驗證。 |
| 文書工作 | 模擬手機編輯文件、試算表及簡報，搭配大字與動態圖表；實際建立 DOCX、XLSX、PPTX、插入圖片、計算指定 SUM 公式、壓縮與重新開啟驗證。不是 Microsoft Office 本體或完整 Excel 引擎。 |

各項要求設備在目前解析度下可用的高更新率模式；**顯示更新率不等於實際渲染 FPS**，影片不因此補成高幀率，錄影目標仍為 30 fps。

### 預設線上內容

- 網頁：[ahui3c.com](https://ahui3c.com)、[玩具人](https://www.toy-people.com/)、[LPComment](https://lpcomment.com/)。
- 影片：[YouTube 測試影片](https://youtu.be/1b-_FC_hIAQ)。
- 1 個網址：A → A → A；2 個網址：A → B → A；3 個網址：A → B → C，每段 1 分鐘。
- 本地影片需另外下載，APK 不內附；程式下載時驗證大小與 SHA-256。素材來源及權利說明見[第三方素材](THIRD_PARTY_ASSETS.md)。

## 模式與成績

| 模式 | 實際停止條件 | 成績意義 |
| --- | --- | --- |
| 完整測試 | 正式開始後持續至電量 ≤20% | 記錄約 80%→20% 的實測區間，再換算等效 100%。 |
| 快速測試 | 正式測試 4 小時，或電量先降至 ≤20% | 以已記錄時間與實際耗電外推，不假設已消耗 60%。 |
| 當機紀錄推算 | 重新開啟後使用最後有效的正式紀錄 | 兩種模式均適用；不補算當機後時間，也不是從中斷處續跑。 |

設紀錄時間為 `T`，消耗電量為 `D` 個百分點：

```text
等效 100% 續航 = T × 100 / D
等效 80%→20% 時間 = T × 60 / D
```

例如 3 小時消耗 24 個百分點，等效 60% 時間為 7 小時 30 分、等效 100% 為 12 小時 30 分。電量消耗不一定線性；**完整、快速、當機推算應分組比較**。完整模式也未真正量測 100%→0%。詳見[測試規範](docs/TEST_PROTOCOL.zh-TW.md)。

## 記錄、保護與校正

- 每秒記錄電量與設備可提供的遙測；正式測試每 30 秒儲存備援檢查點。
- 多組成績自動儲存，可回顧、刪除、匯出；報告 ZIP 包含 PDF、JSON、CSV 與事件紀錄，提供分項時間與耗電估算。
- 測試中保持螢幕喚醒，結束後解除，不改動原本系統休眠逾時設定。
- 啟動時將媒體音量調至最低，離開／結束時依保護規則恢復；不覆蓋使用者其間自行更改的音量。
- 選用自動勿擾，結束後移除程式規則或恢復先前狀態；不是系統鎖定模式，無法保證攔截所有系統或緊急提示。
- 充電、離開前景、電池溫度達 48°C 等情況會中止測試；正常停止與安全中止不等同當機恢復。
- 亮度校正只顯示全白畫面，**沒有亮度滑桿，也不自動設定或驗證 200 nits**，需外部儀器搭配系統或 ADB 調整。

## 測試條件摘要

**程式檢查**：電量 >80%、未充電、相機權限、後鏡頭 4K30 能力、至少 3 GiB 可用空間、有效內容設定，以及啟用自動勿擾時所需權限。前次未處理的恢復紀錄須先處理。

**操作者統一條件（非程式自動保證）**：200 nits、關閉自動亮度、相同解析度／更新率設定、固定網路與影片來源、相近電池健康度與環境溫度。建議環境 23±2°C，每台冷卻後重複至少 3 次取中位數。USB／ADB 連線不得讓測試設備持續充電。

網頁、廣告、串流品質、OS 或工作負載版本都會改變結果。需要較高重現性時，同時選用離線網頁與相同本地影片，並記錄條件。跨 iOS／Android 或其他 benchmark 的數值不應直接視為同一量尺。

## 從原始碼建置

需求：JDK 17、Android SDK 36、Platform Tools；建置遊戲素材另需 **Godot 4.7.2 與對應 Android 匯出範本**。Gradle Wrapper 已內附；首次建置需網路與足夠磁碟空間。`godot/` 原始模型與貼圖依各自授權發布。

```powershell
git clone https://github.com/ahui3c/MobiMark.git
cd MobiMark
# 將 JAVA_HOME 與 ANDROID_HOME 指向本機 JDK 17 與 Android SDK。
.\tools\build-godot.ps1 -Godot 'C:\Tools\Godot\Godot_v4.7.2-stable_win64_console.exe'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

也可從**相同版本 Release** 下載 `prism_front.pck` 放入 `app/src/main/assets/` 再執行 Gradle；修改遊戲後須重新匯出，不可混用版本。預設 APK：`app/build/outputs/apk/debug/app-debug.apk`。其他作業系統可依 PowerShell 腳本順序執行相同 Godot CLI 指令與 `./gradlew`。

既有 v0.3.1 建置紀錄：debug APK、Android 測試 APK 建置成功；73 項單元測試通過；Lint 0 errors、18 warnings。這**不代表**完成 4 小時／80%→20% 耐久測試，也不代表所有實體設備、YouTube、相機與 GPU 相容性均已驗證。

## 授權與回報

原創程式碼依 [Apache-2.0](LICENSE) 發布；Godot 為 MIT，模型及貼圖使用 CC BY 4.0／CC0，須保留相應署名。詳見 [THIRD_PARTY_ASSETS.md](THIRD_PARTY_ASSETS.md) 與 [godot/CREDITS.txt](godot/CREDITS.txt)。模型作者不為本專案背書；未使用 Overwatch 素材。外部網站、影片不隨程式碼授權重新授權。

請於 [Issues](https://github.com/ahui3c/MobiMark/issues) 提供設備型號、Android／App 版本、模式、內容來源與重現步驟；分享報告前請檢查自訂網址、設備資訊或影像隱私。
