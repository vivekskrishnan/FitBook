// Small fetch wrapper for the browser. Calls go to this Next.js app's own /api/* proxy, which
// attaches the JWT from the httpOnly cookie, so no token handling happens here.

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const res = await fetch(`/api/${path}`, {
    method: init.method ?? 'GET',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
    credentials: 'same-origin',
    cache: 'no-store',
  });
  if (res.status === 204) return undefined as T;

  const text = await res.text();
  let data: unknown = undefined;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    // not JSON (for example an HTML error page); fall through to the generic message
  }
  if (!res.ok) {
    const message =
      (data && typeof data === 'object' && 'error' in data && typeof (data as { error: unknown }).error === 'string'
        ? (data as { error: string }).error
        : null) ?? friendlyStatus(res.status);
    throw new ApiError(res.status, message);
  }
  return data as T;
}

function friendlyStatus(status: number): string {
  if (status === 401) return 'Please log in again.';
  if (status === 403) return 'You do not have access to that.';
  if (status === 404) return 'Not found.';
  if (status >= 500) return 'Something went wrong on the server. Please try again.';
  return 'Request failed.';
}
