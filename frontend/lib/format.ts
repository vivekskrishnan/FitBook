// "2026-11-20T09:00:00" -> "Fri, Nov 20, 9:00 AM". The backend sends local date-times without a
// zone, so parse the parts by hand instead of letting Date shift them into another time zone.
export function formatWhen(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso);
  if (!m) return iso;
  const [, y, mo, d, h, mi] = m;
  const date = new Date(Number(y), Number(mo) - 1, Number(d), Number(h), Number(mi));
  return date.toLocaleString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function formatPrice(price: number): string {
  return `$${Number(price).toFixed(2)}`;
}

// Whether a local date-time string is still in the future.
export function isUpcoming(iso: string, now: Date = new Date()): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso);
  if (!m) return false;
  const [, y, mo, d, h, mi] = m;
  return new Date(Number(y), Number(mo) - 1, Number(d), Number(h), Number(mi)).getTime() > now.getTime();
}

// <input type="datetime-local"> gives "2026-11-20T09:00"; the API wants seconds too.
export function toApiDateTime(local: string): string {
  return local.length === 16 ? `${local}:00` : local;
}
