import { useState, type ReactNode } from 'react';

interface SectionProps {
  title: string;
  count?: number;
  color?: string;
  defaultOpen?: boolean;
  children: ReactNode;
}

export function Section({ title, count, color, defaultOpen = true, children }: SectionProps) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className="section">
      <button className="section-header" onClick={() => setOpen(!open)}>
        <div className="section-title-row">
          <span className="section-title" style={color ? { color } : undefined}>
            {title}
          </span>
          {count !== undefined && (
            <span className="section-count">{count}</span>
          )}
        </div>
        <svg
          className={`chevron ${open ? 'open' : ''}`}
          width="20" height="20" viewBox="0 0 24 24"
          fill="none" stroke="currentColor" strokeWidth="2"
        >
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>
      {open && <div className="section-content">{children}</div>}
    </div>
  );
}
