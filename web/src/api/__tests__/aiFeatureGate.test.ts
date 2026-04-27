import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import axios from 'axios';
import apiClient, {
  AI_PATH_PREFIXES,
  HEADER_AI_FEATURES,
  HEADER_VALUE_DISABLED,
  HTTP_451_UNAVAILABLE_FOR_LEGAL_REASONS,
  buildAiDisabledError,
  setAiFeaturesEnabledProvider,
} from '@/api/client';

// Mock toast so the response interceptor's user-facing notification doesn't
// blow up jsdom; tests assert toast call count where it matters.
const toastErrorMock = vi.fn();
vi.mock('sonner', () => ({
  toast: {
    error: (...args: unknown[]) => toastErrorMock(...args),
  },
}));

describe('aiFeatureGateInterceptor', () => {
  // Replace axios's network layer with a fake adapter so we can detect when
  // a request actually leaves the client (gate is OFF means the adapter is
  // never invoked).
  const adapterMock = vi.fn();

  beforeEach(() => {
    adapterMock.mockReset();
    toastErrorMock.mockReset();
    apiClient.defaults.adapter = adapterMock;
    // Default the gate to ENABLED (web's default before the user toggles).
    setAiFeaturesEnabledProvider(() => true);
  });

  afterEach(() => {
    setAiFeaturesEnabledProvider(() => true);
  });

  describe('when AI features are ENABLED', () => {
    it.each(AI_PATH_PREFIXES.map((p) => [p]))(
      'forwards requests to %s through to the network adapter',
      async (prefix) => {
        adapterMock.mockResolvedValueOnce({
          data: { ok: true },
          status: 200,
          statusText: 'OK',
          headers: {},
          config: {},
        });
        const path = prefix.endsWith('/') ? `${prefix}endpoint` : prefix;
        const res = await apiClient.post(path, { foo: 'bar' });
        expect(adapterMock).toHaveBeenCalledTimes(1);
        expect(res.status).toBe(200);
      },
    );

    it('forwards non-AI paths through to the network adapter', async () => {
      adapterMock.mockResolvedValueOnce({
        data: { tasks: [] },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {},
      });
      await apiClient.get('/tasks');
      expect(adapterMock).toHaveBeenCalledTimes(1);
    });
  });

  describe('when AI features are DISABLED', () => {
    beforeEach(() => {
      setAiFeaturesEnabledProvider(() => false);
    });

    it('rejects /ai/eisenhower with a synthetic 451 and never hits the network', async () => {
      await expect(apiClient.post('/ai/eisenhower', {})).rejects.toMatchObject({
        response: {
          status: HTTP_451_UNAVAILABLE_FOR_LEGAL_REASONS,
        },
      });
      expect(adapterMock).not.toHaveBeenCalled();
    });

    it('rejects /tasks/parse (NLP quick-add) without hitting the network', async () => {
      await expect(apiClient.post('/tasks/parse', { text: 'buy milk' })).rejects.toMatchObject({
        response: {
          status: HTTP_451_UNAVAILABLE_FOR_LEGAL_REASONS,
        },
      });
      expect(adapterMock).not.toHaveBeenCalled();
    });

    it('rejects /syllabus/parse without hitting the network', async () => {
      await expect(apiClient.post('/syllabus/parse', { text: 'syl' })).rejects.toMatchObject({
        response: {
          status: HTTP_451_UNAVAILABLE_FOR_LEGAL_REASONS,
        },
      });
      expect(adapterMock).not.toHaveBeenCalled();
    });

    it.each([
      ['/tasks', 'GET'],
      ['/projects', 'POST'],
      ['/habits', 'GET'],
      ['/auth/me', 'GET'],
    ])('still forwards non-AI request %s (%s) to the adapter', async (path, method) => {
      adapterMock.mockResolvedValueOnce({
        data: {},
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {},
      });
      if (method === 'POST') {
        await apiClient.post(path, {});
      } else {
        await apiClient.get(path);
      }
      expect(adapterMock).toHaveBeenCalledTimes(1);
    });

    it('stamps the X-PrismTask-AI-Features: disabled header on the synthetic response', async () => {
      try {
        await apiClient.post('/ai/daily-briefing', {});
        expect.fail('expected a synthetic 451 rejection');
      } catch (err) {
        const e = err as { response?: { headers?: Record<string, string> } };
        expect(e.response?.headers?.[HEADER_AI_FEATURES]).toBe(HEADER_VALUE_DISABLED);
      }
    });

    it('triggers a user-facing toast via the response interceptor', async () => {
      await expect(apiClient.post('/ai/weekly-review', {})).rejects.toBeTruthy();
      // The 451 branch in the response interceptor calls toast.error once.
      expect(toastErrorMock).toHaveBeenCalledTimes(1);
      expect(toastErrorMock.mock.calls[0][0]).toMatch(/AI features are disabled/i);
    });
  });

  describe('path matching', () => {
    beforeEach(() => {
      setAiFeaturesEnabledProvider(() => false);
    });

    it('strips the query string before matching', async () => {
      await expect(apiClient.get('/ai/eisenhower?dry=1')).rejects.toMatchObject({
        response: { status: HTTP_451_UNAVAILABLE_FOR_LEGAL_REASONS },
      });
      expect(adapterMock).not.toHaveBeenCalled();
    });

    it('does not match /ai-prefixed paths that are not under /ai/ (defense)', async () => {
      adapterMock.mockResolvedValueOnce({
        data: {},
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {},
      });
      // "/aircraft" must not be treated as AI-touching just because it starts
      // with the literal substring "/ai" — the prefix is "/ai/" (with slash).
      await apiClient.get('/aircraft');
      expect(adapterMock).toHaveBeenCalledTimes(1);
    });
  });

  describe('buildAiDisabledError', () => {
    it('returns a real AxiosError with response.status=451', () => {
      const err = buildAiDisabledError({ url: '/ai/foo', headers: {} } as never);
      expect(axios.isAxiosError(err)).toBe(true);
      expect(err.response?.status).toBe(451);
      expect(err.response?.data).toMatchObject({ detail: expect.stringContaining('disabled') });
    });
  });
});
