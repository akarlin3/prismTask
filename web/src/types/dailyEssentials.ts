export interface MedicationSlotCompletion {
  id: number;
  date: string;
  slot_key: string;
  med_ids: string[];
  taken_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface MedicationSlotToggleRequest {
  date: string;
  slot_key: string;
  med_ids: string[];
  taken: boolean;
}

export interface MedicationSlotBatchEntry {
  slot_key: string;
  med_ids: string[];
  taken: boolean;
}

export interface MedicationSlotBatchRequest {
  date: string;
  entries: MedicationSlotBatchEntry[];
}

export interface MedicationSlotBatchResponse {
  updated: number;
  slots: MedicationSlotCompletion[];
}

/**
 * Client-side medication slot shape used by the Today UI. Web does not
 * compute virtual doses from raw habit/self-care data the way Android does;
 * it derives slot rows from the materialized completion rows plus whatever
 * ``med_ids`` the caller supplies at toggle time.
 */
export interface MedicationSlot {
  slotKey: string;
  displayTime: string;
  medLabels: string[];
  medIds: string[];
  takenAt: string | null;
}
