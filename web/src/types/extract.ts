/**
 * Types matching `backend/app/schemas/ai.py`:
 *   ExtractFromTextRequest / ExtractedTaskCandidate / ExtractFromTextResponse
 *
 * Pastes — chat transcripts, meeting notes, emails — go in as `text`
 * (max 10k chars). The backend returns proposed task candidates; the
 * client renders them as a diff-preview and lets the user pick which
 * ones to commit. `suggested_priority` is the Android 0–4 scale
 * (0=None, 4=Urgent); callers convert via `androidToWebPriority` before
 * showing it in the UI.
 */

export interface ExtractFromTextRequest {
  text: string;
  source?: string;
}

export interface ExtractedTaskCandidate {
  title: string;
  suggested_due_date?: string | null;
  suggested_priority: number;
  suggested_project?: string | null;
  confidence: number;
}

export interface ExtractFromTextResponse {
  tasks: ExtractedTaskCandidate[];
}
