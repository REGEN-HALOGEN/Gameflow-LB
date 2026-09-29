import { useCallback, useRef, useState } from 'react';
import * as api from '../api';
import { useSim } from '../state/sim';
import type { FaultType } from '../types';

// ---------------------------------------------------------------------------
// ChaosBar — the interactive playground: traffic dial, one-click fault
// injection, and simulation transport controls. Everything here hits the live
// backend; the topology graph + stats react in real time.
// ---------------------------------------------------------------------------

const FAULTS: { type: FaultType; label: string; icon: string; color: string }[] = [
  { type: 'CRASH', label: 'Crash', icon: '💥', color: '#EF4444' },
  { type: 'GPU_OVERLOAD', label: 'GPU spike', icon: '🔥', color: '#F59E0B' },
  { type: 'LATENCY_SPIKE', label: 'Lag spike', icon: '🐌', color: '#A78BFA' },
  { type: 'PACKET_LOSS', label: 'Packet loss', icon: '🕳️', color: '#22D3EE' },
  { type: 'RMI_FAILURE', label: 'RMI cut', icon: '🔌', color: '#F472B6' },
];

const btn =
  'inline-flex items-center gap-1.5 px-2.5 h-8 rounded-[5px] border font-mono text-[11px] transition-all active:scale-95 disabled:opacity-40';

export default function ChaosBar() {
  const { serverIds, servers, simState, speed } = useSim();
  const [traffic, setTraffic] = useState(40);
  const [busy, setBusy] = useState(false);
  const trafficTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const healthy = serverIds.filter((id) => servers[id]?.state !== 'OFFLINE');
  const pickRandom = useCallback(() => {
    const pool = healthy.length > 0 ? healthy : serverIds;
    return pool[Math.floor(Math.random() * pool.length)];
  }, [healthy, serverIds]);

  const faultRandom = async (type: FaultType) => {
    const id = pickRandom();
    if (!id || busy) return;
    setBusy(true);
    try {
      await api.injectFault(id, type);
    } catch {
      /* backend down — the BackendDown state handles it */
    } finally {
      setBusy(false);
    }
  };

  const recoverAll = async () => {
    setBusy(true);
    try {
      await Promise.all(serverIds.map((id) => api.recoverServer(id).catch(() => null)));
    } finally {
      setBusy(false);
    }
  };

  const onTraffic = (v: number) => {
    setTraffic(v);
    if (trafficTimer.current) clearTimeout(trafficTimer.current);
    // Debounce: don't hammer the backend while dragging.
    trafficTimer.current = setTimeout(() => {
      api.simTraffic(v).catch(() => {});
    }, 250);
  };

  const transport = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await fn();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="border border-line rounded-[6px] bg-panel px-3 py-2.5 flex flex-wrap items-center gap-x-4 gap-y-2">
      {/* transport */}
      <div className="flex items-center gap-1.5">
        {simState !== 'RUNNING' ? (
          <button className={`${btn} border-emerald-500/40 text-emerald-300 hover:bg-emerald-500/10`} disabled={busy}
            onClick={() => transport(api.simStart)}>
            ▶ Start
          </button>
        ) : (
          <button className={`${btn} border-amber-500/40 text-amber-300 hover:bg-amber-500/10`} disabled={busy}
            onClick={() => transport(api.simPause)}>
            ⏸ Pause
          </button>
        )}
        <button className={`${btn} border-line2 text-zinc-300 hover:border-zinc-500`} disabled={busy}
          onClick={() => transport(api.simReset)} title="Reset simulation">
          ↺
        </button>
        <div className="flex items-center gap-1 ml-1">
          {[0.5, 1, 2, 5].map((s) => (
            <button key={s}
              className={`font-mono text-[10px] px-1.5 h-6 rounded ${speed === s ? 'bg-info/20 text-info' : 'text-zinc-500 hover:text-zinc-300'}`}
              onClick={() => api.simSpeed(s).catch(() => {})}>
              {s}×
            </button>
          ))}
        </div>
      </div>

      <div className="w-px h-6 bg-line hidden sm:block" />

      {/* traffic dial */}
      <div className="flex items-center gap-2 flex-1 min-w-[200px]">
        <span className="font-mono text-[10px] text-zinc-500 uppercase tracking-wider whitespace-nowrap">
          👥 traffic
        </span>
        <input
          type="range" min={0} max={240} value={traffic}
          onChange={(e) => onTraffic(Number(e.target.value))}
          className="flex-1 accent-[#4F8CFF] h-1 cursor-pointer"
          aria-label="Target concurrent player sessions"
        />
        <span className="font-mono text-[11px] text-zinc-200 tnum w-14 text-right">{traffic} sess</span>
      </div>

      <div className="w-px h-6 bg-line hidden sm:block" />

      {/* fault injection */}
      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="font-mono text-[10px] text-zinc-500 uppercase tracking-wider mr-1">⚡ chaos</span>
        {FAULTS.map((f) => (
          <button key={f.type}
            className={`${btn} text-zinc-200 hover:-translate-y-px hover:shadow-lg`}
            style={{ borderColor: f.color + '55' }}
            onMouseEnter={(e) => (e.currentTarget.style.borderColor = f.color)}
            onMouseLeave={(e) => (e.currentTarget.style.borderColor = f.color + '55')}
            disabled={busy}
            onClick={() => faultRandom(f.type)}
            title={`Inject ${f.label} on a random server`}>
            <span>{f.icon}</span> {f.label}
          </button>
        ))}
        <button className={`${btn} border-emerald-500/40 text-emerald-300 hover:bg-emerald-500/10`} disabled={busy}
          onClick={recoverAll} title="Recover all servers">
          ✨ Recover all
        </button>
      </div>
    </div>
  );
}
