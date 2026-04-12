import { describe, it, expect } from 'vitest';
import { getPriorityLabel, getPriorityColor, PRIORITY_CONFIG } from '@/utils/priority';
import type { TaskPriority } from '@/types/task';

describe('priority utils', () => {
  describe('getPriorityLabel', () => {
    it('returns "Urgent" for priority 1', () => {
      expect(getPriorityLabel(1)).toBe('Urgent');
    });

    it('returns "High" for priority 2', () => {
      expect(getPriorityLabel(2)).toBe('High');
    });

    it('returns "Medium" for priority 3', () => {
      expect(getPriorityLabel(3)).toBe('Medium');
    });

    it('returns "Low" for priority 4', () => {
      expect(getPriorityLabel(4)).toBe('Low');
    });

    it('returns "None" for unknown priority values', () => {
      // Cast to bypass TS type checking for edge case
      expect(getPriorityLabel(0 as TaskPriority)).toBe('None');
      expect(getPriorityLabel(5 as TaskPriority)).toBe('None');
      expect(getPriorityLabel(99 as TaskPriority)).toBe('None');
    });
  });

  describe('getPriorityColor', () => {
    it('returns urgent CSS variable for priority 1', () => {
      expect(getPriorityColor(1)).toBe('var(--color-priority-urgent)');
    });

    it('returns high CSS variable for priority 2', () => {
      expect(getPriorityColor(2)).toBe('var(--color-priority-high)');
    });

    it('returns medium CSS variable for priority 3', () => {
      expect(getPriorityColor(3)).toBe('var(--color-priority-medium)');
    });

    it('returns low CSS variable for priority 4', () => {
      expect(getPriorityColor(4)).toBe('var(--color-priority-low)');
    });

    it('returns none CSS variable for unknown priority', () => {
      expect(getPriorityColor(0 as TaskPriority)).toBe('var(--color-priority-none)');
      expect(getPriorityColor(99 as TaskPriority)).toBe('var(--color-priority-none)');
    });
  });

  describe('PRIORITY_CONFIG', () => {
    it('has entries for all 4 priority levels', () => {
      expect(PRIORITY_CONFIG[1]).toBeDefined();
      expect(PRIORITY_CONFIG[2]).toBeDefined();
      expect(PRIORITY_CONFIG[3]).toBeDefined();
      expect(PRIORITY_CONFIG[4]).toBeDefined();
    });

    it('each entry has label, color, and bgColor', () => {
      for (const key of [1, 2, 3, 4] as TaskPriority[]) {
        const config = PRIORITY_CONFIG[key];
        expect(config).toHaveProperty('label');
        expect(config).toHaveProperty('color');
        expect(config).toHaveProperty('bgColor');
        expect(typeof config.label).toBe('string');
        expect(typeof config.color).toBe('string');
        expect(typeof config.bgColor).toBe('string');
      }
    });

    it('has correct labels for each priority', () => {
      expect(PRIORITY_CONFIG[1].label).toBe('Urgent');
      expect(PRIORITY_CONFIG[2].label).toBe('High');
      expect(PRIORITY_CONFIG[3].label).toBe('Medium');
      expect(PRIORITY_CONFIG[4].label).toBe('Low');
    });

    it('bgColor values contain rgba', () => {
      for (const key of [1, 2, 3, 4] as TaskPriority[]) {
        expect(PRIORITY_CONFIG[key].bgColor).toMatch(/^rgba\(/);
      }
    });
  });
});
