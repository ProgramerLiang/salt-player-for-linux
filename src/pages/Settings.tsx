import { useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { useLibrary } from "../hooks/useLibrary";

export default function Settings() {
  const { scanDirectory, loading } = useLibrary();
  const [path, setPath] = useState("~/Music");

  const handleScan = async () => {
    const resolved = path.replace(/^~/, "/home");
    await scanDirectory(resolved);
  };

  const handleClear = async () => {
    await invoke("clear_library");
  };

  return (
    <section aria-labelledby="settings-title">
      <div className="page-header">
        <div>
          <p className="eyebrow">首选项</p>
          <h2 id="settings-title">设置</h2>
        </div>
      </div>

      <div className="settings-card">
        <label htmlFor="music-dir">音乐目录</label>
        <div className="input-row">
          <input
            id="music-dir"
            className="text-input"
            value={path}
            onChange={(e) => setPath(e.target.value)}
            placeholder="~/Music"
          />
          <button className="primary-button" onClick={handleScan} disabled={loading}>
            {loading ? "扫描中…" : "扫描"}
          </button>
        </div>
        <p className="muted" style={{ fontSize: 13, marginTop: 8 }}>
          输入完整路径或 ~ 开头的主目录路径。
        </p>
      </div>

      <div className="settings-card" style={{ marginTop: 24 }}>
        <button className="danger-button" onClick={handleClear}>
          清空音乐库
        </button>
        <p className="muted" style={{ fontSize: 13, marginTop: 6 }}>
          移除缓存的歌曲列表和播放队列。
        </p>
      </div>
    </section>
  );
}