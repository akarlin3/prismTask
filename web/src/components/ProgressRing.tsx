import { useEffect, useRef } from 'react';

interface ProgressRingProps {
  completed: number;
  total: number;
  size?: number;
  strokeWidth?: number;
}

export function ProgressRing({ completed, total, size = 120, strokeWidth = 8 }: ProgressRingProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const progress = total > 0 ? completed / total : 0;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    ctx.scale(dpr, dpr);

    const cx = size / 2;
    const cy = size / 2;

    // Background circle
    ctx.beginPath();
    ctx.arc(cx, cy, radius, 0, 2 * Math.PI);
    ctx.strokeStyle = 'rgba(255,255,255,0.1)';
    ctx.lineWidth = strokeWidth;
    ctx.stroke();

    // Progress arc
    if (progress > 0) {
      ctx.beginPath();
      ctx.arc(cx, cy, radius, -Math.PI / 2, -Math.PI / 2 + 2 * Math.PI * progress);
      ctx.strokeStyle = progress >= 1 ? '#4CAF50' : 'var(--accent, #4A90D9)';
      ctx.lineWidth = strokeWidth;
      ctx.lineCap = 'round';
      ctx.stroke();
    }
  }, [completed, total, size, strokeWidth, radius, progress, circumference]);

  return (
    <div className="progress-ring" style={{ position: 'relative', width: size, height: size }}>
      <canvas
        ref={canvasRef}
        style={{ width: size, height: size }}
      />
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
        display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      }}>
        <span style={{ fontSize: size * 0.25, fontWeight: 700, color: '#fff' }}>
          {completed}/{total}
        </span>
        <span style={{ fontSize: size * 0.1, color: 'rgba(255,255,255,0.6)', marginTop: 2 }}>
          {progress >= 1 ? 'All done!' : 'today'}
        </span>
      </div>
    </div>
  );
}
