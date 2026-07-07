pub mod audio;
pub mod commands;
pub mod config;
pub mod models;
pub mod mpris;
pub mod queue;
pub mod scanner;

use commands::AppState;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    env_logger::init();

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .manage(AppState::new())
        .invoke_handler(tauri::generate_handler![
            commands::scan_library,
            commands::get_songs,
            commands::play_song,
            commands::pause,
            commands::resume,
            commands::next_song,
            commands::prev_song,
            commands::get_playback_state,
            commands::set_play_mode,
            commands::get_config,
            commands::save_config,
            commands::clear_library,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Salt Player");
}