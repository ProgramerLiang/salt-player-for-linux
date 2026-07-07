import { Play, Pause, SkipBack, SkipForward } from "lucide-react";
import { usePlayer } from "../hooks/usePlayer";
import { useEffect, useState } from "react";

export default function NowPlaying() {
  const { state, pause, resume, next, prev, refreshState } = usePlayer();
  const [coverUrl, setCoverUrl] = useState<string | null>(null);

  useEffect(() => {
    refreshState();
  }, []);

  useEffect(() => {
    if (state?.cover_art && state.cover_art.length > 0) {
      const bytes = new Uint8Array(state.cover_art);
      const blob = new Blob([bytes]);
      const url = URL.createObjectURL(blob);
      setCoverUrl(url);
      return () => URL.revokeObjectURL(url);
    } else {
      setCoverUrl(null);
    }
  }, [state?.cover_art]);

  const hasTrack = state.song_id != null;

  return (
    <section className="nowplaying" aria-label="正在播放">
      {coverUrl ? (
        <img src={coverUrl} alt={state.album} className="np-cover" />
      ) : (
        <div className="np-cover placeholder">♪</div>
      )}
      <div className="np-info">
        <h2>{state.title || "未在播放"}</h2>
        <p className="muted">{state.artist || ""}</p>
        <p className="muted" style={{ fontSize: 13 }}>{state.album || ""}</p>
      </div>
      <div className="np-controls">
        <button className="np-btn" onClick={prev} disabled={!hasTrack}>
          <SkipBack size={22} fill="currentColor" />
        </button>
        <button
          className="np-btn primary"
          onClick={state.is_playing ? pause : resume}
          disabled={!hasTrack}
        >
          {state.is_playing ? <Pause size={28} fill="currentColor" /> : <Play size={28} fill="currentColor" />}
        </button>
        <button className="np-btn" onClick={next} disabled={!hasTrack}>
          <SkipForward size={22} fill="currentColor" />
        </button>
      </div>
    </section>
  );
}