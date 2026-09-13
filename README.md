# BiliForge · 哔哩缓存熔炉

> **用 Shizuku (ADB) 读取哔哩哔哩客户端视频缓存，以 FFmpeg 完成合成 / 转码 / 提取，全程零拷贝直写媒体库。**
> UI 基于 Jetpack Compose + [MIUIX](https://github.com/compose-miuix-ui/miuix)（HyperOS 设计语言）· `Android 13+` · 无 root · 无需存储权限

🔖 **[🇨🇳 中文](#-中文) | [🇺🇸 English](#-english)**

> ⚠️ **语言支持说明：应用界面目前仅支持中文（简体），多语言支持计划在后续版本加入。**
> **Language Notice: The app UI currently supports Chinese (Simplified) only; more locales are planned.**

---

## 🇨🇳 中文

### ✨ 特性

| 模块 | 说明 |
|---|---|
| 📦 **合成** | `video.m4s + audio.m4s → MP4`（`-c copy` 秒级）；旧版 `.blv` 分片自动拼接 |
| 🔄 **转码** | 720P / 1080P / H.265（libx265）；音频轨 320kbps·48kHz |
| 🎵 **提取** | 音频：M4A（无损直拷）/ MP3 320k；弹幕：ASS（含滚动轨道分配）/ SRT |
| 🚀 **零拷贝** | ffmpeg 以 ADB shell 身份运行——**直读缓存目录、直写 `/sdcard/Movies/BiliForge`** |
| 🔐 **免权限** | 通过 Shizuku 完成一切文件操作，不需要 root、不需要申请任何存储权限 |
| 🎨 **界面** | MIUIX（HyperOS）组件库 + 液态玻璃悬浮底栏 + 覆盖式二级页导航 |
| 📦 **引擎内置** | FFmpeg 8.1.2（aarch64 · 16KB 页对齐）打包于 APK，首次启动自动安装 |
| 📋 **运行日志** | 四档日志系统（UI / 任务 / 超级 / 渲染·代码级），支持导出 |

### 🏗 工作原理

```
┌──────────────────────── App 进程 ────────────────────────┐
│  Compose UI · 缓存扫描(entry.json) · 任务队列 · 进度解析    │
└─────────────────────┬────────────────────────────────────┘
                      │ Binder (自定义 AIDL: IRemoteService)
┌─────────────────────┴────────────────────────────────────┐
│  Shizuku Server（由 ADB 或 root 启动）                     │
│   └─ RemoteFsService（以 shell 身份运行）                  │
│        ├─ 读取 /sdcard/Android/data/tv.danmaku.bili/…     │
│        └─ 执行 ffmpeg（/data/local/tmp/biliforge）          │
└──────────────────────────────────────────────────────────┘
```

B 站缓存目录只有 shell 身份可读（Android 分区存储限制），因此所有文件操作与 ffmpeg 执行都在 Shizuku 侧完成；App 仅负责元数据解析与 UI —— 天然零拷贝，无需中间转存。

### 🔧 构建

**环境要求**：Android Studio（支持 AGP 9.4）· JDK 17+ · Android SDK 36

```bash
git clone <this-repo>
# 用 Android Studio 打开 → Sync → Run ▶
```
- 依赖镜像默认**阿里云优先**（见 `settings.gradle.kts`），国内网络开箱可用
- 发布签名：`cp keystore.properties.example keystore.properties` 后填入自己的 keystore（缺失该文件时 release 回退 debug 签名）

### 📲 使用

1. 安装 [Shizuku](https://shizuku.rikka.app/download/) 并以无线调试 / root 启动
2. 打开 BiliForge → 主页点「申请权限」授权
3. **首次启动自动安装 FFmpeg 引擎**（约 10~30 秒，进度见任务页）
4. 在哔哩哔哩客户端缓存想备份的视频
5. 「提取」页 → 扫描缓存 → 点卡片进入处理页（合成 / 转码 / 提取）
6. 产物输出至 `/sdcard/Movies/BiliForge/`（设置中可改）

### ⚙️ 关于内置 FFmpeg 引擎

仓库内置完整 ffmpeg 运行时（`app/src/main/assets/ffmpeg-pack.tar.gz`，约 57 MB——APK 体积主要来源）：

- 来源：**Termux 官方仓库 ffmpeg 8.1.2**（aarch64），已确认 16KB 页对齐（Android 15/16 要求）
- **不想要内置？** 删除该文件即可：应用自动回退「投放式」——把任意 arm64 ffmpeg 放入 `/sdcard/BiliForge/tools/` 后点「安装」
- 许可证提示：FFmpeg 二进制为 GPL/LGPL 构建，分发请遵守其许可证

### ❓ FAQ

- **提示「Shizuku 未运行」？** 到 Shizuku App 确认已启动，回来点「刷新状态」
- **引擎安装失败？** 确认任务页「安装 FFmpeg 工具链」完成；或改用投放式
- **转码报 `Unknown encoder 'libx264'`？** 你替换的 ffmpeg 不含该编码器，用回内置引擎
- **输出在哪？** `/sdcard/Movies/BiliForge/`，媒体扫描后相册/播放器可见

### 🧱 技术栈与致谢

- [Jetpack Compose](https://developer.android.com/compose) / [AndroidX](https://developer.android.com/jetpack)
- [MIUIX](https://github.com/compose-miuix-ui/miuix)（HyperOS 组件库 · Apache-2.0）
- [Shizuku](https://github.com/RikkaApps/Shizuku) / Shizuku-API（RikkaApps）
- 液态玻璃效果移植自 miuix 官方示例（Apache-2.0 · [Kyant0](https://github.com/Kyant0/AndroidLiquidGlass)）
- FFmpeg（[Termux](https://github.com/termux) 官方构建）· [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)

### ⚠️ 免责声明

本项目仅供**个人学习**与**备份自己已缓存的视频**使用；不提供任何内容下载、破解能力；请勿用于商业用途。缓存内容版权归哔哩哔哩及原作者所有。

### 📄 许可证

代码以 [Apache-2.0](LICENSE) 发布。内置 FFmpeg 二进制来自 Termux 构建（GPL/LGPL），分发请遵循其许可证。

---

## 🇺🇸 English

> ⚠️ **Language Notice: The app UI currently supports Chinese (Simplified) only.** English and other locales are planned for future releases.

### ✨ Features

| Module | Description |
|---|---|
| 📦 **Merge** | `video.m4s + audio.m4s → MP4` (`-c copy`, near-instant); legacy `.blv` segments auto-concatenated |
| 🔄 **Transcode** | 720p / 1080p / H.265 (libx265); audio 320kbps · 48kHz |
| 🎵 **Extract** | Audio: M4A (lossless copy) / MP3 320k; Danmaku: ASS (with scrolling-track layout) / SRT |
| 🚀 **Zero-copy** | ffmpeg runs as ADB shell — reads the cache and writes `/sdcard/Movies/BiliForge` directly |
| 🔐 **No permissions** | Everything goes through Shizuku — no root, no storage permission prompts |
| 🎨 **UI** | MIUIX (HyperOS) components + liquid-glass floating navigation bar |
| 📦 **Bundled engine** | FFmpeg 8.1.2 (aarch64, 16KB-page aligned) shipped in the APK, auto-installed on first launch |
| 📋 **Logging** | Four-level runtime logging (UI / Task / Super / Render·Code-level), exportable |

### 🏗 How It Works

Bilibili's cache directory is only readable by the shell user (scoped-storage restriction), so all file I/O and ffmpeg execution happen on the Shizuku side via a custom AIDL `RemoteFsService`. The app itself only parses metadata and renders UI — inherently zero-copy, no intermediate staging.

### 🔧 Build

**Requirements**: Android Studio (AGP 9.4+) · JDK 17+ · Android SDK 36

```bash
git clone <this-repo>
# Open with Android Studio → Sync → Run ▶
```
- Aliyun Maven mirrors are pre-configured (see `settings.gradle.kts`)
- Signing: `cp keystore.properties.example keystore.properties` and fill in your keystore (falls back to debug signing if absent)

### 📲 Usage

1. Install [Shizuku](https://shizuku.rikka.app/download/) and start it (wireless debugging / root)
2. Open BiliForge → tap "Request permission" on the Home tab
3. **The FFmpeg engine installs automatically on first launch** (~10–30 s, progress on the Tasks page)
4. Cache the videos you want to back up in the Bilibili app
5. "Extract" tab → scan cache → tap a card → choose an action (merge / transcode / extract)
6. Output goes to `/sdcard/Movies/BiliForge/` (configurable in Settings)

### ⚙️ About the Bundled FFmpeg

The repo ships a full ffmpeg runtime (`app/src/main/assets/ffmpeg-pack.tar.gz`, ~57 MB — the main reason for the APK size):

- Source: **Termux official repo ffmpeg 8.1.2** (aarch64), verified 16KB-page aligned (required by Android 15/16)
- **Don't want it bundled?** Just delete the file — the app falls back to "side-load" mode: drop any arm64 ffmpeg into `/sdcard/BiliForge/tools/` and tap "Install"
- License note: the FFmpeg binaries are GPL/LGPL builds; redistribution must comply with their licenses

### ❓ FAQ

- **"Shizuku is not running"?** Make sure Shizuku is started, then tap "Refresh status"
- **Engine installation failed?** Ensure the "Install FFmpeg toolchain" task finished, or switch to side-load mode
- **`Unknown encoder 'libx264'`?** Your replacement ffmpeg lacks that encoder — use the bundled engine
- **Where is the output?** `/sdcard/Movies/BiliForge/`, visible in galleries/players after media scan

### 🧱 Tech Stack & Credits

- [Jetpack Compose](https://developer.android.com/compose) / [AndroidX](https://developer.android.com/jetpack)
- [MIUIX](https://github.com/compose-miuix-ui/miuix) (HyperOS design language · Apache-2.0)
- [Shizuku](https://github.com/RikkaApps/Shizuku) / Shizuku-API (RikkaApps)
- Liquid-glass effects ported from the official miuix samples (Apache-2.0 · [Kyant0](https://github.com/Kyant0/AndroidLiquidGlass))
- FFmpeg ([Termux](https://github.com/termux) builds) · [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)

### ⚠️ Disclaimer

This project is for **personal learning** and **backing up your own cached videos** only. It provides no downloading or cracking capability and must not be used commercially. All cached content is copyright of Bilibili and its original authors.

### 📄 License

Code is released under [Apache-2.0](LICENSE). The bundled FFmpeg binaries are Termux GPL/LGPL builds.
