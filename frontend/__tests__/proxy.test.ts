import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NextRequest } from 'next/server';
import { handleProxy, TOKEN_COOKIE } from '@/lib/proxy';

// The proxy is the security-sensitive part of the frontend: it keeps the JWT in an httpOnly cookie,
// out of the browser's JavaScript. fetch is mocked, so no backend is needed.

const fetchMock = vi.fn();

function req(path: string, init: { method?: string; body?: string; token?: string; headers?: Record<string, string> } = {}) {
  const headers: Record<string, string> = { ...(init.headers ?? {}) };
  if (init.token) headers['cookie'] = `${TOKEN_COOKIE}=${init.token}`;
  return new NextRequest(`http://localhost:3000${path}`, { method: init.method ?? 'GET', body: init.body, headers });
}

function upstream(status: number, body: unknown, contentType = 'application/json') {
  return new Response(status === 204 ? null : JSON.stringify(body), { status, headers: { 'content-type': contentType } });
}

function sentHeaders(): Record<string, string> {
  return fetchMock.mock.calls[0][1].headers as Record<string, string>;
}

beforeEach(() => {
  process.env.BACKEND_URL = 'http://backend:8080';
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockReset();
});
afterEach(() => {
  vi.unstubAllGlobals();
});

describe('login', () => {
  it('stores the token in an httpOnly cookie and removes it from the response body', async () => {
    fetchMock.mockResolvedValue(
      upstream(200, { token: 'jwt-abc', tokenType: 'Bearer', expiresInSeconds: 3600, user: { id: 1, role: 'CUSTOMER' } }),
    );

    const res = await handleProxy(req('/api/auth/login', { method: 'POST', body: '{"email":"a","password":"b"}' }), ['auth', 'login']);

    const body = await res.json();
    expect(body.token).toBeUndefined();
    expect(JSON.stringify(body)).not.toContain('jwt-abc');
    expect(body.user.role).toBe('CUSTOMER');

    const cookie = res.headers.get('set-cookie') ?? '';
    expect(cookie).toContain(`${TOKEN_COOKIE}=jwt-abc`);
    expect(cookie.toLowerCase()).toContain('httponly');
    expect(cookie.toLowerCase()).toContain('samesite=lax');
    expect(cookie).toContain('Max-Age=3600');
    expect(cookie).toContain('Path=/');
  });

  it('sends the login to the backend without any Authorization header', async () => {
    fetchMock.mockResolvedValue(upstream(200, { token: 't', expiresInSeconds: 60, user: {} }));

    await handleProxy(req('/api/auth/login', { method: 'POST', body: '{}', token: 'old-token' }), ['auth', 'login']);

    expect(fetchMock.mock.calls[0][0]).toBe('http://backend:8080/api/auth/login');
    expect(sentHeaders()['Authorization']).toBeUndefined();
  });

  it('passes a failed login through and sets no cookie', async () => {
    fetchMock.mockResolvedValue(upstream(401, { error: 'Invalid email or password.' }));

    const res = await handleProxy(req('/api/auth/login', { method: 'POST', body: '{}' }), ['auth', 'login']);

    expect(res.status).toBe(401);
    expect((await res.json()).error).toBe('Invalid email or password.');
    expect(res.headers.get('set-cookie')).toBeNull();
  });
});

describe('authenticated calls', () => {
  it('turns the cookie into a Bearer header', async () => {
    fetchMock.mockResolvedValue(upstream(200, []));

    await handleProxy(req('/api/appointments', { token: 'jwt-abc' }), ['appointments']);

    expect(sentHeaders()['Authorization']).toBe('Bearer jwt-abc');
  });

  it('sends no Authorization header when there is no cookie', async () => {
    fetchMock.mockResolvedValue(upstream(200, { slots: [] }));

    await handleProxy(req('/api/slots'), ['slots']);

    expect(sentHeaders()['Authorization']).toBeUndefined();
  });

  it('keeps the query string', async () => {
    fetchMock.mockResolvedValue(upstream(200, { slots: [] }));

    await handleProxy(req('/api/slots?trainerId=1&date=2026-11-20'), ['slots']);

    expect(fetchMock.mock.calls[0][0]).toBe('http://backend:8080/api/slots?trainerId=1&date=2026-11-20');
  });

  it('forwards the method, body and content type', async () => {
    fetchMock.mockResolvedValue(upstream(201, { appointmentId: 5 }));

    await handleProxy(
      req('/api/appointments', { method: 'POST', body: '{"slotId":3}', token: 't', headers: { 'content-type': 'application/json' } }),
      ['appointments'],
    );

    const init = fetchMock.mock.calls[0][1];
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"slotId":3}');
    expect(init.headers['Content-Type']).toBe('application/json');
  });

  it('relays status codes such as 409 conflicts unchanged', async () => {
    fetchMock.mockResolvedValue(upstream(409, { error: 'Slot 3 is no longer available' }));

    const res = await handleProxy(req('/api/appointments', { method: 'POST', body: '{}', token: 't' }), ['appointments']);

    expect(res.status).toBe(409);
    expect((await res.json()).error).toContain('no longer available');
  });

  it('relays 204 No Content', async () => {
    fetchMock.mockResolvedValue(upstream(204, null));

    const res = await handleProxy(req('/api/provider/slots/9', { method: 'DELETE', token: 't' }), ['provider', 'slots', '9']);

    expect(res.status).toBe(204);
  });

  it('clears an expired or invalid token when the backend answers 401', async () => {
    fetchMock.mockResolvedValue(upstream(401, { error: 'unauthorized' }));

    const res = await handleProxy(req('/api/appointments', { token: 'expired' }), ['appointments']);

    expect(res.status).toBe(401);
    expect(res.headers.get('set-cookie') ?? '').toContain('Max-Age=0');
  });

  it('answers 502 with a generic message when the backend is down', async () => {
    fetchMock.mockRejectedValue(new Error('connect ECONNREFUSED 10.0.0.5:8080'));

    const res = await handleProxy(req('/api/slots'), ['slots']);

    expect(res.status).toBe(502);
    const text = JSON.stringify(await res.json());
    expect(text).toContain('unavailable');
    expect(text).not.toContain('ECONNREFUSED');   // no internal details leak to the browser
  });
});

describe('logout', () => {
  it('clears the cookie without calling the backend', async () => {
    const res = await handleProxy(req('/api/auth/logout', { method: 'POST', token: 'jwt-abc' }), ['auth', 'logout']);

    expect(res.status).toBe(200);
    expect(res.headers.get('set-cookie') ?? '').toContain('Max-Age=0');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('path safety', () => {
  it.each([[['..', 'admin']], [['appointments', '..', '..']], [['a%2Fb'.replace('%2F', '/')]], [[]], [['', 'x']]])(
    'rejects unsafe path %j',
    async (path) => {
      const res = await handleProxy(req('/api/x'), path as string[]);

      expect(res.status).toBe(400);
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );
});
