import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { CapacityControllerScreen } from '../CapacityControllerScreen';
import { STORAGE_KEY, decodePersisted } from '../capacityModel';

describe('CapacityControllerScreen', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('renders the HUD, status strip, audit form and prescription engine', () => {
    render(<CapacityControllerScreen />);
    expect(screen.getByRole('heading', { name: /6D Capacity Controller/i })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /State-space HUD/i })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /Diagnostic status/i })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /Telemetry ingestion audit/i })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /Dynamic prescription engine/i })).toBeInTheDocument();
    expect(screen.getByRole('radiogroup', { name: /Primary Modality Vector/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Integrate Discrete Flux/i })).toBeInTheDocument();
  });

  it('integrates a block, advances the counter, logs it, reports the transition and persists to localStorage', () => {
    render(<CapacityControllerScreen />);
    const form = screen.getByRole('region', { name: /Telemetry ingestion audit/i });
    expect(within(form).getByRole('radiogroup', { name: /Novelty/i })).toBeInTheDocument();
    expect(within(form).getByRole('radio', { name: /Unbuffered Speculative Intake/i })).toBeInTheDocument();
    fireEvent.click(within(form).getByRole('radio', { name: /Zero-Vector/i }));
    fireEvent.click(within(form).getByRole('radio', { name: /^25m/i }));
    fireEvent.click(screen.getByRole('button', { name: /Integrate Discrete Flux/i }));

    expect(screen.getByText(/block k1 · t_awake 0\.4 h/i)).toBeInTheDocument();
    expect(screen.getByText(/Block Log · 1 entries/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/State vector update/i)).toHaveTextContent(/block k1 · Δt 25 m/i);
    expect(screen.getByRole('button', { name: /Copy Report/i })).toBeInTheDocument();
    // Exactly one trailing operational prompt.
    expect(screen.getAllByLabelText(/Operational prompt/i)).toHaveLength(1);
    expect(screen.getByLabelText(/Operational prompt/i)).toHaveTextContent(/Lock in «|Terminate the session/);

    const persisted = decodePersisted(localStorage.getItem(STORAGE_KEY));
    expect(persisted.blockIndex).toBe(1);
    expect(persisted.history).toHaveLength(1);
    expect(persisted.history[0].spec?.modality).toBe('zero');
    expect(persisted.hoursAwake).toBeCloseTo(25 / 60, 6);
  });

  it('restores persisted state on mount and supports undo', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        version: 1,
        x: { E: 0.31, B: 0.72, Fvis: 0.2, Fbody: 0.2, A: 0.5, V: 0.8 },
        hoursAwake: 5,
        blockIndex: 3,
        history: [],
        spec: { modality: 'auditory' },
      }),
    );
    render(<CapacityControllerScreen />);
    // B ≥ 0.60 ∧ E < 0.40 routes to Quadrant I-A.
    expect(screen.getByText('Zero-Input Flush')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Undo/i })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: /Integrate Discrete Flux/i }));
    expect(screen.getByRole('button', { name: /Undo/i })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: /Undo/i }));
    expect(screen.getByText(/block k3 · t_awake 5\.0 h/i)).toBeInTheDocument();
  });

  it('calibrates the state vector through the override modal', () => {
    render(<CapacityControllerScreen />);
    fireEvent.click(screen.getByRole('button', { name: /Override/i }));
    const dialog = screen.getByRole('dialog', { name: /Manual Override/i });
    fireEvent.change(within(dialog).getByLabelText('Cognitive Backlog value'), { target: { value: '0.9' } });
    fireEvent.change(within(dialog).getByLabelText('Systemic Energy value'), { target: { value: '0.2' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /Apply Calibration/i }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('Zero-Input Flush')).toBeInTheDocument();
    expect(decodePersisted(localStorage.getItem(STORAGE_KEY)).x.B).toBeCloseTo(0.9, 6);
  });

  it('surfaces guardrail violations for the armed block', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        version: 1,
        x: { E: 0.7, B: 0.7, Fvis: 0.65, Fbody: 0.2, A: 0.5, V: 0.8 },
        hoursAwake: 3,
        blockIndex: 2,
        history: [],
        spec: { modality: 'reading', cadence: 'm25' },
      }),
    );
    render(<CapacityControllerScreen />);
    const form = screen.getByRole('region', { name: /Telemetry ingestion audit/i });
    expect(within(form).getByText(/Optical cutoff: F_vis = 0\.65 ≥ 0\.60 forces I_vis = 0/i)).toBeInTheDocument();
    expect(within(form).getByText(/Backlog saturated \(B = 0\.70, lock at 0\.65\)/i)).toBeInTheDocument();
    // Routed to I-B: backlog jammed with reserves available.
    expect(screen.getByText('Expressive Digestion')).toBeInTheDocument();
  });

  it('flags the burnout singularity late in the circadian phase', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ version: 1, x: { E: 0.3, B: 0.5, Fvis: 0.2, Fbody: 0.2, A: 0.5, V: 0.8 }, hoursAwake: 17, blockIndex: 9, history: [] }),
    );
    render(<CapacityControllerScreen />);
    expect(screen.getByRole('alert')).toHaveTextContent(/Burnout Singularity/i);
    expect(screen.getByRole('button', { name: /Log Sleep Reset/i })).toBeInTheDocument();
  });
});
