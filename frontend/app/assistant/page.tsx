'use client';

import { useEffect, useRef, useState, type FormEvent } from 'react';
import { RequireRole } from '@/components/RequireRole';
import { useAuth } from '@/components/AuthProvider';
import { api } from '@/lib/api';
import type { FaqAnswer } from '@/lib/types';

interface Message {
  from: 'me' | 'bot' | 'err';
  text: string;
}

// Agent 1: the booking chat (customers). The agent proposes a booking or cancellation and only
// carries it out after the user confirms in a later message; that rule is enforced by the backend.
function Chat() {
  const [messages, setMessages] = useState<Message[]>([
    { from: 'bot', text: 'Hi! I can find open slots, show your appointments, and book or cancel for you. What would you like?' },
  ]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const log = useRef<HTMLDivElement>(null);

  useEffect(() => {
    log.current?.scrollTo({ top: log.current.scrollHeight });
  }, [messages]);

  async function send(e: FormEvent) {
    e.preventDefault();
    const text = input.trim();
    if (!text) return;
    setMessages((m) => [...m, { from: 'me', text }]);
    setInput('');
    setBusy(true);
    try {
      const res = await api<{ reply: string }>('ai/chat', { method: 'POST', body: { message: text } });
      setMessages((m) => [...m, { from: 'bot', text: res.reply }]);
    } catch (err) {
      setMessages((m) => [...m, { from: 'err', text: (err as Error).message }]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h2>Booking assistant</h2>
      <p className="muted">Try: &quot;Book the earliest Pilates slot&quot; or &quot;Cancel my next appointment&quot;. I always ask you to confirm first.</p>
      <div className="chat-log" ref={log}>
        {messages.map((m, i) => (
          <div key={i} className={`msg ${m.from}`}>{m.text}</div>
        ))}
        {busy && <div className="msg bot muted">Thinking...</div>}
      </div>
      <form className="chat-form" onSubmit={send}>
        <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="Type a message..." disabled={busy} />
        <button className="btn" type="submit" disabled={busy}>Send</button>
      </form>
    </>
  );
}

// Grounded Q&A over the studio FAQ (RAG). Any logged-in user.
function Faq() {
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState<FaqAnswer | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function ask(e: FormEvent) {
    e.preventDefault();
    if (!question.trim()) return;
    setBusy(true);
    setError(null);
    setAnswer(null);
    try {
      setAnswer(await api<FaqAnswer>(`ai/ask?q=${encodeURIComponent(question)}`));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h2>Ask about the studio</h2>
      <form className="chat-form" onSubmit={ask}>
        <input value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="e.g. What is the cancellation policy?" disabled={busy} />
        <button className="btn secondary" type="submit" disabled={busy}>{busy ? 'Asking...' : 'Ask'}</button>
      </form>
      {error && <p className="alert error">{error}</p>}
      {answer && (
        <div className="card" style={{ marginTop: '0.75rem' }}>
          <div style={{ whiteSpace: 'pre-wrap' }}>{answer.answer}</div>
          {answer.sources.length > 0 && <div className="sources">Looked at: {answer.sources.join(', ')}</div>}
        </div>
      )}
    </>
  );
}

function AssistantContent() {
  const { user } = useAuth();
  return (
    <>
      <h1>AI assistant</h1>
      {user?.role === 'CUSTOMER' && <Chat />}
      <Faq />
    </>
  );
}

export default function AssistantPage() {
  return (
    <RequireRole>
      <AssistantContent />
    </RequireRole>
  );
}
