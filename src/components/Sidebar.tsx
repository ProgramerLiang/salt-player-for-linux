import { NavLink } from "react-router-dom";
import { Library, Headphones, List, Settings } from "lucide-react";

const links = [
  { to: "/library", label: "音乐库", icon: Library },
  { to: "/playlist", label: "播放队列", icon: List },
  { to: "/nowplaying", label: "正在播放", icon: Headphones },
  { to: "/settings", label: "设置", icon: Settings },
];

export default function Sidebar() {
  return (
    <aside className="sidebar" aria-label="主导航">
      <div className="brand">
        <span className="brand-mark">S</span>
        <div>
          <h1>Salt Player</h1>
          <p>Linux Alpha</p>
        </div>
      </div>
      <nav>
        {links.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => `nav-button${isActive ? " active" : ""}`}
          >
            <Icon size={18} />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}