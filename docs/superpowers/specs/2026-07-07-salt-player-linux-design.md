# Salt Player for Linux — 设计文档

> 日期：2026-07-07 | 版本：v0.1.0-alpha | 目标系统：Ubuntu 24.04

## 1. 概述

Salt Player for Linux 是 Salt Player（椒盐音乐）的 Linux 桌面移植版，一款本地音乐播放软件。参考 [SaltPlayerSource](https://github.com/Moriafly/SaltPlayerSource) 的品牌定位与功能方向，使用 Tauri v2 + Rust + React 构建，首发 Ubuntu 24.04 支持。

## 2. 技术栈

| 层 | 技术 | 说明 |
|:--|:--|:--|
| 桌面壳 | Tauri v2 | 原生窗口、系统托盘、IPC 桥接 |
| 后端 | Rust | 音频引擎、媒体库扫描、MPRIS、配置 |
| 前端 | React + TypeScript + Tailwind CSS | 响应式 UI，类 Salt Player 风格 |
| 音频解码 | symphonia | 纯 Rust，支持 FLAC/MP3/AAC/OGG/WAV |
| 音频输出 | rodio + cpal | 跨平台音频播放 |
| 元数据 | lofty | 读取标签、封面 |
| MPRIS | mpris-server | Linux 桌面媒体控制集成 |
| 打包 | cargo-deb + AppImage | .deb 包 + AppImage |
| CI/CD | GitHub Actions | 自动构建、测试、发布 |

## 3. 架构

```
┌─────────────────────────────────────────────────────┐
│                   Web Frontend                       │
│  React + TypeScript + Tailwind CSS                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐        │
│  │ 播放控制  │ │ 播放列表  │ │ 音乐库/专辑   │        │
│  └──────────┘ └──────────┘ └──────────────┘        │
├─────────────────────────────────────────────────────┤
│              Tauri Bridge (IPC)                      │
│  invoke("scan_library") / invoke("play") / events   │
├─────────────────────────────────────────────────────┤
│                   Rust Backend                       │
│  ┌──────────┐ ┌──────────────┐ ┌────────────────┐  │
│  │ 音频引擎  │ │ 媒体库扫描器  │ │  MPRIS 服务    │  │
│  │ rodio +   │ │ walkdir +    │ │  mpris-server  │  │
│  │ symphonia │ │ lofty        │ │                │  │
│  └──────────┘ └──────────────┘ └────────────────┘  │
│  ┌──────────┐ ┌──────────────┐                      │
│  │ 播放队列  │ │ 配置持久化    │                      │
│  │ 管理器   │ │ (JSON 文件)   │                      │
│  └──────────┘ └──────────────┘                      │
├─────────────────────────────────────────────────────┤
│                   文件系统                           │
│     ~/.config/salt-player/  (配置 + 媒体库缓存)       │
│     ~/Music/                (默认扫描目录)            │
└─────────────────────────────────────────────────────┘
```

## 4. 数据流

### 4.1 媒体库扫描

```
用户选择目录 → 前端 invoke("scan_library", { path })
  → Rust spawn 后台线程
    → walkdir 遍历目录
    → lofty 读取元数据 (title, artist, album, cover, duration)
    → 写入 songs.json 缓存 (~/.config/salt-player/songs.json)
    → 前端 emit event("scan_progress", { current, total })
  → 扫描完成 emit event("scan_complete", { total })
  → 前端刷新歌曲列表
```

### 4.2 播放流程

```
前端 invoke("play", { song_id })
  → 播放队列管理器切换到该歌曲
  → 音频引擎加载文件 → symphonia 解码 → rodio/cpal 输出
  → 定时 emit event("playback_state", { position, duration, playing })
  → 前端更新进度条
  → MPRIS 同步状态
```

### 4.3 播放队列模型

```
queue: Vec<SongId>      // 当前队列
index: usize            // 当前播放位置
mode: Shuffle | Sequential | Repeat
```

- 上一首/下一首只移动 index
- 从列表点击播放：替换整个队列，index 设为点击位置
- 随机模式：shuffle 后按序播放

## 5. 页面路由

| 路由 | 页面 | 功能 |
|:--|:--|:--|
| `/library` | 音乐库 | 按专辑/艺术家/全部浏览 |
| `/playlist` | 播放队列 | 当前队列，可拖拽排序 |
| `/nowplaying` | 正在播放 | 大封面 + 播放控制 |
| `/settings` | 设置 | 扫描目录、语言、主题 |

## 6. 错误处理

| 场景 | 处理方式 |
|:--|:--|
| 文件不存在/不可读 | 跳过并记录日志，前端不弹窗 |
| 音频解码失败 | 跳过该文件，前端 toast 提示 |
| 设备无音频输出 | 启动时检测，前端显示错误页 |
| 配置读写失败 | 回退默认配置，重新写入 |
| 网络不可用 | 无网络依赖，不受影响 |

Rust 侧统一使用 `anyhow` + `thiserror`，IPC 返回 `Result<T, String>` 映射到前端。

## 7. 测试策略

- **Rust 单元测试**：音频引擎、队列管理器、元数据解析
- **前端组件测试**：播放控制、进度条、列表渲染（Vitest）
- **集成测试**：Tauri 端到端（启动 → 扫描 → 播放 → 暂停）
- **CI 检测**：`cargo test` + `cargo clippy` + `npm test` + `npm run build`

## 8. 打包发布

```
GitHub Actions 触发条件: 推送 v* 标签
  ├── Ubuntu 24.04 runner
  ├── 安装系统依赖 (libwebkit2gtk-4.1-dev 等)
  ├── cargo build --release
  ├── 打包 .deb (cargo-deb)
  ├── 打包 AppImage
  ├── 创建 GitHub Release
  └── 上传 .deb + AppImage
```

## 9. 首个版本范围 (v0.1.0-alpha)

- 本地音乐目录扫描（FLAC / MP3 / AAC / OGG / WAV）
- 播放控制：播放/暂停/上一首/下一首/进度拖动
- 播放列表 + 当前播放队列
- 专辑封面展示
- 基础元数据：标题、艺术家、专辑
- MPRIS 集成（Linux 媒体键 + 系统通知）
- 配置持久化（JSON 文件）
- 打包：.deb + AppImage，GitHub Actions 自动发布