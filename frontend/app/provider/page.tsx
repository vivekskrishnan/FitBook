'use client';

import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { RequireRole } from '@/components/RequireRole';
import { api } from '@/lib/api';
import { formatPrice, formatWhen, toApiDateTime } from '@/lib/format';
import type { Appointment, ReminderSummary, Slot } from '@/lib/types';

function ProviderContent() {
  const [slots, setSlots] = useState<Slot[] | null>(null);
  const [bookings, setBookings] = useState<Appointment[]>([]);
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [reminderResult, setReminderResult] = useState<string | null>(null);
  const [running, setRunning] = useState(false);

  const load = useCallback(async () => {
    try {
      const [s, b] = await Promise.all([api<Slot[]>('provider/slots'), api<Appointment[]>('provider/bookings')]);
      setSlots(s);
      setBookings(b);
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);
  useEffect(() => {
    load();
  }, [load]);

  async function addSlot(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api('provider/slots', { method: 'POST', body: { startTime: toApiDateTime(start), endTime: toApiDateTime(end) } });
      setStart('');
      setEnd('');
    } catch (err) {
      setError((err as Error).message);
    }
    load();
  }

  async function removeSlot(id: number) {
    setError(null);
    try {
      await api(`provider/slots/${id}`, { method: 'DELETE' });
    } catch (err) {
      setError((err as Error).message);
    }
    load();
  }

  async function sendReminders() {
    setRunning(true);
    setReminderResult(null);
    try {
      const r = await api<ReminderSummary>('ai/reminders/run?hours=24', { method: 'POST' });
      setReminderResult(
        `Found ${r.found}, sent ${r.sent} (AI-written ${r.llmWritten}, fallback ${r.fallbackUsed}), already reminded ${r.alreadyReminded}, failed ${r.failed}.`,
      );
    } catch (err) {
      setReminderResult((err as Error).message);
    } finally {
      setRunning(false);
    }
  }

  return (
    <>
      <h1>Trainer dashboard</h1>
      {error && <p className="alert error">{error}</p>}

      <h2>Appointment reminders (AI)</h2>
      <div className="row">
        <button className="btn secondary" disabled={running} onClick={sendReminders}>
          {running ? 'Running...' : 'Send reminders now'}
        </button>
        <span className="muted">{reminderResult ?? 'Reminds customers whose sessions start within 24 hours.'}</span>
      </div>

      <h2>Add a slot</h2>
      <form className="filters" onSubmit={addSlot}>
        <label>Start<input type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} required /></label>
        <label>End<input type="datetime-local" value={end} onChange={(e) => setEnd(e.target.value)} required /></label>
        <button className="btn" type="submit">Add slot</button>
      </form>

      <h2>My slots</h2>
      {!slots ? (
        <p className="muted">Loading...</p>
      ) : slots.length === 0 ? (
        <p className="muted">No slots yet.</p>
      ) : (
        <table>
          <thead><tr><th>Service</th><th>When</th><th>Price</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {slots.map((s) => (
              <tr key={s.slotId}>
                <td>{s.serviceName}</td>
                <td>{formatWhen(s.startTime)} - {formatWhen(s.endTime).split(', ').pop()}</td>
                <td>{formatPrice(s.price)}</td>
                <td><span className="badge">{s.status}</span></td>
                <td className="actions">
                  {s.status === 'OPEN' && <button className="btn small danger" onClick={() => removeSlot(s.slotId)}>Remove</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>Bookings on my slots</h2>
      {bookings.length === 0 ? (
        <p className="muted">No bookings yet.</p>
      ) : (
        <table>
          <thead><tr><th>Service</th><th>When</th><th>Status</th></tr></thead>
          <tbody>
            {bookings.map((a) => (
              <tr key={a.appointmentId}>
                <td>{a.serviceName}</td>
                <td>{formatWhen(a.startTime)}</td>
                <td><span className={`badge ${a.status === 'CANCELLED' ? 'cancelled' : ''}`}>{a.status}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

export default function ProviderPage() {
  return (
    <RequireRole role="TRAINER">
      <ProviderContent />
    </RequireRole>
  );
}
