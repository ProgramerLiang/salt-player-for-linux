import { HashRouter, Routes, Route } from "react-router-dom";
import Layout from "./components/Layout";
import Library from "./pages/Library";
import Playlist from "./pages/Playlist";
import NowPlaying from "./pages/NowPlaying";
import Settings from "./pages/Settings";

export default function App() {
  return (
    <HashRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Library />} />
          <Route path="/library" element={<Library />} />
          <Route path="/playlist" element={<Playlist />} />
          <Route path="/nowplaying" element={<NowPlaying />} />
          <Route path="/settings" element={<Settings />} />
        </Route>
      </Routes>
    </HashRouter>
  );
}