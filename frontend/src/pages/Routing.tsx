import { useState } from 'react';
import { Check } from 'lucide-react';
import * as api from '../api';
import { useSim } from '../state/sim';
import { fmt, fmtTime } from '../lib/format';
import { BackendDown } from './Overview';
import { EmptyState, SectionTitle, Skeleton } from '../components/ui';
import type { RoutingStrategy } from '../types';

const STRATEGIES: { id: RoutingStrategy; name: string; desc: string }[] = [
  {
    id: 'WEIGHTED_GAMING',
    name: 'Weighted Gaming',
    desc: 'Composite score: latency ×0.35, cpu ×0.20, gpu ×0.20, loss ×0.10, sessions ×0.10, jitter ×0.05. Penalties above critical thresholds.',
  },
  {
    id: 'LEAST_SESSIONS',
    name: 'Least Sessions',
    desc: 'Routes to the eligible server with the fewest active sessions.',
  },
  {
    id: 'LOWEST_LATENCY',
    name: 'Lowest Latency',
    desc: 'Routes to the eligible server with the lowest round-trip latency.',
  },
  {
    id: 'ROUND_ROBIN',
    name: 'Round Robin',
    desc: 'Cycles through eligible servers in order. Baseline for comparison.',
  },
];

export default function Routing() {
  const { ready, backendUp, strategy, decisions, inspectDecision, simState } = useSim();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const change = async (s: RoutingStrategy) => {
    if (s === strategy || busy) return;
    setBusy(true);
    setErr(null);
    try {
      await api.putStrategy(s);
      // authoritative STRATEGY_CHANGED event arrives over WS; no local fake
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Strategy change failed');
    } finally {
      setBusy(false);
    }
  };

  if (!ready) return <div className="p-4"><Skeleton className="h-[300px]" /></div>;
  if (!backendUp) return <div className="p-4"><BackendDown /></div>;

  return (
    <div className="p-4 grid grid-cols-1 xl:grid-cols-[340px_1fr] gap-4">
      {/* strategy switcher */}
      <section>
        <SectionTitle>Routing strategy</SectionTitle>
        {err && <div className="mb-2 font-mono text-[10px] text-err">{err}</div>}
        <div className="space-y-1.5">
          {STRATEGIES.map((s) => {
            const active = strategy === s.id;
            return (
              <button
                key={s.id}
                onClick={() => change(s.id)}
                disabled={busy}
                className={`w-full text-left px-3 py-2.5 border rounded-[6px] transition-colors disabled:opacity-50 ${
                  active ? 'border-info/50 bg-info/[0.06]' : 'border-line hover:border-line2 hover:bg-panel2'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className={`font-mono text-[11px] font-bold ${active ? 'text-info' : 'text-zinc-200'}`}>
                    {s.name}
                  </span>
                  {active && <Check size={13} className="text-info" />}
                </div>
                <div className="mt-1 text-[10px] leading-relaxed text-zinc-500">{s.desc}</div>
                <div className="mt-1 font-mono text-[9px] text-zinc-600">{s.id}</div>
              </button>
            );
          })}
        </div>
        <p className="mt-2 font-mono text-[10px] text-zinc-600">
          switch is applied live by the java routing engine
        </p>
      </section>

      {/* decision feed */}
      <section>
        <SectionTitle
          right={<span className="font-mono text-[10px] text-zinc-600 tnum">{decisions.length} in buffer</span>}
        >
          Routing decisions — live feed
        </SectionTitle>
        {decisions.length === 0 ? (
          <EmptyState
            title="No routing decisions yet"
            hint={simState === 'STOPPED' ? 'Start the simulation to watch the routing engine score candidates.' : 'Waiting for the first player request…'}
          />
        ) : (
          <div className="border border-line rounded-[6px] divide-y divide-line max-h-[calc(100vh-220px)] overflow-y-auto">
            {decisions.map((d) => (
              <button
                key={d.id}
                onClick={() => inspectDecision(d)}
                className="w-full text-left px-3 py-2 hover:bg-panel2 transition-colors"
              >
                <div className="flex items-center gap-3">
                  <span className="font-mono text-[10px] text-zinc-500 w-[76px] tnum">{fmtTime(d.timestamp)}</span>
                  <span className="font-mono text-[11px] text-zinc-200 w-[70px]">{d.id}</span>
                  <span className="font-mono text-[11px] text-zinc-400">{d.playerId}</span>
                  <span className="font-mono text-[10px] text-zinc-600 truncate flex-1">{d.game} · {d.playerRegion}</span>
                  <span className="font-mono text-[11px] tnum" style={{ color: d.selectedServerId ? '#22C55E' : '#EF4444' }}>
                    {d.selectedServerId ?? 'NO CANDIDATE'}
                  </span>
                </div>
                <div className="mt-0.5 pl-[76px] font-mono text-[10px] text-zinc-600 truncate tnum">
                  {d.candidates
                    .slice()
                    .sort((a, b) => a.score - b.score)
                    .slice(0, 4)
                    .map((c) => `${c.serverId}:${fmt(c.score, 1)}`)
                    .join('  ')}
                </div>
              </button>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
