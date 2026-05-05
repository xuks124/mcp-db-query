# Hermes CUA Helper

Android 辅助 APP，让 Hermes AI 助手能直接操作手机界面。
不需要 WiFi、不需要 ADB、不需要 Root。

## 原理

本 APP 通过 Android 系统标准 API 提供两个核心能力：

1. **无障碍服务 (AccessibilityService)** — 点击、滑动、输入文字、返回/Home/多任务
2. **屏幕截取 (MediaProjection)** — 截取屏幕画面供 AI 分析

Hermes 通过本地 HTTP（127.0.0.1:8765）与 APP 通信，不需要任何网络连接。

## 使用方法

### 1. 安装
从 Releases 或 Actions Artifacts 下载 APK，安装到手机上。

### 2. 授权
打开 APP，依次操作：
1. **启用无障碍服务** — 设置 → 无障碍 → 已安装的应用 → Hermes CUA 助手 → 开启
2. **授权屏幕截图** — 点击按钮，在弹出的对话框中选择「立即开始」
3. **启动服务** — 点击按钮，状态显示「运行中」

### 3. 使用
启动后，Hermes 会自动通过 `127.0.0.1:8765` 连接。你就可以对 Hermes 说：
- "打开微信给张三发早安"
- "帮我看看当前屏幕上有什么"
- "打开设置，关闭蓝牙"

## API 接口（供 Hermes 调用）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/status` | 获取服务状态 |
| GET | `/screenshot` | 获取屏幕截图 PNG |
| GET | `/ui` | 获取 UI 层级 XML |
| GET | `/info` | 获取设备信息（分辨率等） |
| POST | `/tap` | 点击 `{"x":540, "y":1200}` |
| POST | `/swipe` | 滑动 `{"x1":0,"y1":500,"x2":0,"y2":200}` |
| POST | `/text` | 输入文字 `{"text":"Hello"}` |
| POST | `/key` | 按键 `{"key":"BACK"}` |

## 构建方式

### 方式一：GitHub Actions（推荐）
推送代码到 GitHub，Actions 会自动编译并上传 APK。

### 方式二：手动构建
```bash
git clone https://github.com/xuks124/cua-helper
cd cua-helper
chmod +x gradlew
./gradlew assembleRelease
```

## 环境要求

- Android 11 (API 30) 或更高版本
- 无需 Root
- 无需网络连接
# trigger rebuild
