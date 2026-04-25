import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MedicationTierPicker } from '@/features/medication/MedicationTierPicker';

describe('MedicationTierPicker', () => {
  it('renders all 4 canonical tier buttons in canonical order', () => {
    render(
      <MedicationTierPicker
        value={null}
        isUserSet={false}
        onChange={() => {}}
      />,
    );
    const buttons = screen.getAllByRole('button');
    expect(buttons).toHaveLength(4);
    expect(buttons[0]).toHaveTextContent('Skipped');
    expect(buttons[1]).toHaveTextContent('Essential');
    expect(buttons[2]).toHaveTextContent('Prescription');
    expect(buttons[3]).toHaveTextContent('Complete');
  });

  it('marks the selected tier with aria-pressed=true', () => {
    render(
      <MedicationTierPicker
        value="prescription"
        isUserSet
        onChange={() => {}}
        onClear={() => {}}
      />,
    );
    expect(screen.getByTitle('Mark Prescription')).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByTitle('Mark Skipped')).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('emits the canonical lowercase tier on click', async () => {
    const onChange = vi.fn();
    render(
      <MedicationTierPicker
        value={null}
        isUserSet={false}
        onChange={onChange}
      />,
    );
    await userEvent.click(screen.getByTitle('Mark Essential'));
    expect(onChange).toHaveBeenCalledWith('essential');

    await userEvent.click(screen.getByTitle('Mark Prescription'));
    expect(onChange).toHaveBeenLastCalledWith('prescription');
  });

  it('renders the Auto clear button only when isUserSet', () => {
    const { rerender } = render(
      <MedicationTierPicker
        value="essential"
        isUserSet={false}
        onChange={() => {}}
        onClear={() => {}}
      />,
    );
    expect(screen.queryByTitle(/Clear override/)).toBeNull();

    rerender(
      <MedicationTierPicker
        value="essential"
        isUserSet
        onChange={() => {}}
        onClear={() => {}}
      />,
    );
    expect(screen.getByTitle(/Clear override/)).toBeInTheDocument();
  });
});
