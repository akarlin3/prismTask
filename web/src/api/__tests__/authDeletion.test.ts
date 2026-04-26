import { describe, it, expect, vi, beforeEach } from 'vitest';

const { getMock, postMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  deleteMock: vi.fn(),
}));
vi.mock('@/api/client', () => ({
  default: { get: getMock, post: postMock, delete: deleteMock },
}));

import { authApi } from '@/api/auth';

describe('authApi.getDeletionStatus', () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    deleteMock.mockReset();
  });

  it('GETs /auth/me/deletion and returns the response body', async () => {
    getMock.mockResolvedValueOnce({
      data: {
        deletion_pending_at: null,
        deletion_scheduled_for: null,
        deletion_initiated_from: null,
      },
    });
    const status = await authApi.getDeletionStatus();
    expect(getMock).toHaveBeenCalledWith('/auth/me/deletion');
    expect(status.deletion_pending_at).toBeNull();
  });
});

describe('authApi.requestAccountDeletion', () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    deleteMock.mockReset();
  });

  it('POSTs /auth/me/deletion with initiated_from="web" by default', async () => {
    postMock.mockResolvedValueOnce({
      data: {
        deletion_pending_at: '2026-04-25T12:00:00Z',
        deletion_scheduled_for: '2026-05-25T12:00:00Z',
        deletion_initiated_from: 'web',
      },
    });
    const status = await authApi.requestAccountDeletion();
    expect(postMock).toHaveBeenCalledWith('/auth/me/deletion', {
      initiated_from: 'web',
    });
    expect(status.deletion_initiated_from).toBe('web');
  });

  it('passes a non-default initiated_from when explicitly provided', async () => {
    postMock.mockResolvedValueOnce({
      data: {
        deletion_pending_at: '2026-04-25T12:00:00Z',
        deletion_scheduled_for: '2026-05-25T12:00:00Z',
        deletion_initiated_from: 'email',
      },
    });
    await authApi.requestAccountDeletion('email');
    expect(postMock).toHaveBeenCalledWith('/auth/me/deletion', {
      initiated_from: 'email',
    });
  });
});

describe('authApi.cancelAccountDeletion', () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    deleteMock.mockReset();
  });

  it('DELETEs /auth/me/deletion', async () => {
    deleteMock.mockResolvedValueOnce({
      data: {
        deletion_pending_at: null,
        deletion_scheduled_for: null,
        deletion_initiated_from: null,
      },
    });
    const status = await authApi.cancelAccountDeletion();
    expect(deleteMock).toHaveBeenCalledWith('/auth/me/deletion');
    expect(status.deletion_pending_at).toBeNull();
  });
});
