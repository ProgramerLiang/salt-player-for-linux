import { Library, Music, Settings } from "lucide-react";

const navItems = [
  { icon: Library, label: "音乐库" },
  { icon: Music, label: "正在播放" },
  { icon: Settings, label: "设置" },
];

export default function App() {
  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="主导航">
        <div className="brand">
          <span className="brand-mark">S</span>
          <div>
            <h1>Salt Player</h1>
            <p>Linux Alpha</p>
          </div>
        </div>
        <nav>
          {navItems.map(({ icon: Icon, label }) => (
            <button key={label} className="nav-button" type="button">
              <Icon size={18} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <main className="workspace">
        <section className="library-panel" aria-labelledby="library-title">
          <div>
            <p className="eyebrow">Ubuntu 24.04 first</p>
            <h2 id="library-title">本地音乐库</h2>
            <p className="muted">下一步接入 Rust 扫描器、播放引擎和队列管理。</p>
          </div>
          <button className="primary-button" type="button" disabled>
            选择音乐目录
          </button>
        </section>

        <section className="empty-state" aria-label="空音乐库状态">
          <div className="disc" />
          <h3>准备接入本地音乐</h3>
          <p>脚手架已建立，明天从 Rust models/config/scanner 继续。</p>
        </section>
      </main>

      <footer className="player-bar" aria-label="播放控制">
        <div>
          <strong>未在播放</strong>
          <span>等待音乐库扫描</span>
        </div>
        <div className="transport">
          <button type="button" disabled>上一首</button>
          <button type="button" disabled>播放</button>
          <button type="button" disabled>下一首</button>
        </div>
      </footer>
    </div>
  );
}
