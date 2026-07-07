import { Song } from "../types";
import { Play } from "lucide-react";

interface Props {
  songs: Song[];
  currentId?: string | null;
  onPlay: (id: string) => void;
}

export default function SongList({ songs, currentId, onPlay }: Props) {
  if (songs.length === 0) {
    return <p className="muted">暂无歌曲，请先在设置中添加音乐目录并扫描。</p>;
  }
  return (
    <div className="song-table">
      {songs.map((song, i) => (
        <div
          key={song.id}
          className={`song-row${currentId === song.id ? " current" : ""}`}
          onDoubleClick={() => onPlay(song.id)}
        >
          <span className="song-index">{i + 1}</span>
          <div className="song-meta">
            <strong className={`song-title${currentId === song.id ? " active" : ""}`}>
              {song.title}
            </strong>
            <span className="song-sub">{song.artist} · {song.album}</span>
          </div>
          <span className="song-dur">{fmt(song.duration)}</span>
          <button
            className="play-btn"
            onClick={() => onPlay(song.id)}
            aria-label={`播放 ${song.title}`}
          >
            <Play size={14} fill="currentColor" />
          </button>
        </div>
      ))}
    </div>
  );
}

function fmt(s: number): string {
  const m = Math.floor(s / 60);
  const sec = Math.floor(s % 60);
  return `${m}:${sec.toString().padStart(2, "0")}`;
}