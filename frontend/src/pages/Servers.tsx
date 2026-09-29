import { useSim } from '../state/sim';
import { fmt, fmtInt } from '../lib/format';
import { BackendDown } from './Overview';
import {
  CIRCUIT_COLOR,
  EmptyState,
  RMI_COLOR,
  SERVER_STATE_COLOR,
  Skeleton,
  StateBadge,
} from '../components/ui';

function sev(v: number, warnAt: number, critAt: number): string {
  return v >= critAt ? '#EF4444' : v >= warnAt ? '#F59E0B' : '#a1a1aa';
}

export default function Servers() {
  const { ready, backendUp, servers, serverIds, openDrawer, simState } = useSim();

  if (!ready) return <div className="p-4"><Skeleton className="h-[300px]" /></div>;
  if (!backendUp) return <div className="p-4"><BackendDown /></div>;
  if (serverIds.length === 0)
    return (
      <div className="p-4">
        <EmptyState
          title="No game servers registered"
          hint={simState === 'STOPPED' ? 'Start the simulation to bring the RMI game server nodes online.' : 'Waiting for server registry…'}
        />
      </div>
    );

  return (
    <div className="p-4">
      <div className="flex items-center gap-3 mb-3">
        <h1 className="text-[13px] font-semibold text-zinc-200">Game Servers</h1>
        <span className="font-mono text-[10px] text-zinc-600 tnum">{serverIds.length} nodes · java rmi</span>
      </div>

      <div className="border border-line rounded-[6px] overflow-hidden">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-line bg-panel font-mono text-[9px] uppercase tracking-[0.1em] text-zinc-600">
              {['Server', 'State', 'RMI', 'Circuit', 'CPU', 'GPU', 'Latency', 'Loss', 'Sessions', 'Req/s', 'BW'].map((h) => (
                <th key={h} className="px-3 py-2 font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="font-mono text-[11px]">
            {serverIds.map((id) => {
              const s = servers[id];
              const m = s.metrics;
              return (
                <tr
                  key={id}
                  onClick={() => openDrawer(id)}
                  className="border-b border-line/60 last:border-b-0 hover:bg-panel2 cursor-pointer transition-colors"
                  tabIndex={0}
                  onKeyDown={(e) => e.key === 'Enter' && openDrawer(id)}
                >
                  <td className="px-3 py-2.5">
                    <div className="text-zinc-100 font-bold">{id}</div>
                    <div className="text-[10px] text-zinc-600">{s.city} · cap {s.capacity}</div>
                  </td>
                  <td className="px-3 py-2.5">
                    <StateBadge color={SERVER_STATE_COLOR[s.state]} label={s.state} pulse={s.state === 'RECOVERING'} />
                  </td>
                  <td className="px-3 py-2.5">
                    <StateBadge color={RMI_COLOR[s.rmiStatus]} label={s.rmiStatus} />
                  </td>
                  <td className="px-3 py-2.5">
                    <StateBadge color={CIRCUIT_COLOR[s.circuitState]} label={s.circuitState} />
                  </td>
                  <td className="px-3 py-2.5 tnum" style={{ color: sev(m.cpu, 75, 90) }}>{fmt(m.cpu)}%</td>
                  <td className="px-3 py-2.5 tnum" style={{ color: sev(m.gpu, 75, 90) }}>{fmt(m.gpu)}%</td>
                  <td className="px-3 py-2.5 tnum" style={{ color: sev(m.latencyMs, 50, 100) }}>{fmtInt(m.latencyMs)} ms</td>
                  <td className="px-3 py-2.5 tnum" style={{ color: sev(m.packetLoss, 1, 5) }}>{fmt(m.packetLoss, 2)}%</td>
                  <td className="px-3 py-2.5 tnum text-zinc-300">{m.sessions}<span className="text-zinc-600">/{s.capacity}</span></td>
                  <td className="px-3 py-2.5 tnum text-zinc-400">{fmt(m.requestsPerSec)}</td>
                  <td className="px-3 py-2.5 tnum text-zinc-400">{fmtInt(m.networkMbps)}<span className="text-zinc-600"> Mbps</span></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="mt-2 font-mono text-[10px] text-zinc-600">
        click a row to open the inspection drawer — metrics, sessions, routing history, fault injection
      </p>
    </div>
  );
}
