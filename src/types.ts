export interface Song {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration: number;
  file_path: string;
  cover_art: number[] | null;
  format: string;
}

export interface PlaybackState {
  song_id: string | null;
  title: string;
  artist: string;
  album: string;
  cover_art: number[] | null;
  duration: number;
  position: number;
  is_playing: boolean;
}

export interface AppConfig {
  music_dirs: string[];
  theme: string;
  language: string;
}

export type PlayMode = "sequential" | "shuffle" | "repeat";