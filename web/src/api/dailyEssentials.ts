import apiClient from './client';
import type {
  MedicationSlotBatchRequest,
  MedicationSlotBatchResponse,
  MedicationSlotCompletion,
  MedicationSlotToggleRequest,
} from '@/types/dailyEssentials';

/**
 * Daily Essentials medication slot endpoints. Slots are materialized on the
 * backend the first time the user interacts with one; subsequent reads
 * return the persisted row(s) for the given date.
 */
export const dailyEssentialsApi = {
  listSlots(date: string): Promise<MedicationSlotCompletion[]> {
    return apiClient
      .get(`/daily-essentials/slots`, { params: { date } })
      .then((r) => r.data);
  },

  toggleSlot(
    body: MedicationSlotToggleRequest,
  ): Promise<MedicationSlotCompletion> {
    return apiClient
      .post(`/daily-essentials/slots/toggle`, body)
      .then((r) => r.data);
  },

  batchMark(
    body: MedicationSlotBatchRequest,
  ): Promise<MedicationSlotBatchResponse> {
    return apiClient
      .patch(`/daily-essentials/slots/batch`, body)
      .then((r) => r.data);
  },
};
