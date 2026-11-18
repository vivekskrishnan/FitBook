'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from './AuthProvider';

export function NavBar() {
  const { user, loading, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  const link = (href: string, label: string) => (
    <Link href={href} className={pathname === href ? 'active' : undefined}>
      {label}
    </Link>
  );

  return (
    <header className="nav">
      <div className="nav-inner">
        <Link href="/" className="brand">
          FitBook
        </Link>
        <nav>
          {link('/', 'Slots')}
          {user?.role === 'CUSTOMER' && link('/appointments', 'My appointments')}
          {user?.role === 'TRAINER' && link('/provider', 'Dashboard')}
          {user && link('/assistant', 'AI assistant')}
        </nav>
        <div className="nav-user">
          {loading ? null : user ? (
            <>
              <span className="muted">
                {user.name} ({user.role.toLowerCase()})
              </span>
              <button
                className="btn small secondary"
                onClick={async () => {
                  await logout();
                  router.push('/login');
                }}
              >
                Log out
              </button>
            </>
          ) : (
            <Link href="/login" className="btn small">
              Log in
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
