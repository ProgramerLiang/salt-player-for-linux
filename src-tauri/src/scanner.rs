use crate::models::Song;
use lofty::prelude::*;
use lofty::probe::Probe;
use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::path::Path;
use walkdir::WalkDir;

const SUPPORTED_EXTS: &[&str] = &["flac", "mp3", "aac", "ogg", "wav", "m4a", "opus"];

fn song_id(path: &str) -> String {
    let mut h = DefaultHasher::new();
    path.hash(&mut h);
    format!("{:x}", h.finish())
}

pub fn scan_directory(dir: &Path) -> Vec<Song> {
    let mut songs = Vec::new();
    let walker = WalkDir::new(dir).follow_links(true);
    for entry in walker.into_iter().filter_map(|e| e.ok()) {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let ext = path
            .extension()
            .and_then(|e| e.to_str())
            .unwrap_or("")
            .to_lowercase();
        if !SUPPORTED_EXTS.contains(&ext.as_str()) {
            continue;
        }

        let file_path = path.to_string_lossy().to_string();
        let id = song_id(&file_path);

        let tagged_file = match Probe::open(path).and_then(|f| f.read()) {
            Ok(t) => t,
            Err(_) => continue,
        };

        let tag = tagged_file
            .primary_tag()
            .or_else(|| tagged_file.first_tag());
        let tag = match tag {
            Some(t) => t,
            None => continue,
        };

        let dur = tagged_file.properties().duration().as_secs_f64();
        let title = tag
            .title()
            .map(|s| s.to_string())
            .unwrap_or_else(|| {
                path.file_stem()
                    .and_then(|s| s.to_str())
                    .unwrap_or("Unknown")
                    .to_string()
            });
        let artist = tag
            .artist()
            .map(|s| s.to_string())
            .unwrap_or_else(|| "Unknown Artist".to_string());
        let album = tag
            .album()
            .map(|s| s.to_string())
            .unwrap_or_else(|| "Unknown Album".to_string());
        let cover = tag.pictures().first().map(|p| p.data().to_vec());

        songs.push(Song {
            id,
            title,
            artist,
            album,
            duration: dur,
            file_path,
            cover_art: cover,
            format: ext,
        });
    }
    songs
}

pub fn save_songs_cache(songs: &[Song]) -> anyhow::Result<()> {
    let dir = crate::config::config_dir().join("cache");
    std::fs::create_dir_all(&dir)?;
    std::fs::write(dir.join("songs.json"), serde_json::to_string(songs)?)?;
    Ok(())
}

pub fn load_songs_cache() -> Vec<Song> {
    let path = crate::config::config_dir().join("cache").join("songs.json");
    if path.exists() {
        std::fs::read_to_string(&path)
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default()
    } else {
        Vec::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_scan_empty_dir() {
        let tmp = std::env::temp_dir().join("salt-scanner-test-empty");
        let _ = std::fs::create_dir_all(&tmp);
        assert!(scan_directory(&tmp).is_empty());
        let _ = std::fs::remove_dir_all(&tmp);
    }

    #[test]
    fn test_song_id_deterministic() {
        let p = "/music/a.mp3";
        assert_eq!(song_id(p), song_id(p));
    }

    #[test]
    fn test_cache_save_load() {
        let songs = vec![Song {
            id: "abc".into(),
            title: "Test".into(),
            artist: "A".into(),
            album: "B".into(),
            duration: 120.0,
            file_path: "/tmp/t.mp3".into(),
            cover_art: None,
            format: "mp3".into(),
        }];
        let cache_dir = std::env::temp_dir().join("salt-scanner-cache-test");
        std::env::set_var("XDG_CONFIG_HOME", &cache_dir);
        let _ = std::fs::create_dir_all(crate::config::config_dir());
        assert!(save_songs_cache(&songs).is_ok());
        assert!(!load_songs_cache().is_empty());
        let _ = std::fs::remove_dir_all(&cache_dir);
    }
}