import { useMemo, useState } from 'react';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useSim } from '../state/sim';
import { aggRows, serverRows } from '../lib/charts';
import { useTick } from '../lib/useTick';
import { fmtTimeMs } from '../lib/format';
import { BackendDown } from './Overview';
import { EmptyState, SectionTitle, Seg, Skeleton } from '../components/ui';

const SERVER_COLORS = ['#4F8CFF', '#22C55E', '#F59E0B', '#A78BFA', '#94A3B8', '#F472B6'];

function ChartTip({ active, payload, label }: any) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-panel2 border border-line2 rounded-[4px] px-2 py-1.5 font-mono text-[10px]">
      <div className="text-zinc-500 tnum mb-1">{fmtTimeMs(Number(label))}</div>
      {payload.map((p: any) => (
        <div key={String(p.dataKey)} className="tnum flex justify-between gap-3">
          <span style={{ color: p.color || p.stroke }}>{p.name}</span>
          <span className="text-zinc-200">{typeof p.value === 'number' ? p.value.toFixed(2) : p.value}</span>
        </div>
      ))}
    </div>
  );
}

function ChartShell({ title, unit, children, tip }: { title: string; unit?: string; children: React.ReactNode; tip?: string }) {
  return (
    <div className="border border-line rounded-[6px] bg-panel p-3">
      <div className="flex items-baseline justify-between mb-2">
        <h3 className={`text-[10px] font-semibold uppercase tracking-[0.12em] text-zinc-500 ${tip ? 'has-tip' : ''}`} data-tip={tip}>
          {title}
        </h3>
        {unit && <span className="font-mono text-[9px] text-zinc-600">{unit}</span>}
      </div>
      <div className="h-[170px]">{children}</div>
    </div>
  );
}

const axisStyle = { fontSize: 9, fill: '#52525b', fontFamily: 'ui-monospace, monospace' } as const;

export default function Metrics() {
  const { ready, backendUp, serverIds, simState } = useSim();
  const [windowSec, setWindowSec] = useState(60);
  const [paused, setPaused] = useState(false);
  const tick = useTick(1000, !paused && ready);

  const since = Date.now() - windowSec * 1000;
  // recompute on each tick render:
  const agg = useMemo(() => aggRows(since), [since, tick]);
  const cpu = useMemo(() => serverRows(serverIds, 'cpu', since), [serverIds, since, tick]);
  const gpu = useMemo(() => serverRows(serverIds, 'gpu', since), [serverIds, since, tick]);
  const sess = useMemo(() => serverRows(serverIds, 'sessions', since), [serverIds, since, tick]);

  if (!ready) return <div className="p-4"><Skeleton className="h-[400px]" /></div>;
  if (!backendUp) return <div className="p-4"><BackendDown /></div>;
  if (serverIds.length === 0)
    return (
      <div className="p-4">
        <EmptyState
          title="No metric history"
          hint={simState === 'STOPPED' ? 'Start the simulation to begin collecting metrics.' : 'Waiting for the first metric flush…'}
        />
      </div>
    );

  const lineFor = (id: string, i: number, name?: string) => (
    <Line
      key={id}
      type="monotone"
      dataKey={id}
      name={name ?? id}
      stroke={SERVER_COLORS[i % SERVER_COLORS.length]}
      strokeWidth={1.3}
      dot={false}
      isAnimationActive={false}
      connectNulls={false}
    />
  );

  /** Compact colour-keyed legend strip rendered below a per-server chart. */
  const ChartLegend = () => (
    <div className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 px-1">
      {serverIds.map((id, i) => (
        <span key={id} className="inline-flex items-center gap-1 font-mono text-[9px] text-zinc-500">
          <span className="inline-block w-2 h-0.5 rounded-[1px]" style={{ background: SERVER_COLORS[i % SERVER_COLORS.length] }} />
          {id}
        </span>
      ))}
    </div>
  );

  const xAxis = <XAxis dataKey="t" tickFormatter={(t: number) => new Date(t).toTimeString().slice(0, 8)} tick={axisStyle} tickLine={false} axisLine={{ stroke: '#1E242C' }} minTickGap={40} />;

  return (
    <div className="p-4">
      <div className="flex items-center gap-3 mb-3 flex-wrap">
        <h1 className="text-[13px] font-semibold text-zinc-200">Metrics</h1>
        <span className="font-mono text-[10px] text-zinc-600">
          rolling window · sourced from backend history + live ws flush
        </span>
        <div className="flex-1" />
        <Seg
          options={['60', '300']}
          value={String(windowSec)}
          onChange={(v) => setWindowSec(Number(v))}
          labels={{ 60: '60s', 300: '5m' }}
        />
        <button
          onClick={() => setPaused((p) => !p)}
          className={`h-6 px-2 font-mono text-[10px] border rounded-[4px] transition-colors ${
            paused ? 'border-warn/50 text-warn' : 'border-line2 text-zinc-400 hover:text-zinc-200'
          }`}
        >
          {paused ? 'PAUSED — resume' : 'pause live updates'}
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        <ChartShell title="Requests / sec" unit="req/s">
          <ResponsiveContainer>
            <LineChart data={agg} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
              <CartesianGrid stroke="#141a21" vertical={false} />
              {xAxis}
              <YAxis tick={axisStyle} tickLine={false} axisLine={false} width={44} />
              <Tooltip content={<ChartTip />} />
              <Line type="monotone" dataKey="requestsPerSec" name="req/s" stroke="#4F8CFF" strokeWidth={1.4} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartShell>

        <ChartShell title="Average latency" unit="ms" tip="Session-weighted mean latency across servers.">
          <ResponsiveContainer>
            <LineChart data={agg} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
              <CartesianGrid stroke="#141a21" vertical={false} />
              {xAxis}
              <YAxis tick={axisStyle} tickLine={false} axisLine={false} width={44} />
              <Tooltip content={<ChartTip />} />
              <Line type="monotone" dataKey="avgLatencyMs" name="avg ms" stroke="#F59E0B" strokeWidth={1.4} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartShell>

        <ChartShell title="CPU utilization by server" unit="%" tip="Per-server CPU; encoding + game threads.">
          <ResponsiveContainer>
            <LineChart data={cpu} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
              <CartesianGrid stroke="#141a21" vertical={false} />
              {xAxis}
              <YAxis tick={axisStyle} tickLine={false} axisLine={false} width={44} domain={[0, 100]} />
              <Tooltip content={<ChartTip />} />
              {serverIds.map((id, i) => lineFor(id, i))}
            </LineChart>
          </ResponsiveContainer>
          <ChartLegend />
        </ChartShell>

        <ChartShell title="GPU utilization by server" unit="%" tip="Per-server GPU frame-render utilization.">
          <ResponsiveContainer>
            <LineChart data={gpu} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
              <CartesianGrid stroke="#141a21" vertical={false} />
              {xAxis}
              <YAxis tick={axisStyle} tickLine={false} axisLine={false} width={44} domain={[0, 100]} />
              <Tooltip content={<ChartTip />} />
              {serverIds.map((id, i) => lineFor(id, i))}
            </LineChart>
          </ResponsiveContainer>
          <ChartLegend />
        </ChartShell>

        <ChartShell title="Active sessions by server" unit="sessions">
          <ResponsiveContainer>
            <LineChart data={sess} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
              <CartesianGrid stroke="#141a21" vertical={false} />
              {xAxis}
              <YAxis tick={axisStyle} tickLine={false} axisLine={false} width={44} allowDecimals={false} />
              <Tooltip content={<ChartTip />} />
              {serverIds.map((id, i) => lineFor(id, i))}
            </LineChart>
          </ResponsiveContainer>
          <ChartLegend />
        </ChartShell>

        <ChartShell title="Network throughput" unit="Mbps" tip="Sum of per-server egress bandwidth.">
          <ResponsiveContainer>
            <LineChart data={agg} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
              <CartesianGrid stroke="#141a21" vertical={false} />
              {xAxis}
              <YAxis tick={axisStyle} tickLine={false} axisLine={false} width={44} />
              <Tooltip content={<ChartTip />} />
              <Line type="monotone" dataKey="throughputMbps" name="Mbps" stroke="#22C55E" strokeWidth={1.4} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartShell>

        <ChartShell title="Packet loss" unit="%" tip="Session-weighted packet loss; >5% excludes a server from routing." >
          <ResponsiveContainer>
            <LineChart data={agg} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
              <CartesianGrid stroke="#141a21" vertical={false} />
              {xAxis}
              <YAxis tick={axisStyle} tickLine={false} axisLine={false} width={44} />
              <Tooltip content={<ChartTip />} />
              <Line type="monotone" dataKey="packetLoss" name="loss %" stroke="#EF4444" strokeWidth={1.4} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartShell>

        <div className="border border-line rounded-[6px] bg-panel p-3">
          <h3 className="text-[10px] font-semibold uppercase tracking-[0.12em] text-zinc-500 mb-2">
            Routing distribution
          </h3>
          <RoutingDist />
        </div>
      </div>
    </div>
  );
}

/** Share of recent routing decisions per server. */
function RoutingDist() {
  const { decisions, serverIds } = useSim();
  useTick(2000);
  const rows = useMemo(() => {
    // Assign colors from the stable serverIds array so they never shift between renders.
    const colorMap = new Map(serverIds.map((id, i) => [id, SERVER_COLORS[i % SERVER_COLORS.length]]));
    const counts = new Map<string, number>();
    for (const d of decisions.slice(0, 100)) {
      if (d.selectedServerId) counts.set(d.selectedServerId, (counts.get(d.selectedServerId) || 0) + 1);
    }
    const total = [...counts.values()].reduce((a, b) => a + b, 0) || 1;
    return [...counts.entries()]
      .map(([id, n]) => ({ id, n, pct: (n / total) * 100, color: colorMap.get(id) ?? '#6B7280' }))
      .sort((a, b) => b.n - a.n);
  }, [decisions, serverIds]);

  if (rows.length === 0) return <div className="font-mono text-[10px] text-zinc-600">no decisions yet</div>;
  return (
    <div className="space-y-2">
      {rows.map((r) => (
        <div key={r.id}>
          <div className="flex justify-between font-mono text-[10px]">
            <span className="text-zinc-300">{r.id}</span>
            <span className="text-zinc-500 tnum">{r.n} · {r.pct.toFixed(1)}%</span>
          </div>
          <div className="mt-1 h-[5px] bg-line rounded-[2px] overflow-hidden">
            <div className="h-full rounded-[2px]" style={{ width: r.pct + '%', background: r.color }} />
          </div>
        </div>
      ))}
      <div className="font-mono text-[9px] text-zinc-600">last {Math.min(100, decisions.length)} decisions</div>
    </div>
  );
}
