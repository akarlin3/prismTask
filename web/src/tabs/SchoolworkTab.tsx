import { useState, useEffect } from 'react';

// ── CSCA 5424 Data ──

interface Item5424 {
  id: string;
  date: string;
  label: string;
  off?: boolean;
  time?: string;
  done?: boolean;
  buffer?: boolean;
}

interface Phase5424 {
  phase: string;
  subtitle: string;
  items: Item5424[];
}

const CSCA5424_TASKS: Phase5424[] = [
  {
    phase: "Phase 1: Videos & Assignments",
    subtitle: "Apr 1–5 · Alongside comp prep",
    items: [
      { id: "p1", date: "Apr 1", label: "Passover — OFF", off: true },
      { id: "p2", date: "Apr 2", label: "Passover — OFF", off: true },
      { id: "v1", date: "Apr 3", label: "Watch: FPTAS & Knapsack (29 min)", time: "30 min", done: true },
      { id: "a1", date: "Apr 3", label: "Graded Assignment: FPTAS", time: "30 min", done: true },
      { id: "v2", date: "Apr 4", label: "Watch: Eulerian Walks (12 min)", time: "15 min", done: true },
      { id: "a2", date: "Apr 4", label: "Graded Assignment: Metric TSPs", time: "30 min", done: true },
      { id: "v3", date: "Apr 5", label: "Watch: Christofides Algorithm (28 min)", time: "30 min" },
      { id: "b1", date: "Apr 6", label: "OFF", off: true },
      { id: "b2", date: "Apr 7", label: "OFF", off: true },
    ],
  },
  {
    phase: "Phase 2: Programming + Final Exam",
    subtitle: "Apr 8–15 · 3 hr/day max",
    items: [
      { id: "hc", date: "Apr 8", label: "Honor Code Verification", time: "1 min" },
      { id: "tsp", date: "Apr 8", label: "TSP Programming Assignment", time: "3 hr" },
      { id: "off3", date: "Apr 9", label: "OFF", off: true },
      { id: "fe1", date: "Apr 10", label: "Final Exam: finish TSP if needed, read specs, plan approach, begin coding", time: "3 hr" },
      { id: "fe2", date: "Apr 11", label: "Final Exam: core implementation", time: "3 hr" },
      { id: "fe3", date: "Apr 12", label: "Final Exam: core implementation continued", time: "3 hr" },
      { id: "fe4", date: "Apr 13", label: "Final Exam: testing, debugging, polish + submit", time: "2–3 hr" },
      { id: "buf", date: "Apr 14", label: "BUFFER — overflow if needed", time: "0–3 hr", buffer: true },
      { id: "buf2", date: "Apr 15", label: "BUFFER", time: "", buffer: true },
    ],
  },
];

// ── CSCA 5454 Data ──

interface Task5454 {
  id: string;
  text: string;
  time: string;
  type: 'video' | 'assignment' | 'code';
  done?: boolean;
}

interface Day5454 {
  date: string;
  label?: string;
  off?: boolean;
  exam?: boolean;
  buffer?: boolean;
  deadline?: boolean;
  day?: number;
  phase?: number;
  category?: string;
  tasks?: Task5454[];
}

const CSCA5454_SCHEDULE: Day5454[] = [
  { date: "Apr 1", label: "PASSOVER SEDER 1", off: true },
  { date: "Apr 2", label: "PASSOVER SEDER 2", off: true },
  { date: "Apr 3", day: 1, phase: 1, category: "quantum", tasks: [
    { id: "w2-mqsv", text: "Multi Qubit Quantum States video", time: "29 min", type: "video" },
    { id: "w2-mqsa", text: "Multiple Qubit Quantum States — assignment", time: "2 hrs", type: "assignment" },
  ]},
  { date: "Apr 4", day: 2, phase: 1, category: "rsa", tasks: [
    { id: "w1-eebv", text: "Extended Euclid Bezout Coefficients video", time: "18 min", type: "video" },
    { id: "w1-bca", text: "Bezout Coefficients — assignment", time: "20 min", type: "assignment" },
  ]},
  { date: "Apr 5", day: 3, phase: 1, category: "rsa", tasks: [
    { id: "w1-rsav", text: "RSA Cryptography video", time: "43 min", type: "video" },
  ]},
  { date: "Apr 6", label: "Rest day — comp exam prep", off: true },
  { date: "Apr 7", label: "MED PHYSICS COMP — Nuclear Science + Radiobiology", off: true, exam: true },
  { date: "Apr 8", day: 4, phase: 1, category: "rsa", tasks: [
    { id: "w1-rsaq", text: "Quiz on RSA — assignment", time: "50 min", type: "assignment" },
  ]},
  { date: "Apr 9", label: "Rest day — comp exam prep", off: true },
  { date: "Apr 10", label: "MED PHYSICS COMP — Radiation Therapy", off: true, exam: true },
  { date: "Apr 11", day: 5, phase: 2, category: "rsa", tasks: [
    { id: "w1-pa1", text: "Week 1 Programming Assignment — Part 1", time: "2 hrs", type: "code" },
  ]},
  { date: "Apr 12", day: 6, phase: 2, category: "rsa", tasks: [
    { id: "w1-pa2", text: "Week 1 Programming Assignment — finish", time: "~1 hr", type: "code" },
    { id: "w1-eagv", text: "Euclid's Algorithm & GCD video", time: "23 min", type: "video" },
    { id: "w1-gcda", text: "GCD and Euclid's Algorithm — assignment", time: "30 min", type: "assignment" },
  ]},
  { date: "Apr 13", day: 7, phase: 2, category: "quantum", tasks: [
    { id: "w2-qpcv", text: "Quantum Physics Computing Basics video", time: "41 min", type: "video" },
    { id: "w2-qpv", text: "Quantum Parallelism video", time: "14 min", type: "video" },
    { id: "w2-ncv", text: "No Cloning Theorem video", time: "5 min", type: "video" },
    { id: "w2-mqgv", text: "Multi Qubit Quantum Gates video", time: "28 min", type: "video" },
  ]},
  { date: "Apr 14", day: 8, phase: 2, category: "quantum", tasks: [
    { id: "w2-mqga", text: "Multi Qubit Quantum Gates — assignment", time: "40 min", type: "assignment" },
    { id: "w2-biv", text: "Bell's Inequality video", time: "21 min", type: "video" },
    { id: "w2-gsv", text: "Grover Search Algorithm video", time: "42 min", type: "video" },
  ]},
  { date: "Apr 15", day: 9, phase: 2, category: "quantum", tasks: [
    { id: "w2-qpca", text: "Quantum Parallelism & Classical Gates — assignment", time: "45 min", type: "assignment" },
    { id: "w3-ofv", text: "Order Finding and Factoring video", time: "23 min", type: "video" },
    { id: "w3-ofqv", text: "Order Finding on a Quantum Computer video", time: "26 min", type: "video" },
  ]},
  { date: "Apr 16", day: 10, phase: 2, category: "quantum", tasks: [
    { id: "w3-ofa1", text: "Order Finding & Factoring — assignment (continue)", time: "~2 hrs", type: "assignment" },
  ]},
  { date: "Apr 17", day: 11, phase: 2, category: "quantum", tasks: [
    { id: "w3-ofa2", text: "Order Finding & Factoring — assignment (finish)", time: "~30 min", type: "assignment" },
    { id: "w3-qft1v", text: "QFT Part 1 video", time: "18 min", type: "video" },
    { id: "w3-qft2v", text: "QFT Part 2 video", time: "28 min", type: "video" },
    { id: "w3-qfta", text: "Quantum Fourier Transform — assignment", time: "1 hr", type: "assignment" },
  ]},
  { date: "Apr 18", day: 12, phase: 2, category: "quantum", tasks: [
    { id: "w3-pa1", text: "Week 3 Programming Assignment — Part 1", time: "2 hrs", type: "code" },
  ]},
  { date: "Apr 19", day: 13, phase: 2, category: "ds", tasks: [
    { id: "w3-pa2", text: "Week 3 Programming Assignment — finish", time: "~1 hr", type: "code" },
    { id: "w4-tbv", text: "Tries: Basics video", time: "39 min", type: "video" },
  ]},
  { date: "Apr 20", day: 14, phase: 2, category: "ds", tasks: [
    { id: "w4-stv", text: "Suffix Tries video", time: "12 min", type: "video" },
    { id: "w4-gstv", text: "Generalized Suffix Tries video", time: "33 min", type: "video" },
    { id: "w4-uk1v", text: "Ukkonen's Algorithm Part 1", time: "24 min", type: "video" },
    { id: "w4-uk2v", text: "Ukkonen's Algorithm Part 2", time: "25 min", type: "video" },
  ]},
  { date: "Apr 21", day: 15, phase: 2, category: "ds", tasks: [
    { id: "w4-pa", text: "Week 4 Programming Assignment — finish", time: "~2 hrs", type: "code" },
  ]},
  { date: "Apr 22", day: 16, phase: 2, category: "final", tasks: [
    { id: "fe-1", text: "Final Exam — Session 1", time: "2 hrs", type: "code" },
  ]},
  { date: "Apr 23", day: 17, phase: 2, category: "final", tasks: [
    { id: "fe-2", text: "Final Exam — Session 2", time: "2 hrs", type: "code" },
  ]},
  { date: "Apr 24", day: 18, phase: 2, category: "final", tasks: [
    { id: "fe-3", text: "Final Exam — Session 3", time: "2 hrs", type: "code" },
  ]},
  { date: "Apr 25", day: 19, phase: 2, category: "final", tasks: [
    { id: "fe-4", text: "Final Exam — Session 4", time: "2 hrs", type: "code" },
  ]},
  { date: "Apr 26", day: 20, phase: 2, category: "final", tasks: [
    { id: "fe-5", text: "Final Exam — Session 5 (if needed)", time: "2 hrs", type: "code" },
  ]},
  { date: "Apr 27", label: "Buffer / catch-up", off: true, buffer: true },
  { date: "Apr 28", label: "Buffer / catch-up", off: true, buffer: true },
  { date: "Apr 29", label: "Buffer / catch-up", off: true, buffer: true },
  { date: "Apr 30", label: "DEADLINE — Final submissions", off: true, deadline: true },
];

const CAT_COLORS: Record<string, { accent: string; label: string }> = {
  rsa: { accent: "#3b82f6", label: "RSA" },
  quantum: { accent: "#8b5cf6", label: "Quantum" },
  ds: { accent: "#22c55e", label: "Data Structures" },
  final: { accent: "#f97316", label: "Final Exam" },
};

const TYPE_ICONS: Record<string, string> = {
  video: "▶",
  assignment: "✎",
  code: "⟨/⟩",
};

// ── Storage helpers ──

const STORAGE_5424 = "csca5424-checklist";
const STORAGE_5454 = "csca5454-checked";

function loadChecked(key: string, defaults: Record<string, boolean> = {}): Record<string, boolean> {
  try {
    const raw = localStorage.getItem(key);
    if (raw) return { ...defaults, ...JSON.parse(raw) };
  } catch { /* ignore */ }
  return defaults;
}

function saveChecked(key: string, data: Record<string, boolean>) {
  try {
    localStorage.setItem(key, JSON.stringify(data));
  } catch { /* ignore */ }
}

// ── Main Component ──

type CourseView = '5424' | '5454';

export function SchoolworkTab() {
  const [view, setView] = useState<CourseView>('5454');

  // 5424 state
  const [checked5424, setChecked5424] = useState<Record<string, boolean>>(() => {
    const defaults: Record<string, boolean> = {};
    CSCA5424_TASKS.forEach(phase => {
      phase.items.forEach(item => {
        if (item.done) defaults[item.id] = true;
      });
    });
    return loadChecked(STORAGE_5424, defaults);
  });

  // 5454 state
  const [checked5454, setChecked5454] = useState<Record<string, boolean>>(() => {
    const defaults: Record<string, boolean> = {};
    CSCA5454_SCHEDULE.forEach(day => {
      day.tasks?.forEach(task => {
        if (task.done) defaults[task.id] = true;
      });
    });
    return loadChecked(STORAGE_5454, defaults);
  });

  useEffect(() => { saveChecked(STORAGE_5424, checked5424); }, [checked5424]);
  useEffect(() => { saveChecked(STORAGE_5454, checked5454); }, [checked5454]);

  const toggle5424 = (id: string) => {
    setChecked5424(prev => ({ ...prev, [id]: !prev[id] }));
  };

  const toggle5454 = (id: string) => {
    setChecked5454(prev => ({ ...prev, [id]: !prev[id] }));
  };

  // Progress calculations
  const total5424 = CSCA5424_TASKS.flatMap(p => p.items).filter(i => !i.off && !i.buffer).length;
  const done5424 = CSCA5424_TASKS.flatMap(p => p.items).filter(i => !i.off && !i.buffer && checked5424[i.id]).length;
  const pct5424 = total5424 > 0 ? Math.round((done5424 / total5424) * 100) : 0;

  const total5454 = CSCA5454_SCHEDULE.reduce((sum, d) => sum + (d.tasks ? d.tasks.length : 0), 0);
  const done5454 = CSCA5454_SCHEDULE.reduce((sum, d) => {
    if (!d.tasks) return sum;
    return sum + d.tasks.filter(t => checked5454[t.id]).length;
  }, 0);
  const pct5454 = total5454 > 0 ? Math.round((done5454 / total5454) * 100) : 0;

  const overallTotal = total5424 + total5454;
  const overallDone = done5424 + done5454;
  const overallPct = overallTotal > 0 ? Math.round((overallDone / overallTotal) * 100) : 0;

  const today = new Date();
  const todayStr = `Apr ${today.getDate()}`;

  return (
    <div className="tab-content schoolwork-tab">
      {/* Header */}
      <div className="sw-header">
        <span className="sw-label">UC Boulder</span>
        <h2 className="sw-title">Schoolwork</h2>
        <span className="sw-subtitle">Spring 2026 · Deadline: April 30</span>
      </div>

      {/* Overall progress */}
      <div className="sw-progress-card">
        <div className="sw-progress-row">
          <span className="sw-progress-text">{overallDone}/{overallTotal} tasks</span>
          <span className="sw-progress-pct">{overallPct}%</span>
        </div>
        <div className="sw-progress-bar">
          <div
            className={`sw-progress-fill ${overallPct === 100 ? 'complete' : ''}`}
            style={{ width: `${overallPct}%` }}
          />
        </div>
      </div>

      {/* Course switcher */}
      <div className="sw-switcher">
        <button
          className={`sw-switch-btn ${view === '5424' ? 'active' : ''}`}
          onClick={() => setView('5424')}
        >
          <span className="sw-switch-code">5424</span>
          <span className="sw-switch-name">Approx Algorithms</span>
          <span className="sw-switch-pct">{pct5424}%</span>
        </button>
        <button
          className={`sw-switch-btn ${view === '5454' ? 'active' : ''}`}
          onClick={() => setView('5454')}
        >
          <span className="sw-switch-code">5454</span>
          <span className="sw-switch-name">DS, RSA & Quantum</span>
          <span className="sw-switch-pct">{pct5454}%</span>
        </button>
      </div>

      {/* Course content */}
      {view === '5424' ? (
        <Course5424 checked={checked5424} toggle={toggle5424} done={done5424} total={total5424} pct={pct5424} todayStr={todayStr} />
      ) : (
        <Course5454 checked={checked5454} toggle={toggle5454} done={done5454} total={total5454} pct={pct5454} todayStr={todayStr} />
      )}
    </div>
  );
}

// ── CSCA 5424 Course View ──

function Course5424({ checked, toggle, done, total, pct, todayStr }: {
  checked: Record<string, boolean>;
  toggle: (id: string) => void;
  done: number;
  total: number;
  pct: number;
  todayStr: string;
}) {
  return (
    <div className="sw-course">
      <div className="sw-course-header">
        <div className="sw-course-info">
          <span className="sw-course-code">CSCA 5424</span>
          <h3 className="sw-course-name">Approximation Algorithms</h3>
          <span className="sw-course-deadline">Target: April 14</span>
        </div>
        <div className="sw-course-progress-ring">
          <svg viewBox="0 0 36 36" className="sw-ring">
            <path className="sw-ring-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
            <path className="sw-ring-fill" strokeDasharray={`${pct}, 100`} d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
          </svg>
          <span className="sw-ring-text">{done}/{total}</span>
        </div>
      </div>

      {CSCA5424_TASKS.map((phase, pi) => (
        <div key={pi} className="sw-phase">
          <div className="sw-phase-header">
            <h4 className="sw-phase-title">{phase.phase}</h4>
            <span className="sw-phase-sub">{phase.subtitle}</span>
          </div>
          <div className="sw-items">
            {phase.items.map((item, ii) => {
              const isChecked = checked[item.id];
              const isOff = item.off;
              const isBuffer = item.buffer;
              const showDate = ii === 0 || phase.items[ii - 1].date !== item.date;
              const isToday = item.date === todayStr;

              return (
                <div key={item.id}>
                  {showDate && (
                    <div className={`sw-date-header ${isToday ? 'today' : ''}`}>
                      {item.date}
                      {isToday && <span className="sw-today-badge">TODAY</span>}
                    </div>
                  )}
                  <div
                    className={`sw-item ${isOff ? 'off' : ''} ${isChecked ? 'checked' : ''} ${isBuffer ? 'buffer' : ''}`}
                    onClick={() => !isOff && toggle(item.id)}
                  >
                    {!isOff ? (
                      <div className={`sw-checkbox ${isChecked ? 'checked' : ''} ${isBuffer ? 'buffer' : ''}`}>
                        {isChecked && <span>✓</span>}
                      </div>
                    ) : (
                      <div className="sw-checkbox off">—</div>
                    )}
                    <span className={`sw-item-label ${isChecked ? 'checked' : ''}`}>{item.label}</span>
                    {item.time && !isOff && (
                      <span className={`sw-time-badge ${isChecked ? 'checked' : ''}`}>{item.time}</span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}

// ── CSCA 5454 Course View ──

function Course5454({ checked, toggle, done, total, pct, todayStr }: {
  checked: Record<string, boolean>;
  toggle: (id: string) => void;
  done: number;
  total: number;
  pct: number;
  todayStr: string;
}) {
  return (
    <div className="sw-course">
      <div className="sw-course-header">
        <div className="sw-course-info">
          <span className="sw-course-code">CSCA 5454</span>
          <h3 className="sw-course-name">Advanced DS, RSA & Quantum</h3>
          <span className="sw-course-deadline">Deadline: April 30</span>
        </div>
        <div className="sw-course-progress-ring">
          <svg viewBox="0 0 36 36" className="sw-ring">
            <path className="sw-ring-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
            <path className="sw-ring-fill" strokeDasharray={`${pct}, 100`} d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
          </svg>
          <span className="sw-ring-text">{done}/{total}</span>
        </div>
      </div>

      {CSCA5454_SCHEDULE.map((day, i) => {
        const isToday = day.date === todayStr;
        const allDone = day.tasks && day.tasks.every(t => checked[t.id]);
        const cat = day.category ? CAT_COLORS[day.category] : null;

        if (day.off) {
          return (
            <div key={i} className={`sw-day-off ${day.exam ? 'exam' : ''} ${day.deadline ? 'deadline' : ''} ${day.buffer ? 'buffer' : ''}`}>
              <span className="sw-day-off-date">{day.date}</span>
              <span className={`sw-day-off-label ${day.exam ? 'exam' : ''} ${day.deadline ? 'deadline' : ''}`}>
                {day.label}
              </span>
              <span className="sw-day-off-tag">OFF</span>
            </div>
          );
        }

        return (
          <div key={i} className={`sw-day ${isToday ? 'today' : ''}`}>
            {isToday && <div className="sw-today-bar" />}
            <div className="sw-day-header">
              <div className="sw-day-header-left">
                <span className="sw-day-date">{day.date}</span>
                {isToday && <span className="sw-today-badge">TODAY</span>}
                {cat && (
                  <span className="sw-cat-badge" style={{ color: cat.accent, background: `${cat.accent}22` }}>
                    {cat.label}
                  </span>
                )}
              </div>
              <div className="sw-day-header-right">
                {day.phase && <span className="sw-phase-badge">P{day.phase}</span>}
                {allDone && <span className="sw-day-done">✓</span>}
              </div>
            </div>
            <div className="sw-day-tasks">
              {day.tasks!.map(task => (
                <button
                  key={task.id}
                  className={`sw-task-btn ${checked[task.id] ? 'checked' : ''}`}
                  onClick={() => toggle(task.id)}
                >
                  <div className={`sw-checkbox ${checked[task.id] ? 'checked' : ''}`}>
                    {checked[task.id] && <span>✓</span>}
                  </div>
                  <div className="sw-task-content">
                    <span className={`sw-task-text ${checked[task.id] ? 'checked' : ''}`}>
                      <span className="sw-type-icon">{TYPE_ICONS[task.type]}</span>
                      {task.text}
                    </span>
                  </div>
                  <span className="sw-task-time">{task.time}</span>
                </button>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
}
