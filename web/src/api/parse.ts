import apiClient from './client';
import type { NLPParseRequest, NLPParseResult } from '@/types/api';

export const parseApi = {
  parse(data: NLPParseRequest): Promise<NLPParseResult> {
    return apiClient.post('/tasks/parse', data).then((r) => r.data);
  },
};
