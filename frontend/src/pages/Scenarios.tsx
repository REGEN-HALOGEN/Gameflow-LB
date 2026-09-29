import { useState } from 'react';
import { FlaskConical, Square } from 'lucide-react';
import * as api from '../api';
import { useSim } from '../state/sim';
import { BackendDown } from './Overview';
import { Btn, SectionTitle, Skeleton, StateBadge } from '../components/ui';
import type { FaultType, Scenario } from '../types';

const FAULTS: { type: FaultType; label: string }[] = [
  { type: 'GPU_OVERLOAD', label: 'GPU Overload' },
  { type: 'LATENCY_SPIKE', label: 'Latency Spike' },
  { type: 'PACKET_LOSS', label: 'Packet Loss' },
  { type: 'RMI_FAILURE', label: 'RMI Failure' },
  { type: 'CRASH', label: 'Crash Server' },
];

function ScenarioCard({ sc }: { sc: Scenario }) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const toggle = async () => {
    setBusy(true);
    setErr(null);
    try {
      if (sc.active) await api.stopScenario(sc.id);
      else await api.startScenario(sc.id);
      // authoritative SCENARIO_STARTED/STOPPED events arrive over WS
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Scenario request failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className={`border rounded-[6px] p-3 bg-panel transition-colors ${
        sc.active ? 'border-warn/50' : 'border-line hover:border-line2'
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <FlaskConical size={13} className={sc.active ? 'text-warn' : 'text-zinc-600'} />
          <span className="text-[12px] font-semibold text-zinc-200">{sc.name}</span>
        </div>
        {sc.active && <StateBadge color="#F59E0B" label="ACTIVE" pulse />}
      </div>
      <p className="mt-1.5 text-[11px] leading-relaxed text-zinc-500">{sc.description}</p>
      <div className="mt-2 flex items-center justify-between">
        <span className="font-mono text-[10px] text-zinc-600">
          {sc.targetServerId ? 'target ' + sc.targetServerId : 'fleet-wide'} · {sc.id}
        </span>
        <button
          onClick={toggle}
          disabled={busy}
          className={`inline-flex items-center gap-1.5 font-mono text-[10px] px-2.5 h-7 rounded-[4px] border transition-colors disabled:opacity-40 ${
            sc.active
              ? 'border-err/40 text-err hover:bg-err/10'
              : 'border-line2 text-zinc-300 hover:bg-panel2'
          }`}
        >
          {sc.active ? <Square size={10} /> : null}
          {busy ? '…' : sc.active ? 'STOP' : 'START'}
        </button>
      </div>
      {err && <div className="mt-1.5 font-mono text-[10px] text-err">{err}</div>}
    </div>
  );
}

export default function Scenarios() {
  const { ready, backendUp, scenarios, serverIds } = useSim();
  const [target, setTarget] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const server = target || serverIds[0] || '';

  const inject = async (type: FaultType) => {
    if (!server) return;
    setBusy(type);
    setErr(null);
    try {
      await api.injectFault(server, type);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Fault injection failed');
    } finally {
      setBusy(null);
    }
  };

  const recover = async () => {
    if (!server) return;
    setBusy('RECOVER');
    setErr(null);
    try {
      await api.recoverServer(server);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Recover failed');
    } finally {
      setBusy(null);
    }
  };

  if (!ready) return <div className="p-4"><Skeleton className="h-[300px]" /></div>;
  if (!backendUp) return <div className="p-4"><BackendDown /></div>;

  return (
    <div className="p-4 max-w-[1100px]">
      <h1 className="text-[13px] font-semibold text-zinc-200">Demo Scenarios</h1>
      <p className="font-mono text-[10px] text-zinc-600 mt-0.5 mb-3">
        deterministic scenario scripts that manipulate real simulation state — for live demonstrations
      </p>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
        {scenarios.map((sc) => (
          <ScenarioCard key={sc.id} sc={sc} />
        ))}
      </div>
      {scenarios.length === 0 && (
        <div className="font-mono text-[11px] text-zinc-600">no scenarios reported by backend</div>
      )}

      <section className="mt-6">
        <SectionTitle>Manual fault injection</SectionTitle>
        <div className="border border-line rounded-[6px] bg-panel p-3 flex items-center gap-2 flex-wrap">
          <span className="font-mono text-[10px] text-zinc-500 uppercase tracking-[0.08em]">target</span>
          <select
            value={server}
            onChange={(e) => setTarget(e.target.value)}
            className="h-7 bg-panel2 border border-line2 rounded-[4px] font-mono text-[11px] text-zinc-200 px-2"
            aria-label="Fault target server"
          >
            {serverIds.map((id) => (
              <option key={id} value={id}>{id}</option>
            ))}
          </select>
          <div className="w-px h-5 bg-line" />
          {FAULTS.map((f) => (
            <Btn key={f.type} accent="red" disabled={busy !== null || !server} onClick={() => inject(f.type)}>
              {busy === f.type ? '…' : f.label}
            </Btn>
          ))}
          <Btn accent="green" disabled={busy !== null || !server} onClick={recover}>
            {busy === 'RECOVER' ? '…' : 'Recover'}
          </Btn>
        </div>
        {err && <div className="mt-2 font-mono text-[10px] text-err">{err}</div>}
        <p className="mt-2 font-mono text-[10px] text-zinc-600">
          faults are injected into the java simulation — watch topology, routing and events react
        </p>
      </section>
    </div>
  );
}
