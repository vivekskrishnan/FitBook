'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/components/AuthProvider';
import { api } from '@/lib/api';
import { formatPrice, formatWhen } from '@/lib/format';
import type { SlotOptions, SlotsPage } from '@/lib/types';

// Public browse page. Booking needs a customer login; the button only shows for customers.
export default function SlotsPageView() {
  const { user } = useAuth();
  const [options, setOptions] = useState<SlotOptions>({ trainers: [], services: [] });
  const [trainerId, setTrainerId] = useState('');
  const [serviceId, setServiceId] = useState('');
  const [date, setDate] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<SlotsPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [booking, setBooking] = useState<number | null>(null);

  const load = useCallback(async () => {
    const params = new URLSearchParams({ page: String(page) });
    if (trainerId) params.set('trainerId', trainerId);
    if (serviceId) params.set('serviceId', serviceId);
    if (date) params.set('date', date);
    try {
      setData(await api<SlotsPage>(`slots?${params}`));
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [page, trainerId, serviceId, date]);

  useEffect(() => {
    api<SlotOptions>('slots/options').then(setOptions).catch(() => {});
  }, []);
  useEffect(() => {
    load();
  }, [load]);

  async function book(slotId: number) {
    setBooking(slotId);
    setNotice(null);
    setError(null);
    try {
      await api('appointments', { method: 'POST', body: { slotId } });
      setNotice('Booked! A confirmation was sent. See it under My appointments.');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBooking(null);
      load(); // the slot is gone (or someone else took it), so refresh the list either way
    }
  }

  const resetPage = <T,>(set: (v: T) => void) => (v: T) => {
    set(v);
    setPage(0);
  };

  return (
    <>
      <h1>Open sessions</h1>

      <div className="filters">
        <label>
          Trainer
          <select value={trainerId} onChange={(e) => resetPage(setTrainerId)(e.target.value)}>
            <option value="">Any</option>
            {options.trainers.map((t) => (
              <option key={t.id} value={t.id}>{t.name}</option>
            ))}
          </select>
        </label>
        <label>
          Service
          <select value={serviceId} onChange={(e) => resetPage(setServiceId)(e.target.value)}>
            <option value="">Any</option>
            {options.services.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        </label>
        <label>
          Date
          <input type="date" value={date} onChange={(e) => resetPage(setDate)(e.target.value)} />
        </label>
      </div>

      {error && <p className="alert error">{error}</p>}
      {notice && <p className="alert ok">{notice}</p>}
      {!user && (
        <p className="muted">
          <Link href="/login">Log in</Link> as a customer to book a session.
        </p>
      )}

      {!data ? (
        <p className="muted">Loading...</p>
      ) : data.slots.length === 0 ? (
        <p className="muted">No open slots match those filters.</p>
      ) : (
        <table>
          <thead>
            <tr><th>Service</th><th>Trainer</th><th>When</th><th>Price</th><th></th></tr>
          </thead>
          <tbody>
            {data.slots.map((s) => (
              <tr key={s.slotId}>
                <td>{s.serviceName}</td>
                <td>{s.trainerName}</td>
                <td>{formatWhen(s.startTime)}</td>
                <td>{formatPrice(s.price)}</td>
                <td className="actions">
                  {user?.role === 'CUSTOMER' && (
                    <button className="btn small" disabled={booking === s.slotId} onClick={() => book(s.slotId)}>
                      {booking === s.slotId ? 'Booking...' : 'Book'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {data && data.totalPages > 1 && (
        <div className="pager">
          <button className="btn small secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span className="muted">Page {data.page + 1} of {data.totalPages}</span>
          <button className="btn small secondary" disabled={page >= data.totalPages - 1} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </>
  );
}
