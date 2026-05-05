// 4-week embedded firmware sprint schedule.
// Used as the canonical JSX-format fixture for /tasks/parse-checklist.
import React from 'react';

const SPRINT = [
  // Week 1 — Foundation
  { week: 1, phase: 'Foundation', task: 'Set up toolchain (gcc-arm, openocd)', priority: 4 },
  { week: 1, phase: 'Foundation', task: 'Read STM32 reference manual chapters 1-3', priority: 2 },
  { week: 1, phase: 'Foundation', task: 'Order Nucleo board + sensors', priority: 4 },

  // Week 2 — Bring-up
  { week: 2, phase: 'Bring-up', task: 'First boot + LED blink', priority: 4 },
  { week: 2, phase: 'Bring-up', task: 'Configure UART + clock tree', priority: 2 },
  { week: 2, phase: 'Bring-up', task: 'EXAM: bring-up review with mentor', priority: 4 },

  // Week 3 — Application
  { week: 3, phase: 'Application', task: 'Write I2C driver for IMU', priority: 2 },
  { week: 3, phase: 'Application', task: 'Implement 100Hz sampling loop', priority: 2 },
  { week: 3, phase: 'Application', task: 'Add SD-card data logger', priority: 1 },

  // Week 4 — Integration
  { week: 4, phase: 'Integration', task: '4-hour battery endurance test', priority: 2 },
  { week: 4, phase: 'Integration', task: 'EXAM: final demo to advisor', priority: 4 },
  { week: 4, phase: 'Integration', task: 'Write final report', priority: 2 },
];

export default function Schedule() {
  return <pre>{JSON.stringify(SPRINT, null, 2)}</pre>;
}
