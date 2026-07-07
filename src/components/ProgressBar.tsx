interface Props {
  position: number;
  duration: number;
}

function formatTime(s: number): string {
  if (!s || s < 0) return "0:00";
  const m = Math.floor(s / 60);
  const sec = Math.floor(s % 60);
  return `${m}:${sec.toString().padStart(2, "0")}`;
}

export default function ProgressBar({ position, duration }: Props) {
  const pct = duration > 0 ? Math.min((position / duration) * 100, 100) : 0;
  return (
    <div className="flex items-center gap-2 w-full max-w-[200px]">
      <span className="time-label">{formatTime(position)}</span>
      <div className="progress-track">
        <div className="progress-fill" style={{ width: `${pct}%` }} />
      </div>
      <span className="time-label">{formatTime(duration)}</span>
    </div>
  );
}