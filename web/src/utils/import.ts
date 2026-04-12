import { exportApi } from '@/api/export';

export interface ImportPreview {
  version: number;
  goalCount: number;
  projectCount: number;
  taskCount: number;
  tagCount: number;
  habitCount: number;
  templateCount: number;
}

export function validateImportFile(data: unknown): ImportPreview | null {
  if (!data || typeof data !== 'object') return null;
  const obj = data as Record<string, unknown>;

  if (!obj.version || typeof obj.version !== 'number') return null;

  return {
    version: obj.version as number,
    goalCount: Array.isArray(obj.goals) ? obj.goals.length : 0,
    projectCount: Array.isArray(obj.projects) ? obj.projects.length : 0,
    taskCount: Array.isArray(obj.tasks) ? obj.tasks.length : 0,
    tagCount: Array.isArray(obj.tags) ? obj.tags.length : 0,
    habitCount: Array.isArray(obj.habits) ? obj.habits.length : 0,
    templateCount: Array.isArray(obj.templates) ? obj.templates.length : 0,
  };
}

export async function parseImportFile(file: File): Promise<{ data: unknown; preview: ImportPreview }> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const data = JSON.parse(e.target?.result as string);
        const preview = validateImportFile(data);
        if (!preview) {
          reject(new Error('Invalid import file format. Missing "version" field.'));
          return;
        }
        resolve({ data, preview });
      } catch {
        reject(new Error('Failed to parse JSON file.'));
      }
    };
    reader.onerror = () => reject(new Error('Failed to read file.'));
    reader.readAsText(file);
  });
}

export async function importData(
  file: File,
  mode: 'merge' | 'replace',
  onProgress?: (step: string) => void,
): Promise<Record<string, number>> {
  onProgress?.('Uploading and importing data...');
  const result = await exportApi.importJson(file, mode);
  return result;
}
