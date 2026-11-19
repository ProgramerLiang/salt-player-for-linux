# 椒盐罐 · Salt Shaker

**Salt Player（椒盐音乐）的非官方 Linux 衍生版。**

> 「同一个调味，换了个罐子装。」
> 椒盐罐是社区自己找的容器：同样的味道，装它的人不是原作者。

本项目与 Salt Player 作者 Moriafly **无隶属关系**，也未经其认可或背书，不是官方产品。
如果你在寻找官方版本，请前往 <https://github.com/Moriafly/SaltPlayerSource>。

## 状态

| 能力 | 状态 |
|:--|:--|
| 编译 | `./gradlew :app:compileKotlin` 通过 |
| 测试 | `./gradlew :app:test` 69 项通过 |
| 包 | Arch `.pkg.tar.zst`、Deb、tar.gz（CI 另出 Rpm/AppImage） |
| 音频后端 | GStreamer（主）→ libmpv → Java Sound，设置页展示探测结果与安装命令 |
| MPRIS | 名称注册与 PlayPause/Next 等方法实测通过 |
| 托盘 | 本机 AWT 托盘不可用，设置页如实提示，不假装支持 |

## 命名由来

`salt-player` 这个名字属于上游，容易让人误以为是官方 Linux 版。改叫 **椒盐罐 / Salt Shaker**
既保留了「椒盐」的调味隐喻，又用「罐子」这个容器明确表达：这是别人装的另一罐。

## 构建

```bash
./gradlew :app:compileKotlin
./gradlew :app:test
./gradlew :app:createDistributable    # 原生 app-image
./gradlew :app:packageArch            # Arch .pkg.tar.zst
./gradlew :app:packageDebFallback     # 无需 dpkg-deb 的 .deb
./gradlew :app:packageTarGz           # 便携 tar.gz
```

Arch 用户本地构建（无需 root）：

```bash
./gradlew :app:packageArch
sudo pacman -U app/build/compose/binaries/main/arch/salt-shaker-0.2.0-1-x86_64.pkg.tar.zst
```

## 配置与数据

| 用途 | 路径 |
|:--|:--|
| 配置 | `~/.config/salt-shaker/settings.json` |
| 媒体库 | `~/.local/share/salt-shaker/library.db` |
| 封面缓存 | `~/.cache/salt-shaker/covers/` |
| 日志 | `~/.cache/salt-shaker/salt-shaker.log` |

首次启动会从旧目录 `~/.config/salt-player/` 与 `~/.local/share/salt-player/` **复制**（不移动）
设置与已扫描的媒体库，原目录保留以便回滚。该逻辑有单元测试覆盖。

## 发行版依赖

GStreamer 插件决定可播放格式；缺失时设置页显示可复制的安装命令并降级到可用后端。

```bash
# Debian/Ubuntu
sudo apt install gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
                 gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav
# Fedora
sudo dnf install gstreamer1-plugins-base gstreamer1-plugins-good \
                 gstreamer1-plugins-bad-free gstreamer1-plugins-ugly-free gstreamer1-libav
# Arch
sudo pacman -S gst-plugins-base gst-plugins-good gst-plugins-bad gst-plugins-ugly gst-libav
```

Flatpak 见 [flatpak/README.md](flatpak/README.md)（app-id `io.github.Corripo.SaltShaker`）。

## 许可证

LGPL-3.0-or-later（见 [LICENSE](LICENSE)）。上游版权归 Moriafly 与贡献者；
Linux 适配与改名后的修改部分 `Copyright (C) 2026 Corripo`。
依赖与致谢见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
