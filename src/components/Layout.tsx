import { Outlet } from "react-router-dom";
import Sidebar from "./Sidebar";
import PlayerBar from "./PlayerBar";

export default function Layout() {
  return (
    <div className="app-shell">
      <Sidebar />
      <main className="workspace">
        <Outlet />
      </main>
      <PlayerBar />
    </div>
  );
}