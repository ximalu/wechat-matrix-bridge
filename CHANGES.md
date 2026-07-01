# WMBridge 修改记录

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
