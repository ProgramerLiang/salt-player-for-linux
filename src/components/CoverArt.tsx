import { Song } from "../types";

interface Props {
  song: Song | null;
  size?: number;
}

export default function CoverArt({ song, size = 200 }: Props) {
  if (song?.cover_art && song.cover_art.length > 0) {
    const bytes = new Uint8Array(song.cover_art);
    const blob = new Blob([bytes]);
    const url = URL.createObjectURL(blob);
    return (
      <img
        src={url}
        alt={song.album}
        className="cover"
        style={{ width: size, height: size }}
        onLoad={() => URL.revokeObjectURL(url)}
      />
    );
  }
  return (
    <div className="cover placeholder" style={{ width: size, height: size }}>
      ♪
    </div>
  );
}