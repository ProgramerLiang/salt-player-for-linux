# Salt Player for Linux

Salt Player（椒盐音乐）的 Linux 桌面版，基于 [SaltUI](https://github.com/Moriafly/SaltUI) + [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)。

## 功能

- 🎵 本地音乐扫描（mp3/flac/ogg/wav/m4a 等）
- 🖼️ 内嵌封面显示
- ▶️ 播放控制（播放/暂停/下一首/上一首）
- 🔀 四种播放模式（顺序/随机/单曲循环/列表循环）
- 📋 播放队列管理
- ⚙️ 设置页面（音乐目录 / 音量 / 主题）
- 💾 配置持久化至 `~/.config/salt-player/`

## 构建

**环境要求：** JDK 17+

```bash
# 克隆仓库
git clone https://github.com/ProgramerLiang/salt-player-for-linux.git
cd salt-player-for-linux

# 编译并运行
./gradlew :app:run

# 打包 .deb
./gradlew :app:packageDeb
# 输出: app/build/compose/binaries/main/deb/salt-player_*.deb

# 打包 AppImage
./gradlew :app:packageAppImage
```

**系统依赖（Ubuntu 24.04）：**

```bash
sudo apt install libasound2-dev libx11-dev libxext-dev libxrender-dev \
                 libxtst-dev libxi-dev libfreetype-dev
```

## 技术栈

| 层 | 技术 |
|:--|:--|
| UI 框架 | Compose Multiplatform 1.11.0-beta02 |
| UI 库 | SaltUI 2.9.0-beta02（椒盐音乐官方 UI 库） |
| 语言 | Kotlin 2.3.20 |
| 构建 | Gradle 8.13 |
| 音频播放 | javax.sound (SourceDataLine) |
| 元数据 | jaudiotagger 3.0.1 |
| 序列化 | kotlinx-serialization-json |

## 项目结构

```
app/src/main/kotlin/com/saltplayer/
├── Main.kt                    # 应用入口
├── AppState.kt                # 全局状态管理
├── AppLayout.kt               # 整体布局
├── model/Model.kt             # 数据模型
├── scanner/LibraryScanner.kt  # 音频文件扫描
├── player/AudioPlayer.kt      # 音频播放引擎
├── queue/PlayQueue.kt         # 播放队列
├── config/ConfigManager.kt    # 配置持久化
└── ui/
    ├── theme/AppTheme.kt       # SaltTheme 封装
    ├── component/
    │   ├── Sidebar.kt          # 侧边导航
    │   └── PlayerBar.kt        # 底部播放栏
    └── screen/
        ├── LibraryScreen.kt    # 音乐库
        ├── NowPlayingScreen.kt # 正在播放
        ├── PlaylistScreen.kt   # 播放队列
        └── SettingsScreen.kt   # 设置
```

## License

MIT License.

```text
Copyright (c) 2021-2026 Moriafly
```

Linux 适配部分：

```text
Copyright (c) 2026 Corripo
```
