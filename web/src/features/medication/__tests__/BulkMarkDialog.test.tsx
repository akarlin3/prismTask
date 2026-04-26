import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BulkMarkDialog } from '@/features/medication/BulkMarkDialog';
import type { MedicationSlot } from '@/types/dailyEssentials';

const slots: MedicationSlot[] = [
  {
    slotKey: 'morning',
    displayTime: 'morning',
    medLabels: ['Adderall', 'Vitamin D'],
    medIds: ['med:adderall', 'med:vitamin_d'],
    takenAt: null,
  },
  {
    slotKey: 'evening',
    displayTime: 'evening',
    medLabels: ['Lexapro'],
    medIds: ['med:lexapro'],
    takenAt: null,
  },
];

describe('BulkMarkDialog', () => {
  let onCancel: () => void;
  let onConfirm: (params: {
    scope: 'slot' | 'full_day';
    slotKey: string | null;
    tier: import('@/api/firestore/medicationSlots').MedicationTier;
  }) => Promise<void>;

  beforeEach(() => {
    onCancel = vi.fn();
    onConfirm = vi.fn(() => Promise.resolve()) as typeof onConfirm;
  });

  function renderOpen() {
    return render(
      <BulkMarkDialog
        isOpen
        slots={slots}
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    );
  }

  it('opens with scope=slot and the first slot pre-selected', () => {
    renderOpen();
    const slotRadio = screen.getByLabelText(/This slot/i);
    expect(slotRadio).toBeChecked();
    // The slot picker fieldset is visible in slot scope.
    expect(screen.getByText(/^Slot$/i)).toBeInTheDocument();
  });

  it('disables Mark until a tier is picked', async () => {
    const user = userEvent.setup();
    renderOpen();

    const markButton = screen.getByRole('button', { name: /^Mark$/i });
    expect(markButton).toBeDisabled();

    await user.click(screen.getByRole('button', { name: /^essential$/ }));
    expect(markButton).toBeEnabled();
  });

  it('hides the slot picker when scope flips to full_day', async () => {
    const user = userEvent.setup();
    renderOpen();
    expect(screen.queryByText(/^Slot$/i)).toBeInTheDocument();

    await user.click(screen.getByLabelText(/Full day/i));

    expect(screen.queryByText(/^Slot$/i)).not.toBeInTheDocument();
  });

  it('summary line reflects the picked scope, slot, and tier', async () => {
    const user = userEvent.setup();
    renderOpen();
    await user.click(screen.getByRole('button', { name: /^complete$/ }));

    // Scope=slot, default slot=morning (2 medications), tier=complete.
    expect(
      screen.getByText(/will mark slot "morning" \(2 medications\) as complete/i),
    ).toBeInTheDocument();

    // Flip to full_day → summary covers all slots.
    await user.click(screen.getByLabelText(/Full day/i));
    expect(
      screen.getByText(/will mark 2 slots across today as complete/i),
    ).toBeInTheDocument();
  });

  it('passes the picked scope/slotKey/tier to onConfirm for slot scope', async () => {
    const user = userEvent.setup();
    renderOpen();
    // Pick the second slot.
    await user.click(screen.getByLabelText(/evening/i));
    await user.click(screen.getByRole('button', { name: /^prescription$/ }));
    await user.click(screen.getByRole('button', { name: /^Mark$/i }));

    await waitFor(() =>
      expect(onConfirm).toHaveBeenCalledWith({
        scope: 'slot',
        slotKey: 'evening',
        tier: 'prescription',
      }),
    );
  });

  it('clears slotKey to null when scope is full_day', async () => {
    const user = userEvent.setup();
    renderOpen();
    await user.click(screen.getByLabelText(/Full day/i));
    await user.click(screen.getByRole('button', { name: /^complete$/ }));
    await user.click(screen.getByRole('button', { name: /^Mark$/i }));

    await waitFor(() =>
      expect(onConfirm).toHaveBeenCalledWith({
        scope: 'full_day',
        slotKey: null,
        tier: 'complete',
      }),
    );
  });

  it('locks the dialog while submission is in flight', async () => {
    let resolveConfirm: () => void;
    const slowConfirm = vi.fn(
      () =>
        new Promise<void>((res) => {
          resolveConfirm = res;
        }),
    ) as typeof onConfirm;
    const user = userEvent.setup();
    render(
      <BulkMarkDialog
        isOpen
        slots={slots}
        onCancel={onCancel}
        onConfirm={slowConfirm}
      />,
    );
    await user.click(screen.getByRole('button', { name: /^complete$/ }));
    await user.click(screen.getByRole('button', { name: /^Mark$/i }));

    // Cancel button is disabled while submitting; user can't escape.
    const cancelButton = screen.getByRole('button', { name: /^Cancel$/i });
    expect(cancelButton).toBeDisabled();

    // Resolve to unlock.
    resolveConfirm!();
    await waitFor(() => expect(cancelButton).toBeEnabled());
  });
});
