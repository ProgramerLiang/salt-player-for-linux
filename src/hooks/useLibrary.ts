import { useState, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import type { Song } from "../types";

export function useLibrary() {
  const [songs, setSongs] = useState<Song[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const scanDirectory = useCallback(async (path: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await invoke<Song[]>("scan_library", { path });
      setSongs(result);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const result = await invoke<Song[]>("get_songs");
      setSongs(result);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  const clear = useCallback(async () => {
    await invoke("clear_library");
    setSongs([]);
  }, []);

  return { songs, loading, error, scanDirectory, refresh, clear };
}