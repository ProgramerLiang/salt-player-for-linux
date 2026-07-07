use crate::models::{PlayMode, Song};
use rand::seq::SliceRandom;
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
pub struct QueueInfo {
    pub songs: Vec<Song>,
    pub index: usize,
    pub mode: String,
}

pub struct PlayQueue {
    queue: Vec<Song>,
    original: Vec<Song>,
    pub index: usize,
    pub mode: PlayMode,
}

impl PlayQueue {
    pub fn new() -> Self {
        Self {
            queue: Vec::new(),
            original: Vec::new(),
            index: 0,
            mode: PlayMode::Sequential,
        }
    }

    pub fn current(&self) -> Option<&Song> {
        self.queue.get(self.index)
    }

    pub fn set_queue(&mut self, songs: Vec<Song>, start_index: usize) {
        self.original = songs.clone();
        self.queue = songs;
        self.index = start_index.min(self.queue.len().saturating_sub(1));
        if self.mode == PlayMode::Shuffle {
            self.shuffle(false);
        }
    }

    pub fn next(&mut self) -> Option<&Song> {
        if self.queue.is_empty() {
            return None;
        }
        if self.mode != PlayMode::Repeat {
            self.index = (self.index + 1) % self.queue.len();
        }
        self.current()
    }

    pub fn prev(&mut self) -> Option<&Song> {
        if self.queue.is_empty() {
            return None;
        }
        if self.mode != PlayMode::Repeat {
            self.index = if self.index == 0 {
                self.queue.len() - 1
            } else {
                self.index - 1
            };
        }
        self.current()
    }

    pub fn set_mode(&mut self, mode: PlayMode) {
        self.mode = mode.clone();
        match mode {
            PlayMode::Shuffle => self.shuffle(true),
            _ => self.restore_order(),
        }
    }

    fn shuffle(&mut self, keep_current: bool) {
        if self.queue.is_empty() {
            return;
        }
        let current_id = if keep_current {
            self.current().map(|s| s.id.clone())
        } else {
            None
        };
        let mut rng = rand::thread_rng();
        self.queue.shuffle(&mut rng);
        if let Some(ref id) = current_id {
            self.index = self
                .queue
                .iter()
                .position(|s| s.id == *id)
                .unwrap_or(0);
        }
    }

    fn restore_order(&mut self) {
        if let Some(current) = self.current() {
            let current_id = current.id.clone();
            self.queue = self.original.clone();
            self.index = self
                .queue
                .iter()
                .position(|s| s.id == current_id)
                .unwrap_or(0);
        } else {
            self.queue = self.original.clone();
            self.index = 0;
        }
    }

    pub fn info(&self) -> QueueInfo {
        QueueInfo {
            songs: self.queue.clone(),
            index: self.index,
            mode: self.mode.as_str().to_string(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::Song;

    fn s(id: &str) -> Song {
        Song {
            id: id.into(),
            title: id.into(),
            artist: "T".into(),
            album: "T".into(),
            duration: 100.0,
            file_path: format!("/m/{}.mp3", id),
            cover_art: None,
            format: "mp3".into(),
        }
    }

    #[test]
    fn test_sequential() {
        let mut q = PlayQueue::new();
        q.set_queue(vec![s("a"), s("b"), s("c")], 0);
        assert_eq!(q.current().unwrap().id, "a");
        q.next();
        assert_eq!(q.current().unwrap().id, "b");
        q.next();
        assert_eq!(q.current().unwrap().id, "c");
        q.next();
        assert_eq!(q.current().unwrap().id, "a");
    }

    #[test]
    fn test_prev_wraps() {
        let mut q = PlayQueue::new();
        q.set_queue(vec![s("a"), s("b")], 0);
        q.prev();
        assert_eq!(q.current().unwrap().id, "b");
    }

    #[test]
    fn test_repeat_keeps_same() {
        let mut q = PlayQueue::new();
        q.set_queue(vec![s("x")], 0);
        q.set_mode(PlayMode::Repeat);
        q.next();
        assert_eq!(q.current().unwrap().id, "x");
        q.prev();
        assert_eq!(q.current().unwrap().id, "x");
    }
}