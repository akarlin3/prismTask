import apiClient from '@/api/client';
import type {
  SyllabusParseResult,
  SyllabusConfirmRequest,
  SyllabusConfirmResponse,
} from './syllabusTypes';

export async function parseSyllabus(file: File): Promise<SyllabusParseResult> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await apiClient.post<SyllabusParseResult>('/syllabus/parse', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
}

export async function confirmSyllabus(
  request: SyllabusConfirmRequest,
): Promise<SyllabusConfirmResponse> {
  const res = await apiClient.post<SyllabusConfirmResponse>('/syllabus/confirm', request);
  return res.data;
}
