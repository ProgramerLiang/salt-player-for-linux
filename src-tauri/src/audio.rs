use std::sync::mpsc::{self, Receiver, Sender};
use std::time::Instant;

enum AudioCommand {
    Load(String, Sender<Result<f64, String>>),
    Play,
    Pause,
    Stop,
    SetVolume(f32),
    GetState(Sender<AudioState>),
    #[allow(dead_code)]
    Quit,
}

#[derive(Debug, Clone, Default)]
pub struct AudioState {
    pub playing: bool,
    pub position: f64,
    pub duration: f64,
}

/// Send-only handle to a dedicated audio thread.
/// Implements Send + Sync so it can live in Tauri State.
#[derive(Clone)]
pub struct AudioEngine {
    tx: Sender<AudioCommand>,
}

impl AudioEngine {
    pub fn new() -> anyhow::Result<Self> {
        let (tx, rx) = mpsc::channel();
        std::thread::Builder::new()
            .name("salt-audio".into())
            .spawn(move || audio_loop(rx))?;
        Ok(Self { tx })
    }

    pub fn load_file(&self, path: &str) -> Result<f64, String> {
        let (tx, rx) = mpsc::channel();
        self.tx
            .send(AudioCommand::Load(path.to_owned(), tx))
            .map_err(|e| e.to_string())?;
        rx.recv().map_err(|e| e.to_string())?
    }

    pub fn play(&self) {
        let _ = self.tx.send(AudioCommand::Play);
    }

    pub fn pause(&self) {
        let _ = self.tx.send(AudioCommand::Pause);
    }

    pub fn stop(&self) {
        let _ = self.tx.send(AudioCommand::Stop);
    }

    pub fn set_volume(&self, vol: f32) {
        let _ = self.tx.send(AudioCommand::SetVolume(vol));
    }

    pub fn get_state(&self) -> AudioState {
        let (tx, rx) = mpsc::channel();
        let _ = self.tx.send(AudioCommand::GetState(tx));
        rx.recv().unwrap_or_default()
    }
}

fn audio_loop(rx: Receiver<AudioCommand>) {
    use rodio::{Decoder, OutputStream, Sink, Source};
    use std::fs::File;
    use std::io::BufReader;

    let (_stream, handle) = match OutputStream::try_default() {
        Ok(s) => s,
        Err(e) => {
            eprintln!("salt-audio: failed to open audio stream: {e}");
            for cmd in rx {
                if matches!(cmd, AudioCommand::Quit) {
                    break;
                }
            }
            return;
        }
    };

    let mut sink: Option<Sink> = None;
    let mut started_at: Option<Instant> = None;
    let mut paused_pos: f64 = 0.0;
    let mut duration: f64 = 0.0;

    for cmd in rx {
        match cmd {
            AudioCommand::Load(path, reply) => {
                if let Some(s) = sink.take() {
                    s.stop();
                }
                started_at = None;
                paused_pos = 0.0;

                let result = (|| -> Result<f64, String> {
                    let file = BufReader::new(File::open(&path).map_err(|e| e.to_string())?);
                    let source = Decoder::new(file).map_err(|e| e.to_string())?;
                    let dur = source
                        .total_duration()
                        .map(|d| d.as_secs_f64())
                        .unwrap_or(0.0);
                    let s = Sink::try_new(&handle).map_err(|e| e.to_string())?;
                    s.append(source);
                    s.pause();
                    duration = dur;
                    sink = Some(s);
                    Ok(dur)
                })();
                let _ = reply.send(result);
            }

            AudioCommand::Play => {
                if let Some(ref s) = sink {
                    s.play();
                    started_at = Some(Instant::now());
                }
            }

            AudioCommand::Pause => {
                if let Some(ref s) = sink {
                    paused_pos = calc_pos(started_at, paused_pos);
                    s.pause();
                    started_at = None;
                }
            }

            AudioCommand::Stop => {
                if let Some(s) = sink.take() {
                    s.stop();
                }
                started_at = None;
                paused_pos = 0.0;
                duration = 0.0;
            }

            AudioCommand::SetVolume(vol) => {
                if let Some(ref s) = sink {
                    s.set_volume(vol.clamp(0.0, 1.0));
                }
            }

            AudioCommand::GetState(reply) => {
                let playing = sink
                    .as_ref()
                    .map(|s| !s.is_paused() && s.len() > 0)
                    .unwrap_or(false);
                let pos = calc_pos(started_at, paused_pos);
                let _ = reply.send(AudioState {
                    playing,
                    position: if playing { pos.min(duration) } else { pos },
                    duration,
                });
            }

            AudioCommand::Quit => break,
        }
    }
}

fn calc_pos(started_at: Option<Instant>, paused: f64) -> f64 {
    if let Some(start) = started_at {
        start.elapsed().as_secs_f64()
    } else {
        paused
    }
}