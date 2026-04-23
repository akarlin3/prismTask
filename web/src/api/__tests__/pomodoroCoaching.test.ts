import { describe, it, expect, vi, beforeEach } from 'vitest';

const { postMock } = vi.hoisted(() => ({ postMock: vi.fn() }));
vi.mock('@/api/client', () => ({ default: { post: postMock } }));

import { aiApi } from '@/api/ai';

describe('aiApi.pomodoroCoaching', () => {
  beforeEach(() => postMock.mockReset());

  it('POSTs a pre_session payload with upcoming tasks', async () => {
    postMock.mockResolvedValueOnce({ data: { message: 'Breathe in.' } });
    const res = await aiApi.pomodoroCoaching({
      trigger: 'pre_session',
      upcoming_tasks: [
        { task_id: 't1', title: 'Review PR', allocated_minutes: 25 },
      ],
      session_length_minutes: 25,
    });
    expect(postMock).toHaveBeenCalledWith('/ai/pomodoro-coaching', {
      trigger: 'pre_session',
      upcoming_tasks: [
        { task_id: 't1', title: 'Review PR', allocated_minutes: 25 },
      ],
      session_length_minutes: 25,
    });
    expect(res.message).toBe('Breathe in.');
  });

  it('POSTs a break_activity payload with recent suggestions', async () => {
    postMock.mockResolvedValueOnce({ data: { message: 'Stretch.' } });
    await aiApi.pomodoroCoaching({
      trigger: 'break_activity',
      elapsed_minutes: 3,
      break_type: 'short',
      recent_suggestions: ['Drink water'],
    });
    expect(postMock).toHaveBeenCalledWith('/ai/pomodoro-coaching', {
      trigger: 'break_activity',
      elapsed_minutes: 3,
      break_type: 'short',
      recent_suggestions: ['Drink water'],
    });
  });

  it('POSTs a session_recap payload with completed + started tasks', async () => {
    postMock.mockResolvedValueOnce({ data: { message: 'Solid set.' } });
    await aiApi.pomodoroCoaching({
      trigger: 'session_recap',
      completed_tasks: [{ title: 'Ship PR' }],
      started_tasks: [{ title: 'Draft RFC' }],
      session_duration_minutes: 50,
    });
    expect(postMock).toHaveBeenCalledWith('/ai/pomodoro-coaching', {
      trigger: 'session_recap',
      completed_tasks: [{ title: 'Ship PR' }],
      started_tasks: [{ title: 'Draft RFC' }],
      session_duration_minutes: 50,
    });
  });
});
