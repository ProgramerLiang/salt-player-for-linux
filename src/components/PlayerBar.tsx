import { Play, Pause, SkipBack, SkipForward } from "lucide-react";
import { usePlayer } from "../hooks/usePlayer";
import ProgressBar from "./ProgressBar";

function formatTime(s: number): string {
  if (!s || s < 0) return "0:00";
  const m = Math.floor(s / 60);
  const sec = Math.floor(s % 60);
  return `${m}:${sec.toString().padStart(2, "0")}`;
}

export default function PlayerBar() {
  const { state, pause, resume, next, prev } = usePlayer();

  return (
    <footer className="player-bar" aria-label="播放控制">
      <div className="track-info">
        <strong>{state.title || "未在播放"}</strong>
        <span>{state.artist || state.album || "等待播放"}</span>
      </div>

      <div className="transport-group">
        <button className="transport-btn" onClick={prev} aria-label="上一首">
          <SkipBack size={18} fill="currentColor" />
        </button>
        <button
          className="transport-btn primary"
          onClick={state.is_playing ? pause : resume}
          aria-label={state.is_playing ? "暂停" : "播放"}
        >
          {state.is_playing ? (
            <Pause size={20} fill="currentColor" />
          ) : (
            <Play size={20} fill="currentColor" />
          )}
        </button>
        <button className="transport-btn" onClick={next} aria-label="下一首">
          <SkipForward size={18} fill="currentColor" />
        </button>
      </div>

      <div className="progress-area">
        <ProgressBar position={state.position} duration={state.duration} />
      </div>
    </footer>
  );
}