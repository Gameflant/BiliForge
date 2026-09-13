# BiliForge · 哔哩缓存熔炉

> **用 Shizuku (ADB) 读取哔哩哔哩客户端视频缓存，以 FFmpeg 完成合成 / 转码 / 提取，全程零拷贝直写媒体库。**
> UI 基于 Jetpack Compose + [MIUIX](https://github.com/compose-miuix-ui/miuix)（HyperOS 设计语言），深色霓虹 + 液态玻璃悬浮底栏。
>
> `Android 13+` (minSdk 33) · `targetSdk 36`（Android 16）· Kotlin 2.x · 无 root · 无需存储权限

---

## ✨ 特性

| 模块 | 说明 |
|---|---|
| 📦 **合成** | `video.m4s + audio.m4s → MP4`（`-c copy` 秒级）；旧版 `.blv` 分片自动拼接 |
| 🔄 **转码** | 720P / 1080P / H.265（libx265）/ 音频轨 320kbps·48kHz |
| 🎵 **提取** | 音频：M4A（无损直拷）/ MP3 320k；弹幕：ASS（含滚动轨道分配）/ SRT |
| 🚀 **零拷贝** | ffmpeg 以 ADB shell 身份运行——**直读缓存目录、直写 `/sdcard/Movies/BiliForge`** |
| 🔐 **免权限** | 通过 Shizuku 完成一切文件操作，不需要 root、不需要申请任何存储权限 |
| 🎨 **界面** | MIUIX（HyperOS）组件库 + 液态玻璃悬浮底栏 + 覆盖式二级页导航 |
| 📦 **引擎内置** | FFmpeg 8.1.2（aarch64 · 16KB 页对齐）打包于 APK，首次启动自动安装 |
| 📋 **运行日志** | 四档日志系统（UI / 任务 / 超级 / 渲染·代码级），支持导出 |

## 🏗 工作原理

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

- B 站缓存目录只有 shell 身份可读（Android 分区存储限制），因此所有文件操作与 ffmpeg 执行都在 Shizuku 侧完成；
- App 仅负责元数据解析与 UI —— 天然零拷贝，无需中间转存。

## 🔧 构建

### 环境
- Android Studio（支持 AGP 9.4 的版本）
- JDK 17+
- Android SDK 36（Android 16）

### 步骤
```bash
git clone <this-repo>
# 用 Android Studio 打开 → Sync → Run ▶ 或 Build > Generate Signed APK
```
- 依赖镜像已默认配置**阿里云优先**（`settings.gradle.kts`），国内网络开箱可用；
- 首次 Sync 需下载依赖（数百 MB）。

### 签名（发布版）
```bash
cp keystore.properties.example keystore.properties
# 编辑填入你自己的 keystore 信息；缺失该文件时 release 构建回退 debug 签名
```

## 📲 使用

1. 安装 [Shizuku](https://shizuku.rikka.app/download/) 并以无线调试 / root 启动
2. 打开 BiliForge → 主页点「申请权限」授权
3. **首次启动会自动安装 FFmpeg 引擎**（约 10~30 秒，进度见任务页）
4. 在哔哩哔哩客户端缓存想备份的视频
5. 「提取」页 → 扫描缓存 → 点卡片进入处理页（合成 / 转码 / 提取）
6. 产物输出至 `/sdcard/Movies/BiliForge/`（可在设置中更改输出目录）

## ⚙️ 关于内置 FFmpeg 引擎

仓库内置了完整 ffmpeg 运行时（`app/src/main/assets/ffmpeg-pack.tar.gz`，约 57 MB —— 这也是 APK 体积的主要来源）：

- 来源：**Termux 官方仓库 ffmpeg 8.1.2**（aarch64），已校验 16KB 页对齐（Android 15/16 要求）；
- **不想要内置？** 直接删除该文件即可：
  应用会自动回退「投放式」——把任意 Android/arm64 ffmpeg 放入 `/sdcard/BiliForge/tools/` 后点「安装」。
- 许可证提示：FFmpeg 二进制为 GPL/LGPL 构建，分发请遵守其许可证（详见 LICENSE 末尾说明）。

## 📂 项目结构

```
app/src/main/
├── assets/ffmpeg-pack.tar.gz              # 内置 FFmpeg 引擎
├── java/com/feringsapp/biliforge/
│   ├── core/shizuku/                      # Shizuku 桥接 / ADB 身份服务 / 日志
│   ├── data/                              # entry.json 解析 / 缓存扫描 / 设置
│   ├── media/                             # FFmpeg 引擎 / 弹幕转换 / 回退合成器
│   ├── work/                              # 任务队列 / 前台服务 / 引擎状态
│   └── ui/                                # MIUIX 主题 / 液态玻璃 / 页面
└── res/                                   # 自适应图标（含主题图标）
```

## ❓ FAQ

**Q: 提示「Shizuku 未运行」？**
到 Shizuku App 确认已启动，回来点「刷新状态」。

**Q: 引擎安装失败 / ffmpeg 不存在？**
确认任务页的「安装 FFmpeg 工具链」完成；或改投放式（见上）。

**Q: 转码报 `Unknown encoder 'libx264'`？**
你替换的 ffmpeg 构建不含该编码器，换用内置引擎或包含 x264/x265 的完整构建。

**Q: 输出在哪？**
`/sdcard/Movies/BiliForge/`，媒体扫描后相册/播放器可见。

## 🧱 技术栈与致谢

- [Jetpack Compose](https://developer.android.com/compose) / [AndroidX](https://developer.android.com/jetpack)
- [MIUIX](https://github.com/compose-miuix-ui/miuix)（HyperOS 设计语言组件库 · Apache-2.0）
- [Shizuku](https://github.com/RikkaApps/Shizuku) / Shizuku-API（RikkaApps）
- 液态玻璃效果移植自 miuix 官方示例（Apache-2.0，原作者 [Kyant0](https://github.com/Kyant0/AndroidLiquidGlass)）
- FFmpeg（[Termux](https://github.com/termux) 官方构建）· [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)

## ⚠️ 免责声明

本项目仅供**个人学习**与**备份自己已缓存的视频**使用；
不提供任何内容下载、破解能力；请勿用于商业用途。
缓存内容版权归哔哩哔哩及原作者所有。

## 📄 许可证

代码以 [Apache-2.0](LICENSE) 发布。
内置 FFmpeg 二进制来自 Termux 构建（GPL/LGPL），分发请遵循其许可证；
不需要时删除 `app/src/main/assets/ffmpeg-pack.tar.gz` 即可（应用会自动回退外部 ffmpeg 方案）。
