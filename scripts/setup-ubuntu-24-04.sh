#!/usr/bin/env bash
set -euo pipefail

echo "=== Salt Player for Linux — Ubuntu 24.04 环境准备 ==="

# 1️⃣ 系统依赖（需要 sudo）
echo ""
echo "⚙️ 安装 Tauri v2 系统依赖..."
apt-get update -qq
apt-get install -y -qq \
  libwebkit2gtk-4.1-dev \
  libayatana-appindicator3-dev \
  librsvg2-dev \
  patchelf \
  libasound2-dev \
  libssl-dev \
  libsoup-3.0-dev \
  libjavascriptcoregtk-4.1-dev \
  curl \
  build-essential \
  pkg-config

echo "✅ 系统依赖安装完成"
echo ""

# 2️⃣ Rust 工具链（当前用户，非 root）
if command -v rustc &>/dev/null; then
  echo "✅ Rust 已安装: $(rustc --version)"
else
  echo "📦 安装 Rust 工具链..."
  sudo -u "$SUDO_USER" bash -c 'curl --proto =https --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y'
  echo "✅ Rust 安装完成"
fi

# 3️⃣ Cargo 加入 PATH
echo ""
echo "🔧 确保 Cargo 在 PATH 中..."
CARGO_HOME="$(eval echo ~$SUDO_USER)/.cargo"
if [ -f "$CARGO_HOME/env" ]; then
  source "$CARGO_HOME/env"
fi

echo ""
echo "=== 环境准备完毕 ==="
echo ""
echo "接下来以普通用户执行："
echo ""
echo "  cd ~/Desktop/salt-player-for-linux"
echo "  npm run build             # 构建前端"
echo "  cd src-tauri && cargo check   # 检查 Rust"
echo "  cd .. && npm run tauri build  # 构建最终 .deb + AppImage"
echo ""