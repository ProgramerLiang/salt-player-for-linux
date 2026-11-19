# AGENTS.md — 本仓库的版本与发布约定

本文件是项目级约定，后续任何 agent / 贡献者都按此执行，不需要每轮口头交代。

## 1. 版本号

- **每轮本地 review 通过后、打包之前，必须升级版本号。**
  位置：`app/build.gradle.kts` 的 `version` 与 `packageVersion`，以及
  `app/src/main/kotlin/com/saltplayer/BuildInfo.kt` 的 `VERSION`（界面「关于」里展示）。
- 升级幅度遵循语义化版本：
  | 变更类型 | 示例 |
  |:--|:--|
  | 修复、优化、文档 | patch：`0.2.0 → 0.2.1` |
  | 新功能（向后兼容） | minor：`0.2.1 → 0.3.0` |
  | 破坏性变更（配置/数据不兼容） | major：`0.3.0 → 1.0.0` |
- 打出的安装包文件名必须体现新版本（`salt-shaker-<version>-1-x86_64.pkg.tar.zst`）。
- **禁止**用同一版本号覆盖已发布过的包。

## 2. GitHub 默认行为

- 默认只 `git commit` + `git push`。
- **不建 tag、不建 GitHub Release、不发版。**
- 除非用户在当轮明确说出「发版 / 发布 / release」，否则不得自行打 tag 或创建 Release。

## 3. 用户明确要求「发版」时

1. 确认：工作区干净、`./gradlew :app:test` 通过、版本号已按语义升级。
2. 构建**所有支持的 Linux 平台安装包**，至少包含：
   - `.deb`（Debian / Ubuntu）
   - `.rpm`（Fedora / openSUSE）
   - `AppImage`（通用）
   - `.tar.gz`（便携）
   - `.pkg.tar.zst`（Arch）
   - Flatpak（manifest 可用时）
3. 若某格式因环境缺依赖无法产出：**必须在报告中写明缺什么、如何补齐**，不得静默跳过，
   也不得用「部分格式」冒充「全部」。
4. 创建 tag 与 GitHub Release；Release notes 写明新增内容与**未验证边界**
   （例如真实音频设备、深色主题取色、桌面集成等），并把所有产物作为 assets 上传。
5. 上传后回读校验：Release URL、tag 指向的 commit、assets 数量与文件名、每个文件的 SHA-256。

## 4. 通用纪律

- 需要 `sudo` 的命令一律交给编排者执行，agent 不自行提权，不写 askpass 或密码文件。
- 不重启 / 不登出桌面会话，不 `pkill` 用户进程，不动其他窗口。
- 提交前 `./gradlew :app:compileKotlin` 与 `./gradlew :app:test` 必须通过。
- 安装包自检必须真实通过：0 重复条目、0 hardlink、0 `./` 前缀、解包后关键文件非空。
