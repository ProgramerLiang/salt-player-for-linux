use crate::audio::AudioEngine;
use crate::config;
use crate::models::{AppConfig, PlayMode, PlaybackState, Song};
use crate::queue::PlayQueue;
use crate::scanner;
use std::sync::Mutex;

pub struct AppState {
    pub songs: Mutex<Vec<Song>>,
    pub queue: Mutex<PlayQueue>,
    pub config: Mutex<AppConfig>,
    pub audio: AudioEngine,
}

impl AppState {
    pub fn new() -> Self {
        let cfg = config::load_config();
        let cached = scanner::load_songs_cache();
        Self {
            songs: Mutex::new(cached),
            queue: Mutex::new(PlayQueue::new()),
            config: Mutex::new(cfg),
            audio: AudioEngine::new().expect("Failed to init audio engine"),
        }
    }
}

fn current_playback(audio: &AudioEngine, queue: &PlayQueue) -> PlaybackState {
    let st = audio.get_state();
    if let Some(song) = queue.current() {
        PlaybackState {
            song_id: Some(song.id.clone()),
            title: song.title.clone(),
            artist: song.artist.clone(),
            album: song.album.clone(),
            cover_art: song.cover_art.clone(),
            duration: song.duration,
            position: st.position.min(song.duration),
            is_playing: st.playing,
        }
    } else {
        PlaybackState {
            song_id: None,
            title: String::new(),
            artist: String::new(),
            album: String::new(),
            cover_art: None,
            duration: 0.0,
            position: 0.0,
            is_playing: false,
        }
    }
}

fn play_song_internal(audio: &AudioEngine, queue: &mut PlayQueue, songs: &[Song], song_id: &str) -> Result<PlaybackState, String> {
    let song = songs
        .iter()
        .find(|s| s.id == *song_id)
        .cloned()
        .ok_or_else(|| "歌曲未找到".to_string())?;
    let pos = songs.iter().position(|s| s.id == *song_id).unwrap_or(0);
    queue.set_queue(songs.to_vec(), pos);
    let _ = audio.load_file(&song.file_path)?;
    audio.play();
    Ok(current_playback(audio, queue))
}

#[tauri::command]
pub fn scan_library(state: tauri::State<AppState>, path: String) -> Result<Vec<Song>, String> {
    let dir = std::path::Path::new(&path);
    if !dir.is_dir() {
        return Err(format!("目录不存在: {}", path));
    }
    let songs = scanner::scan_directory(dir);
    let _ = scanner::save_songs_cache(&songs);
    if let Ok(mut s) = state.songs.lock() {
        *s = songs.clone();
    }
    Ok(songs)
}

#[tauri::command]
pub fn get_songs(state: tauri::State<AppState>) -> Result<Vec<Song>, String> {
    state.songs.lock().map(|s| s.clone()).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn play_song(state: tauri::State<AppState>, song_id: String) -> Result<PlaybackState, String> {
    let songs = state.songs.lock().map_err(|e| e.to_string())?;
    let mut queue = state.queue.lock().map_err(|e| e.to_string())?;
    play_song_internal(&state.audio, &mut queue, &songs, &song_id)
}

#[tauri::command]
pub fn pause(state: tauri::State<AppState>) -> Result<PlaybackState, String> {
    state.audio.pause();
    let q = state.queue.lock().map_err(|e| e.to_string())?;
    Ok(current_playback(&state.audio, &q))
}

#[tauri::command]
pub fn resume(state: tauri::State<AppState>) -> Result<PlaybackState, String> {
    state.audio.play();
    let q = state.queue.lock().map_err(|e| e.to_string())?;
    Ok(current_playback(&state.audio, &q))
}

#[tauri::command]
pub fn next_song(state: tauri::State<AppState>) -> Result<PlaybackState, String> {
    let mut queue = state.queue.lock().map_err(|e| e.to_string())?;
    queue.next();
    if let Some(song) = queue.current() {
        let _ = state.audio.load_file(&song.file_path);
        state.audio.play();
    }
    Ok(current_playback(&state.audio, &queue))
}

#[tauri::command]
pub fn prev_song(state: tauri::State<AppState>) -> Result<PlaybackState, String> {
    let mut queue = state.queue.lock().map_err(|e| e.to_string())?;
    queue.prev();
    if let Some(song) = queue.current() {
        let _ = state.audio.load_file(&song.file_path);
        state.audio.play();
    }
    Ok(current_playback(&state.audio, &queue))
}

#[tauri::command]
pub fn get_playback_state(state: tauri::State<AppState>) -> Result<PlaybackState, String> {
    let q = state.queue.lock().map_err(|e| e.to_string())?;
    Ok(current_playback(&state.audio, &q))
}

#[tauri::command]
pub fn set_play_mode(state: tauri::State<AppState>, mode: String) -> Result<(), String> {
    let pm = PlayMode::from_str(&mode).ok_or_else(|| format!("无效模式: {}", mode))?;
    state.queue.lock().map(|mut q| q.set_mode(pm)).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn get_config(state: tauri::State<AppState>) -> Result<AppConfig, String> {
    state.config.lock().map(|c| c.clone()).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn save_config(state: tauri::State<AppState>, config: AppConfig) -> Result<(), String> {
    config::save_config(&config).map_err(|e| e.to_string())?;
    if let Ok(mut c) = state.config.lock() {
        *c = config;
    }
    Ok(())
}

#[tauri::command]
pub fn clear_library(state: tauri::State<AppState>) -> Result<(), String> {
    if let Ok(mut songs) = state.songs.lock() {
        songs.clear();
    }
    if let Ok(mut q) = state.queue.lock() {
        q.set_queue(vec![], 0);
    }
    state.audio.stop();
    let cache = crate::config::config_dir().join("cache").join("songs.json");
    let _ = std::fs::remove_file(cache);
    Ok(())
}