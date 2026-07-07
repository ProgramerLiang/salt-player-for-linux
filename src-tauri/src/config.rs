use crate::models::AppConfig;
use std::path::PathBuf;

pub fn config_dir() -> PathBuf {
    dirs::config_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("salt-player")
}

fn config_path() -> PathBuf {
    config_dir().join("config.json")
}

pub fn load_config() -> AppConfig {
    let path = config_path();
    if path.exists() {
        std::fs::read_to_string(&path)
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default()
    } else {
        let config = AppConfig::default();
        let _ = save_config(&config);
        config
    }
}

pub fn save_config(config: &AppConfig) -> anyhow::Result<()> {
    let dir = config_dir();
    std::fs::create_dir_all(&dir)?;
    let json = serde_json::to_string_pretty(config)?;
    std::fs::write(config_path(), json)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = AppConfig::default();
        assert!(!config.music_dirs.is_empty());
        assert_eq!(config.theme, "dark");
        assert_eq!(config.language, "zh-CN");
    }

    #[test]
    fn test_save_load_roundtrip() {
        let dir = std::env::temp_dir().join("salt-test-config");
        std::env::set_var("XDG_CONFIG_HOME", &dir);
        let _ = std::fs::create_dir_all(config_dir());
        let config = AppConfig {
            music_dirs: vec!["/tmp/music".into()],
            theme: "light".into(),
            language: "en".into(),
        };
        assert!(save_config(&config).is_ok());
        let loaded = load_config();
        assert_eq!(loaded.music_dirs, vec!["/tmp/music"]);
        assert_eq!(loaded.theme, "light");
        assert_eq!(loaded.language, "en");
        let _ = std::fs::remove_dir_all(&dir);
    }
}