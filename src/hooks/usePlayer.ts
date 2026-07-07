import { useState, useCallback, useEffect, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import type { PlaybackState, PlayMode } from "../types";

const EMPTY: PlaybackState = {
  song_id: null,
  title: "",
  artist: "",
  album: "",
  cover_art: null,
  duration: 0,
  position: 0,
  is_playing: false,
};

export function usePlayer() {
  const [state, setState] = useState<PlaybackState>(EMPTY);
  const [mode, setMode] = useState<PlayMode>("sequential");
  const timer = useRef<ReturnType<typeof setInterval>>();

  useEffect(() => {
    if (state.is_playing) {
      timer.current = setInterval(async () => {
        try {
          const s = await invoke<PlaybackState>("get_playback_state");
          setState(s);
        } catch {
          // ignore
        }
      }, 500);
    }
    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [state.is_playing]);

  const playSong = useCallback(async (songId: string) => {
    try {
      const s = await invoke<PlaybackState>("play_song", { songId });
      setState(s);
    } catch (e) {
      console.error("play error", e);
    }
  }, []);

  const pause = useCallback(async () => {
    try {
      const s = await invoke<PlaybackState>("pause");
      setState(s);
    } catch (e) {
      console.error(e);
    }
  }, []);

  const resume = useCallback(async () => {
    try {
      const s = await invoke<PlaybackState>("resume");
      setState(s);
    } catch (e) {
      console.error(e);
    }
  }, []);

  const next = useCallback(async () => {
    try {
      const s = await invoke<PlaybackState>("next_song");
      setState(s);
    } catch (e) {
      console.error(e);
    }
  }, []);

  const prev = useCallback(async () => {
    try {
      const s = await invoke<PlaybackState>("prev_song");
      setState(s);
    } catch (e) {
      console.error(e);
    }
  }, []);

  const setPlayMode = useCallback(async (m: PlayMode) => {
    try {
      await invoke("set_play_mode", { mode: m });
      setMode(m);
    } catch (e) {
      console.error(e);
    }
  }, []);

  const refreshState = useCallback(async () => {
    try {
      const s = await invoke<PlaybackState>("get_playback_state");
      setState(s);
    } catch {
      setState(EMPTY);
    }
  }, []);

  return { state, mode, playSong, pause, resume, next, prev, setPlayMode, refreshState };
}