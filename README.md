# Salt Player for Linux

椒盐音乐（Salt Player）的 Linux 桌面重做版。采用 **Compose Multiplatform Desktop + SaltUI 3.0.0**，按 Android 平板版形态设计：左侧导航 + 右侧内容 + 顶部标题/搜索 + 底部迷你播放条，可展开全屏播放页与逐行歌词。

## 运行状态

| 能力 | 状态 |
|:--|:--|
| 编译 | `./gradlew :app:compileKotlin` 通过 |
| 测试 | `./gradlew :app:test` 66/66 通过 |
| 包 | `packageDebFallback`（159 MB `.deb`）、`packageTarGz`（160 MB `.tar.gz`）、`packageArch`（142 MB `.pkg.tar.zst`）真实产出 |
| 音频后端 | GStreamer（主）→ libmpv → Java Sound，设置页展示探测结果与安装命令 |
| MPRIS | 名称注册、PlayPause/Next 等方法调用实测通过；Properties Get 待修（见报告） |

## 技术栈

| 层 | 技术 |
|:--|:--|
| UI | Compose Multiplatform 1.12.2 + SaltUI 3.0.0（官方 UI 库，Apache-2.0） |
| 语言/构建 | Kotlin 2.4.20 / Gradle 8.13 / JDK 17 |
| 数据 | SQLite（sqlite-jdbc）、jaudiotagger 标签解析、XDG 路径 |
| 播放 | GStreamer 1.x（gst1-java-core）、libmpv（JNA）、Java Sound 兜底 |
| 桌面 | MPRIS D-Bus（dbus-java）、托盘、单实例、GTK/portal 主题探测 |

## 构建与测试

```bash
./gradlew :app:compileKotlin
./gradlew :app:test
./gradlew :app:createDistributable              # app-image
./gradlew :app:packageDebFallback               # 不依赖 dpkg-deb 的 .deb
./gradlew :app:packageTarGz                     # 便携 tar.gz
./gradlew :app:packageArch                      # Arch .pkg.tar.zst
```

CI（Ubuntu 24.04）会尝试标准 `packageDeb`/`packageRpm`/`packageAppImage` 并上传全部 artifact。

## 发行版依赖

GStreamer 插件决定可播放格式；缺插件时设置页会显示可复制的安装命令，且自动降级到可用后端。

**Debian/Ubuntu**
```bash
sudo apt install gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
                 gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav
```
**Fedora**
```bash
sudo dnf install gstreamer1-plugins-base gstreamer1-plugins-good \
                 gstreamer1-plugins-bad-free gstreamer1-plugins-ugly-free gstreamer1-libav
```
**Arch**
```bash
sudo pacman -S gst-plugins-base gst-plugins-good gst-plugins-bad gst-plugins-ugly gst-libav
```
**openSUSE**
```bash
sudo zypper install gstreamer-plugins-base gstreamer-plugins-good \
                   gstreamer-plugins-bad gstreamer-plugins-ugly gstreamer-plugins-libav
```

## Flatpak

见 [flatpak/README.md](flatpak/README.md)，清单在 `flatpak/io.github.Corripo.SaltPlayer.yml`。

## 许可证

本项目主体为 **LGPL-3.0-or-later**（见 [LICENSE](LICENSE)）。包含派生自 `Moriafly/SaltPlayerCommunity`（LGPL-2.1-or-later）的设计参考/实现，原始版权归 Moriafly 与贡献者；依赖 `Moriafly/SaltUI`（Apache-2.0）。保留原始 Salt Player 版权声明；Linux 适配部分 `Copyright (C) 2026 Corripo`。详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
