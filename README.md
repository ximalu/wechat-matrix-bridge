# WeChat Matrix Bridge (WMBridge)

Android 通知监听应用：将微信通知实时转发到 Matrix 房间，由 Hermes AI Agent 管理。

## 架构

```
Android (NLS 监听微信通知)
  → 本地缓存，攒批 (3条/10分钟)
    → Matrix REST API POST
      → 专用 Matrix 房间
        → Hermes 读取 → 分类/归档/提醒
```

## 技术栈

- **语言:** Kotlin
- **最低版本:** Android 8.0 (API 26)
- **编译版本:** Android 15 (API 35)
- **核心依赖:** OkHttp, Gson, Coroutines

## 使用方式

1. 下载 APK 安装到手机
2. 在系统设置中授予「通知使用权」
3. 输入 Matrix 服务器地址、Access Token、房间 ID
4. 点击保存，服务自动启动

## 构建

```bash
./gradlew assembleDebug
```

APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## License

MIT
