import { NextRequest, NextResponse } from 'next/server';

// Backend-for-frontend proxy. The browser only ever talks to this Next.js server (same origin);
// this code forwards /api/* calls to the Spring Boot API and handles the JWT:
//
//   - On login, the token from the backend is stored in an httpOnly cookie and REMOVED from the
//     response body, so page JavaScript can never read it (an XSS bug cannot steal the token).
//   - On every other call, the cookie is turned into an "Authorization: Bearer <token>" header.
//   - On logout, the cookie is cleared.
//
// SameSite=Lax on the cookie plus the fact that only this proxy (not the backend) reads it keeps
// cross-site requests from riding on the login.

export const TOKEN_COOKIE = 'fb_token';

export function backendUrl(): string {
  return (process.env.BACKEND_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
}

function json(body: unknown, status: number): NextResponse {
  return NextResponse.json(body, { status });
}

function clearCookie(res: NextResponse): NextResponse {
  res.cookies.set(TOKEN_COOKIE, '', { httpOnly: true, sameSite: 'lax', path: '/', maxAge: 0 });
  return res;
}

export async function handleProxy(req: NextRequest, path: string[]): Promise<NextResponse> {
  // Never let a crafted path escape /api/ on the backend.
  if (path.length === 0 || path.some((seg) => seg === '' || seg === '.' || seg === '..' || seg.includes('/') || seg.includes('\\'))) {
    return json({ error: 'Bad request path.' }, 400);
  }
  const joined = path.join('/');

  if (joined === 'auth/logout') {
    return clearCookie(json({ ok: true }, 200));
  }

  const headers: Record<string, string> = { Accept: 'application/json' };
  const contentType = req.headers.get('content-type');
  if (contentType) headers['Content-Type'] = contentType;
  const token = req.cookies.get(TOKEN_COOKIE)?.value;
  if (token && joined !== 'auth/login') headers['Authorization'] = `Bearer ${token}`;

  const hasBody = req.method !== 'GET' && req.method !== 'HEAD';
  let upstream: Response;
  try {
    upstream = await fetch(`${backendUrl()}/api/${joined}${req.nextUrl.search}`, {
      method: req.method,
      headers,
      body: hasBody ? await req.text() : undefined,
      cache: 'no-store',
    });
  } catch {
    return json({ error: 'The server is unavailable. Please try again.' }, 502);
  }

  if (upstream.status === 204) {
    return new NextResponse(null, { status: 204 });
  }
  const text = await upstream.text();

  if (joined === 'auth/login' && upstream.ok) {
    const data = JSON.parse(text) as { token: string; expiresInSeconds: number; [key: string]: unknown };
    const { token: issued, ...safe } = data;
    const res = json(safe, 200);
    res.cookies.set(TOKEN_COOKIE, issued, {
      httpOnly: true,
      sameSite: 'lax',
      path: '/',
      secure: process.env.COOKIE_SECURE === 'true',
      maxAge: data.expiresInSeconds,
    });
    return res;
  }

  const res = new NextResponse(text, {
    status: upstream.status,
    headers: { 'Content-Type': upstream.headers.get('content-type') ?? 'application/json' },
  });
  // An expired or invalid token: drop the cookie so the UI sends the user back to the login page.
  return upstream.status === 401 && token ? clearCookie(res) : res;
}
