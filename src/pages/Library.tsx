import { useEffect } from "react";
import { useLibrary } from "../hooks/useLibrary";
import { usePlayer } from "../hooks/usePlayer";
import SongList from "../components/SongList";

export default function Library() {
  const { songs, loading, error, refresh } = useLibrary();
  const { state, playSong } = usePlayer();

  useEffect(() => {
    refresh();
  }, []);

  return (
    <section aria-labelledby="library-title">
      <div className="page-header">
        <div>
          <p className="eyebrow">Ubuntu 24.04</p>
          <h2 id="library-title">音乐库</h2>
        </div>
        <button className="primary-button" onClick={refresh} disabled={loading}>
          {loading ? "刷新中…" : "刷新"}
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      <SongList
        songs={songs}
        currentId={state.song_id}
        onPlay={playSong}
      />
    </section>
  );
}