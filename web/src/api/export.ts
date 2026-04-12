import apiClient from './client';

export const exportApi = {
  exportJson(): Promise<Blob> {
    return apiClient
      .get('/export/json', { responseType: 'blob' })
      .then((r) => r.data);
  },

  exportCsv(): Promise<Blob> {
    return apiClient
      .get('/export/csv', { responseType: 'blob' })
      .then((r) => r.data);
  },

  importJson(file: File, mode: 'merge' | 'replace'): Promise<Record<string, number>> {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient
      .post(`/import/json?mode=${mode}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },
};
