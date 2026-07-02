# WMBridge 修改记录

## v1.0.0-beta9

### 新增功能

**消息记录 Tab（消息已发/未发状态查看）**
- 新增持久化消息历史存储 `MessageHistory`，跨进程同步（`:bridge` 进程写入，主进程读取）
- 新增 `MessageEntry` 数据模型，每条记录含 sender / content / groupName / timestamp / 状态（PENDING / SENT / FAILED）
- 新增 `MessageHistoryActivity`，以 RecyclerView 展示所有已收集消息，每条显示发送者、内容、群名、时间、状态标签
- 状态圆点：🟢 已发 / 🔴 待发（未发）/ 🔴 失败
- 顶部摘要栏：显示总数、已发数、待发数、失败数
- 支持清空历史记录
- 新增入口按钮「📋 消息记录」在设置页按钮组中
- 对应文件：
  - `model/MessageEntry.kt` — 消息记录数据模型（新增）
  - `data/MessageHistory.kt` — 持久化存储（新增）
  - `res/layout/activity_message_history.xml` — 历史页布局（新增）
  - `res/layout/item_message_entry.xml` — 列表项布局（新增）
  - `MessageHistoryActivity.kt` — 历史页 Activity（新增）
  - `MainActivity.kt` — 新增入口按钮
  - `activity_main.xml` — 新增 btnHistory 按钮
  - `NotificationListener.kt` — 收集时记录 PENDING，发送成功记为 SENT，失败记为 FAILED
  - `AndroidManifest.xml` — 注册 MessageHistoryActivity
  - `colors.xml` — 新增状态标签配色
  - `strings.xml` — 新增 btn_history 字符串

### Bug 修复
- 消息发送失败后会在日志标记 FAILED，方便用户通过「消息记录」Tab 排查漏发问题

## v1.0.0-beta16

### Bug 修复
- **AuthorizationActivity 启动崩溃**：`item_permission.xml` 根元素是 CardView，但 `setupPermissionItems()` 中强转成了 `LinearLayout`，导致 `ClassCastException`。改为 `as View` 泛型转型，移除 `LinearLayout` 导入
- **消息记录页重叠状态栏**：根布局缺少 `fitsSystemWindows="true"`，顶部操作栏与系统状态栏重叠
- **关键词输入栏间距过小**：`tilKeywords` 的 `marginBottom=0dp` 与帮助文字的 `marginTop=2dp` 间距太小，多行时重叠。改为 `marginBottom=8dp`
- **关键词帮助文字与按钮组间距不足**：`tvKeywordHelp` 的 `marginBottom` 20dp→24dp，`btnTest` 添加 `marginBottom=8dp`
- **[根因] Android 15 edge-to-edge 未处理**：`targetSdk=35` 强制 edge-to-edge 渲染，但 `activity_main.xml` 和 `activity_authorization.xml` 缺少 `fitsSystemWindows="true"`，导致内容被状态栏和导航栏压缩，Layout 内所有 margin 实际可用空间远小于预期。添加 `fitsSystemWindows` 修复
- **关键词输入框高度计算偏差**：`Android:minLines="2"` 设在 `TextInputEditText` 上会导致 `TextInputLayout` 的 filled box 高度计算偏差，视觉上侵占下方帮助文字空间。去掉 `minLines`，改为默认单行 + `gravity="top"`，同时增大 `marginBottom` 8dp→12dp
- **[根治] 关键词帮助文字改用 Material 内置 helperText**：之前用独立 `TextView`（`tvKeywordHelp`）放在 TextInputLayout 下方，间距靠手动 margin 控制，不同设备上视觉表现不一致。按 Material Components 官方做法，改用 `app:helperText` + `app:helperTextEnabled` 将帮助文字置于 TextInputLayout 内部，由库自身控制布局和间距，彻底消除重叠可能
- **版本号未跟随迭代更新**：`build.gradle.kts` 中 `versionName` 一直写死为 `1.0.0-beta9`，`versionCode` 写死为 `9`，导致 beta10~15 安装后始终显示 beta9。现改为 `1.0.0-beta16 / versionCode=15`
- **关键词 TextInputLayout 添加显式 filled 样式**：按 Material Components Catalog 样例，添加 `style="?attr/textInputFilledStyle"` 确保 helper text 区域在不同设备上正确计算渲染

## v1.0.0-beta8

### 新增功能

**发送频率设置**
- 新增下拉选择：每收到一条 / 每3分钟 / 每5分钟 / 每10分钟 / 每20分钟 / 每30分钟
- "每收到一条"模式：每条通知立即发送到 Matrix
- 定时模式：攒批后定时发送，替代原来的固定 10 分钟
- 对应文件：`Config.kt`（新增 `SendFrequency` 枚举）、`NotificationListener.kt`（频率逻辑）

**关键词过滤**
- 三种模式：关闭 / 仅包含关键词 / 排除关键词
- 关键词以逗号分隔，同时匹配发送者、内容、群名称
- 对应文件：`Config.kt`（新增 `KeywordMode` 枚举）、`NotificationListener.kt`（`matchesKeywords()`）

**通知栏常驻开关**
- 可关闭前台服务的状态栏通知显示
- 关闭后使用 `IMPORTANCE_NONE` 通道，通知不显示在状态栏但仍需存在（Android 8+ 前台服务必须）
- 对应文件：`ForegroundService.kt`、`Config.kt`

**正式签名**
- 生成正式 keystore（存放于 `~/.android/wmbridge.keystore`，不提交到仓库）
- 签名密码通过环境变量注入（WMB_KEYSTORE_PATH、WMB_KEYSTORE_PASS、WMB_KEY_ALIAS、WMB_KEY_PASS）
- APK 输出文件名改为 `WMBridge.{versionName}.apk`

**安全加固**
- 仓库设为公开：从 git 移除 keystore 文件，加入 .gitignore
- build.gradle.kts 签名配置改为读取环境变量，移除硬编码密码
- 新增 GitHub Actions CI：tag 推送时自动签名发布 Release
- GitHub Secrets 配置：WMB_KEYSTORE_BASE64 / WMB_KEYSTORE_PASS / WMB_KEY_ALIAS / WMB_KEY_PASS

### 界面改进
- 按功能区划分：Matrix 配置 / 转发设置 / 关键词过滤
- 频率和关键词模式使用选择弹窗（AlertDialog 单选列表）
- 通知栏开关使用 SwitchMaterial
- 关键词输入带说明文字

### 优化
- 发送失败时重新入队（`NotificationListener.kt`）
- ProGuard 规则文件（`app/proguard-rules.pro`）
- Gradle 配置优化：JVM 参数、缓存配置

### Bug 修复
- **关键词输入栏布局重叠**：`activity_main.xml` tilKeywords 底部 margin 改为 0dp，帮助文字顶部加 2dp margin，输入框设 minLines=2 并 text top gravity，避免多行时与周边元素重叠

### 保活与权限增强（v1.0.0-beta9）
参考 TapClick (LGH1996/TapClick) 设计，新增以下保活和权限机制：

**新增文件**
- `AuthorizationActivity.kt` — 一站式权限引导页，500ms 轮询刷新状态
- `service/MyTileService.kt` — Quick Settings 快捷磁贴（运行中/已停止状态）
- `service/MyDeviceAdminReceiver.kt` — 设备管理员（防卸载门槛）
- `service/ScreenStateReceiver.kt` — SCREEN_OFF/SCREEN_ON 广播监听保活
- `service/BridgeProvider.kt` — ContentProvider IPC 跨进程通信
- `res/layout/activity_authorization.xml` — 权限引导页布局
- `res/layout/item_permission.xml` — 权限卡片模板
- `res/layout/item_keepalive.xml` — 保活开关卡片
- `res/xml/device_admin.xml` — 设备管理员策略声明
- `res/drawable/ic_check_green.xml` — 权限已开启图标
- `res/drawable/ic_close_red.xml` — 权限未开启图标
- `res/drawable/ic_tile.xml` — 快捷磁贴图标
- `BootReceiver.kt` — 开机自启（之前声明了但文件缺失，本次补上）

**修改文件**
- `AndroidManifest.xml` — 新增所有组件声明；Service 和 NLS 改为独立进程 `:bridge`
- `ForegroundService.kt` — 新增 `TYPE_ACCESSIBILITY_OVERLAY` 不可见悬浮窗保活；注册 SCREEN_OFF 广播接收器；新增 `ACTION_TOGGLE_OVERLAY` 动作
- `Config.kt` — 新增 keepAliveNotification、keepAliveOverlay、deviceAdminActivated 配置项；SharedPreferences 改为 `MODE_MULTI_PROCESS`
- `MainActivity.kt` — 新增"🔒 权限与保活设置"按钮 → 跳转 AuthorizationActivity
- `strings.xml` — 新增权限/保活相关字符串
- `activity_main.xml` — 新增 btnPermissions 按钮

**6 种保活手段**
1. 前台服务 + 持续通知 ✅（已有，增强）
2. TYPE_ACCESSIBILITY_OVERLAY 不可见悬浮窗 ✅（新增）
3. 独立进程 `:bridge` ✅（新增，服务与 UI 进程隔离）
4. SCREEN_OFF 广播监听 ✅（新增）
5. 快捷磁贴快速入口 ✅（新增）
6. 设备管理员防卸载 ✅（新增）

**权限引导 5 项**
- 通知使用权
- 通知权限（Android 13+）
- 忽略电池优化
- 设备管理员
- 悬浮窗保活权限

### 项目文件说明

```
wechat-matrix-bridge/
├── app/
│   ├── proguard-rules.pro    ← ProGuard/R8 混淆规则
│   └── src/
│       └── main/java/com/ximalu/wmbridge/
│           ├── data/
│           │   ├── Config.kt           ← 配置 + SendFrequency/KeywordMode 枚举
│           │   ├── BatchBuffer.kt      ← 攒批缓存（线程安全）
│           │   └── WeChatNotification.kt → model/ 下
│           ├── matrix/
│           │   └── MatrixClient.kt     ← Matrix REST API 客户端
│           ├── service/
│           │   ├── ForegroundService.kt    ← 前台服务（通知栏可隐藏）
│           │   └── NotificationListener.kt ← NLS 核心 + 关键词过滤 + 频率控制
│           ├── MainActivity.kt         ← 配置界面
│           └── WMBridgeApp.kt          ← Application 入口
│
├── CHANGES.md               ← 本文（修改记录 + 项目信息）
└── README.md                ← 项目简介
```
