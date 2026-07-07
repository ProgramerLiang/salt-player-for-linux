import { useLibrary } from "../hooks/useLibrary";
import { usePlayer } from "../hooks/usePlayer";
import SongList from "../components/SongList";
import { useEffect, useState } from "react";

export default function Playlist() {
  const { songs, loading, refresh } = useLibrary();
  const { state, playSong } = usePlayer();
  const [mode, setMode] = useState("sequential");
  const { setPlayMode } = usePlayer();

  useEffect(() => {
    refresh();
  }, []);

  const modes = [
    { value: "sequential", label: "顺序" },
    { value: "shuffle", label: "随机" },
    { value: "repeat", label: "单曲" },
  ] as const;

  return (
    <section aria-labelledby="queue-title">
      <div className="page-header">
        <div>
          <p className="eyebrow">当前队列</p>
          <h2 id="queue-title">播放队列</h2>
        </div>
        <div className="mode-group">
          {modes.map((m) => (
            <button
              key={m.value}
              className={`mode-btn${mode === m.value ? " active" : ""}`}
              onClick={() => {
                setMode(m.value);
                setPlayMode(m.value);
              }}
            >
              {m.label}
            </button>
          ))}
        </div>
      </div>
      {loading ? (
        <p className="muted">加载中…</p>
      ) : (
        <SongList
          songs={songs}
          currentId={state.song_id}
          onPlay={playSong}
        />
      )}
    </section>
  );
}