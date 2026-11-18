'use client';

import { useCallback, useEffect, useState } from 'react';
import { RequireRole } from '@/components/RequireRole';
import { api } from '@/lib/api';
import { formatPrice, formatWhen, isUpcoming } from '@/lib/format';
import type { Appointment } from '@/lib/types';

function AppointmentsContent() {
  const [items, setItems] = useState<Appointment[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setItems(await api<Appointment[]>('appointments'));
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);
  useEffect(() => {
    load();
  }, [load]);

  async function cancel(a: Appointment) {
    if (!window.confirm(`Cancel ${a.serviceName} with ${a.trainerName} on ${formatWhen(a.startTime)}?`)) return;
    setError(null);
    setNotice(null);
    try {
      await api(`appointments/${a.appointmentId}/cancel`, { method: 'POST' });
      setNotice('Appointment cancelled. A confirmation was sent.');
    } catch (e) {
      setError((e as Error).message);
    }
    load();
  }

  const upcoming = (items ?? []).filter((a) => a.status === 'BOOKED' && isUpcoming(a.startTime));
  const history = (items ?? []).filter((a) => !(a.status === 'BOOKED' && isUpcoming(a.startTime)));

  return (
    <>
      <h1>My appointments</h1>
      {error && <p className="alert error">{error}</p>}
      {notice && <p className="alert ok">{notice}</p>}

      <h2>Upcoming</h2>
      {!items ? (
        <p className="muted">Loading...</p>
      ) : upcoming.length === 0 ? (
        <p className="muted">No upcoming appointments.</p>
      ) : (
        <table>
          <thead><tr><th>Service</th><th>Trainer</th><th>When</th><th>Price</th><th></th></tr></thead>
          <tbody>
            {upcoming.map((a) => (
              <tr key={a.appointmentId}>
                <td>{a.serviceName}</td>
                <td>{a.trainerName}</td>
                <td>{formatWhen(a.startTime)}</td>
                <td>{formatPrice(a.price)}</td>
                <td className="actions"><button className="btn small danger" onClick={() => cancel(a)}>Cancel</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>History</h2>
      {history.length === 0 ? (
        <p className="muted">Nothing yet.</p>
      ) : (
        <table>
          <thead><tr><th>Service</th><th>Trainer</th><th>When</th><th>Status</th></tr></thead>
          <tbody>
            {history.map((a) => (
              <tr key={a.appointmentId}>
                <td>{a.serviceName}</td>
                <td>{a.trainerName}</td>
                <td>{formatWhen(a.startTime)}</td>
                <td><span className={`badge ${a.status === 'CANCELLED' ? 'cancelled' : ''}`}>{a.status === 'BOOKED' ? 'COMPLETED' : a.status}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

export default function AppointmentsPage() {
  return (
    <RequireRole role="CUSTOMER">
      <AppointmentsContent />
    </RequireRole>
  );
}
