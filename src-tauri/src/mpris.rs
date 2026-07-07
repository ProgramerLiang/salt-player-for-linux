/// MPRIS integration stub for Linux desktop media controls.
/// Full implementation requires the `mpris-server` or `dbus` crates.
/// This module provides a placeholder that logs instead of registering
/// a DBus service, to keep the crate buildable without platform-specific
/// dependencies. Upgrade this when targeting actual Linux desktop releases.
pub struct MprisService;

impl MprisService {
    pub fn new() -> Self {
        log::info!("MPRIS service not yet active — requires mpris-server crate");
        Self
    }

    pub fn update_metadata(
        &self,
        _title: &str,
        _artist: &str,
        _album: &str,
        _duration: f64,
    ) {
        // TODO: emit org.mpris.MediaPlayer2.Player properties
    }

    pub fn update_playback_status(&self, _playing: bool, _position: f64) {
        // TODO: emit PlaybackStatus / Seeked
    }
}