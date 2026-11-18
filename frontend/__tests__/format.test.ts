import { describe, expect, it } from 'vitest';
import { formatPrice, formatWhen, isUpcoming, toApiDateTime } from '@/lib/format';

describe('formatWhen', () => {
  it('shows the local time exactly as sent, with no time-zone shift', () => {
    expect(formatWhen('2026-11-20T09:00:00')).toBe('Fri, Nov 20, 9:00 AM');
    expect(formatWhen('2026-11-20T21:30:00')).toBe('Fri, Nov 20, 9:30 PM');
  });

  it('returns the input unchanged if it is not a date-time', () => {
    expect(formatWhen('soon')).toBe('soon');
  });
});

describe('isUpcoming', () => {
  const now = new Date(2026, 10, 20, 12, 0); // Nov 20, 2026 12:00 local

  it('is true for later times and false for earlier ones', () => {
    expect(isUpcoming('2026-11-20T12:01:00', now)).toBe(true);
    expect(isUpcoming('2026-11-20T11:59:00', now)).toBe(false);
    expect(isUpcoming('2025-01-01T09:00:00', now)).toBe(false);
  });

  it('is false for garbage', () => {
    expect(isUpcoming('nope', now)).toBe(false);
  });
});

describe('formatPrice', () => {
  it('always shows two decimals', () => {
    expect(formatPrice(45)).toBe('$45.00');
    expect(formatPrice(20.5)).toBe('$20.50');
  });
});

describe('toApiDateTime', () => {
  it('adds seconds to a datetime-local value', () => {
    expect(toApiDateTime('2026-11-20T09:00')).toBe('2026-11-20T09:00:00');
    expect(toApiDateTime('2026-11-20T09:00:00')).toBe('2026-11-20T09:00:00');
  });
});
