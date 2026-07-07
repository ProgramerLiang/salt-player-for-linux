use rodio::{Decoder, OutputStream, Sink, Source};
use std::fs::File;
use std::io::BufReader;
use std::time::Instant;

pub struct AudioEngine {
    _stream: OutputStream,
    sink: Option<Sink>,
    pub current_duration: f64,
    started_at: Option<Instant>,
    paused_position: f64,
}

impl AudioEngine {
    pub fn new() -> anyhow::Result<Self> {
        let (stream, _) = OutputStream::try_default()?;
        Ok(Self {
            _stream: stream,
            sink: None,
            current_duration: 0.0,
            started_at: None,
            paused_position: 0.0,
        })
    }

    pub fn load_file(&mut self, path: &str) -> anyhow::Result<f64> {
        self.stop();
        let file = BufReader::new(File::open(path)?);
        let source = Decoder::new(file)?;
        let dur = source
            .total_duration()
            .map(|d| d.as_secs_f64())
            .unwrap_or(0.0);
        let sink = Sink::try_new(&self._stream)?;
        sink.append(source);
        sink.pause();
        self.sink = Some(sink);
        self.current_duration = dur;
        self.started_at = None;
        self.paused_position = 0.0;
        Ok(dur)
    }

    pub fn play(&mut self) {
        if let Some(ref sink) = self.sink {
            sink.play();
            self.started_at = Some(Instant::now());
        }
    }

    pub fn pause(&mut self) {
        if let Some(ref sink) = self.sink {
            self.paused_position = self.position();
            sink.pause();
            self.started_at = None;
        }
    }

    pub fn stop(&mut self) {
        if let Some(sink) = self.sink.take() {
            sink.stop();
        }
        self.started_at = None;
        self.paused_position = 0.0;
        self.current_duration = 0.0;
    }

    pub fn is_playing(&self) -> bool {
        self.sink
            .as_ref()
            .map(|s| !s.is_paused() && s.len() > 0)
            .unwrap_or(false)
    }

    pub fn position(&self) -> f64 {
        if let Some(started) = self.started_at {
            (Instant::now() - started).as_secs_f64()
        } else if self.sink.is_some() {
            self.paused_position
        } else {
            0.0
        }
    }

    pub fn set_volume(&self, vol: f32) {
        if let Some(ref sink) = self.sink {
            sink.set_volume(vol.clamp(0.0, 1.0));
        }
    }

    pub fn has_sink(&self) -> bool {
        self.sink.is_some()
    }
}