'use client';

import { useRouter } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';
import { useAuth } from './AuthProvider';
import type { Role } from '@/lib/types';

// Wraps a page that needs a login (and optionally a specific role). Not logged in -> login page.
// Wrong role -> a short message. This is only a convenience for the UI: the real access control is
// enforced by the backend on every API call.
export function RequireRole({ role, children }: { role?: Role; children: ReactNode }) {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) router.replace('/login');
  }, [loading, user, router]);

  if (loading || !user) return <p className="muted">Loading...</p>;
  if (role && user.role !== role) {
    return <p className="alert error">This page is only for {role.toLowerCase()}s.</p>;
  }
  return <>{children}</>;
}
