import { useEffect, useState } from 'react';
import * as api from '../api';
import { API_BASE, WS_URL } from '../config';
import { useSim } from '../state/sim';
import { fmtTime } from '../lib/format';
import { BackendDown } from './Overview';
import { Btn, SectionTitle, Skeleton, StateBadge, WS_COLOR } from '../components/ui';

export default function Settings() {
  const { ready, backendUp, wsStatus, simState, speed, strategy, retrySeed } = useSim();
  const [test, setTest] = useState<{ ok: boolean; ms: number; detail: string } | null>(null);
  const [testing, setTesting] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [resetErr, setResetErr] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const testConnection = async () => {
    setTesting(true);
    setTest(null);
    const t0 = performance.now();
    try {
      const sys = await api.getSystem();
      setTest({
        ok: true,
        ms: Math.round(performance.now() - t0),
        detail: `status=${sys.status} state=${sys.simulationState} uptime=${sys.uptimeSec}s`,
      });
    } catch (e) {
      setTest({ ok: false, ms: Math.round(performance.now() - t0), detail: e instanceof Error ? e.message : 'failed' });
    } finally {
      setTesting(false);
    }
  };

  const reset = async () => {
    setResetting(true);
    setResetErr(null);
    try {
      await api.simReset();
    } catch (e) {
      setResetErr(e instanceof Error ? e.message : 'Reset failed');
    } finally {
      setResetting(false);
    }
  };

  if (!ready) return <div className="p-4"><Skeleton className="h-[300px]" /></div>;

  return (
    <div className="p-4 max-w-[720px]">
      <h1 className="text-[13px] font-semibold text-zinc-200 mb-3">Settings</h1>

      <section className="mb-5">
        <SectionTitle>Connection</SectionTitle>
        <div className="border border-line rounded-[6px] bg-panel divide-y divide-line font-mono text-[11px]">
          <div className="px-3 py-2 flex justify-between gap-4">
            <span className="text-zinc-500">REST base URL</span>
            <span className="text-zinc-200 break-all text-right">{API_BASE}</span>
          </div>
          <div className="px-3 py-2 flex justify-between gap-4">
            <span className="text-zinc-500">WebSocket URL</span>
            <span className="text-zinc-200 break-all text-right">{WS_URL}</span>
          </div>
          <div className="px-3 py-2 flex justify-between">
            <span className="text-zinc-500">WebSocket status</span>
            <StateBadge color={WS_COLOR[wsStatus]} label={wsStatus} />
          </div>
          <div className="px-3 py-2 flex justify-between">
            <span className="text-zinc-500">Backend</span>
            <StateBadge color={backendUp ? '#22C55E' : '#EF4444'} label={backendUp ? 'REACHABLE' : 'DOWN'} />
          </div>
          <div className="px-3 py-2 flex justify-between">
            <span className="text-zinc-500">Simulation</span>
            <span className="text-zinc-200">{simState} · {speed}x · {strategy}</span>
          </div>
        </div>
        <div className="mt-2 flex items-center gap-2">
          <Btn onClick={testConnection} disabled={testing}>
            {testing ? 'testing…' : 'Test connection'}
          </Btn>
          {test && (
            <span className="font-mono text-[10px] tnum" style={{ color: test.ok ? '#22C55E' : '#EF4444' }}>
              {test.ok ? `OK · ${test.ms} ms · ${test.detail}` : `FAIL · ${test.detail}`}
            </span>
          )}
        </div>
        <p className="mt-2 font-mono text-[10px] text-zinc-600">
          override with VITE_API_URL / VITE_WS_URL at build time · dev proxy maps /api and /ws to localhost:8080
        </p>
      </section>

      <section className="mb-5">
        <SectionTitle>Danger zone</SectionTitle>
        <div className="border border-line rounded-[6px] bg-panel p-3 flex items-center gap-3">
          <div className="flex-1">
            <div className="text-[12px] text-zinc-300">Reset simulation</div>
            <div className="font-mono text-[10px] text-zinc-600 mt-0.5">
              clears sessions, routing decisions, events, faults and scenarios
            </div>
          </div>
          <Btn accent="red" onClick={reset} disabled={resetting || !backendUp}>
            {resetting ? '…' : 'Reset'}
          </Btn>
        </div>
        {resetErr && <div className="mt-2 font-mono text-[10px] text-err">{resetErr}</div>}
        {!backendUp && (
          <div className="mt-3"><BackendDown onRetry={retrySeed} /></div>
        )}
      </section>

      <section>
        <SectionTitle>About</SectionTitle>
        <div className="border border-line rounded-[6px] bg-panel p-3 font-mono text-[11px] text-zinc-400 leading-relaxed">
          <div><span className="text-zinc-200 font-bold">GameFlow LB</span> <span className="text-zinc-600">v0.1.0</span></div>
          <div className="mt-1">
            Interactive cloud-gaming load balancer simulator — watch player requests flow through a
            Java routing engine, over RMI, onto simulated GPU game servers.
          </div>
          <div className="mt-2 text-zinc-600">
            frontend: react 18 · vite 5 · typescript · tailwind · reactflow · recharts
            <br />
            backend: java 21 · spring boot · webflux · java rmi · websocket
          </div>
          <div className="mt-2 text-zinc-600">local time {fmtTime(now)}</div>
        </div>
      </section>
    </div>
  );
}
