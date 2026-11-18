import { NextRequest } from 'next/server';
import { handleProxy } from '@/lib/proxy';

export const dynamic = 'force-dynamic';

type Ctx = { params: Promise<{ path: string[] }> };

async function forward(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  return handleProxy(req, path);
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const DELETE = forward;
