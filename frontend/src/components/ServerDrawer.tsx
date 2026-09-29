import { useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';
import { Line, LineChart, ResponsiveContainer, YAxis } from 'recharts';
import * as api from '../api';
import { useSim } from '../state/sim';
import { serverHistory } from '../lib/charts';
import { useTick } from '../lib/useTick';
import { fmt, fmtAgo, fmtMs, fmtPct, fmtTime } from '../lib/format';
import {
  Btn,
  CIRCUIT_COLOR,
  MetricBar,
  RMI_COLOR,
  SectionTitle,
  SERVER_STATE_COLOR,
  StateBadge,
} from './ui';
import { EventRows } from './EventTail';
import type { FaultType } from '../types';

const FAULTS: { type: FaultType; label: string; hint: string }[] = [
  { type: 'GPU_OVERLOAD', label: 'GPU Overload', hint: 'Drives GPU utilization toward saturation' },
  { type: 'LATENCY_SPIKE', label: 'Latency Spike', hint: 'Inflates round-trip latency on this node' },
  { type: 'PACKET_LOSS', label: 'Packet Loss', hint: 'Degrades the network path to this node' },
  { type: 'RMI_FAILURE', label: 'RMI Failure', hint: 'Game server stops answering RMI calls' },
  { type: 'CRASH', label: 'Crash Server', hint: 'Node goes offline immediately' },
];

const METRIC_TIPS: Record<string, string> = {
  CPU: 'Share of server CPU consumed by encoding + game threads.',
  GPU: 'Share of GPU used for rendering game frames.',
  RAM: 'System memory in use.',
  VRAM: 'GPU frame-buffer memory in use.',
  Encoding: 'Share of the hardware encoder pipeline (NVENC) busy compressing video.',
  Latency: 'Round-trip time from player edge to this game server.',
  Jitter: 'Variance of latency between consecutive packets; high jitter causes stutter.',
  'Packet loss': 'Share of packets never arriving; above 5% a server is unroutable.',
};

export default function ServerDrawer() {
  const {
    drawerServerId,
    openDrawer,
    servers,
    sessions,
    decisions,
    events,
    inspectDecision,
  } = useSim();
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const tick = useTick(1000, !!drawerServerId);

  const server = drawerServerId ? servers[drawerServerId] : undefined;

  const serverSessions = useMemo(
    () => (server ? sessions.filter((s) => s.serverId === server.id).slice(0, 10) : []),
    [sessions, server],
  );
  const serverDecisions = useMemo(
    () =>
      server
        ? decisions
            .filter(
              (d) => d.selectedServerId === server.id || d.candidates.some((c) => c.serverId === server.id),
            )
            .slice(0, 6)
        : [],
    [decisions, server],
  );
  const serverEvents = useMemo(
    () => (server ? events.filter((e) => e.serverId === server.id).slice(0, 8) : []),
    [events, server],
  );
  const spark = useMemo(() => {
    if (!server) return [];
    return (serverHistory.get(server.id) || []).slice(-90).map((p) => ({ t: p.t, cpu: p.cpu, gpu: p.gpu }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [server, tick]);

  const inject = async (type: FaultType) => {
    if (!server) return;
    setBusy(type);
    setErr(null);
    try {
      await api.injectFault(server.id, type);
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
      await api.recoverServer(server.id);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Recover failed');
    } finally {
      setBusy(null);
    }
  };

  return (
    <AnimatePresence>
      {server && (
        <>
          <motion.div
            className="fixed inset-0 bg-black/50 z-40"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => openDrawer(null)}
          />
          <motion.aside
            className="fixed top-0 right-0 bottom-0 w-[400px] max-w-[92vw] bg-panel border-l border-line z-50 flex flex-col"
            initial={{ x: 420 }}
            animate={{ x: 0 }}
            exit={{ x: 420 }}
            transition={{ type: 'tween', duration: 0.18 }}
            role="dialog"
            aria-label={'Server details for ' + server.id}
          >
            {/* header */}
            <div className="px-4 py-3 border-b border-line flex items-start justify-between shrink-0">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-[14px] font-bold text-zinc-100">{server.id}</span>
                  <StateBadge color={SERVER_STATE_COLOR[server.state]} label={server.state} pulse={server.state === 'RECOVERING'} />
                </div>
                <div className="font-mono text-[10px] text-zinc-500 mt-0.5">
                  {server.city} · {server.region} · capacity {server.capacity}
                </div>
              </div>
              <button
                onClick={() => openDrawer(null)}
                className="p-1.5 text-zinc-500 hover:text-zinc-200 rounded-[4px]"
                aria-label="Close server details"
              >
                <X size={15} />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-4 py-3 space-y-5">
              {/* status grid */}
              <div className="grid grid-cols-3 gap-px bg-line border border-line rounded-[6px] overflow-hidden">
                {[
                  { k: 'RMI', v: <StateBadge color={RMI_COLOR[server.rmiStatus]} label={server.rmiStatus} /> },
                  { k: 'CIRCUIT', v: <StateBadge color={CIRCUIT_COLOR[server.circuitState]} label={server.circuitState} /> },
                  {
                    k: 'ELIGIBLE',
                    v: (
                      <span className="font-mono text-[10px]" style={{ color: server.eligible ? '#22C55E' : '#F59E0B' }}>
                        {server.eligible ? 'YES' : 'NO'}
                      </span>
                    ),
                  },
                ].map(({ k, v }) => (
                  <div key={k} className="bg-panel px-2.5 py-2">
                    <div className="text-[9px] uppercase tracking-[0.1em] text-zinc-600">{k}</div>
                    <div className="mt-1">{v}</div>
                  </div>
                ))}
              </div>

              {/* live metrics */}
              <section>
                <SectionTitle>Live metrics</SectionTitle>
                <div className="space-y-2.5">
                  <MetricBar label="CPU" value={server.metrics.cpu} tip={METRIC_TIPS.CPU} />
                  <MetricBar label="GPU" value={server.metrics.gpu} tip={METRIC_TIPS.GPU} />
                  <MetricBar label="RAM" value={server.metrics.ram} tip={METRIC_TIPS.RAM} />
                  <MetricBar label="VRAM" value={server.metrics.vram} tip={METRIC_TIPS.VRAM} />
                  <MetricBar label="Encoding" value={server.metrics.encoding} tip={METRIC_TIPS.Encoding} />
                  <div className="grid grid-cols-2 gap-x-4 gap-y-2.5 pt-1">
                    <Mini label="LATENCY" value={fmtMs(server.metrics.latencyMs)} tip={METRIC_TIPS.Latency} bad={server.metrics.latencyMs > 100} warn={server.metrics.latencyMs > 50} />
                    <Mini label="JITTER" value={fmtMs(server.metrics.jitterMs)} tip={METRIC_TIPS.Jitter} warn={server.metrics.jitterMs > 10} />
                    <Mini label="PACKET LOSS" value={fmtPct(server.metrics.packetLoss)} tip={METRIC_TIPS['Packet loss']} bad={server.metrics.packetLoss > 5} warn={server.metrics.packetLoss > 1} />
                    <Mini label="TEMP" value={fmt(server.metrics.temperatureC, 0) + '°C'} bad={server.metrics.temperatureC > 85} warn={server.metrics.temperatureC > 75} />
                    <Mini label="SESSIONS" value={`${server.metrics.sessions} / ${server.capacity}`} />
                    <Mini label="REQ/S" value={fmt(server.metrics.requestsPerSec)} />
                    <Mini label="BANDWIDTH" value={fmt(server.metrics.networkMbps, 0) + ' Mbps'} />
                    <Mini label="WEIGHT" value={fmt(server.weight, 2)} />
                  </div>
                </div>
              </section>

              {/* sparkline */}
              <section>
                <SectionTitle>CPU / GPU — last 90s</SectionTitle>
                <div className="h-[70px] border border-line rounded-[4px] bg-ink px-1">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={spark} margin={{ top: 6, right: 6, bottom: 2, left: 0 }}>
                      <YAxis hide domain={[0, 100]} />
                      <Line type="monotone" dataKey="cpu" stroke="#4F8CFF" strokeWidth={1.2} dot={false} isAnimationActive={false} />
                      <Line type="monotone" dataKey="gpu" stroke="#22C55E" strokeWidth={1.2} dot={false} isAnimationActive={false} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
                <div className="mt-1 flex gap-3 font-mono text-[9px] text-zinc-500">
                  <span><span className="text-info">—</span> CPU</span>
                  <span><span className="text-ok">—</span> GPU</span>
                </div>
              </section>

              {/* sessions */}
              <section>
                <SectionTitle right={<span className="font-mono text-[10px] text-zinc-600">{serverSessions.length}</span>}>
                  Active sessions
                </SectionTitle>
                {serverSessions.length === 0 ? (
                  <div className="text-[11px] text-zinc-600">No active sessions on this server.</div>
                ) : (
                  <div className="border border-line rounded-[4px] divide-y divide-line">
                    {serverSessions.map((s) => (
                      <div key={s.id} className="px-2 py-1.5 flex items-center justify-between font-mono text-[10px]">
                        <span className="text-zinc-300">{s.id} <span className="text-zinc-600">{s.playerId}</span></span>
                        <span className="text-zinc-500 tnum">{s.game}</span>
                        <span className="text-zinc-500 tnum">{fmtMs(s.latencyMs, 0)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              {/* recent routing decisions */}
              <section>
                <SectionTitle>Recent routing decisions</SectionTitle>
                {serverDecisions.length === 0 ? (
                  <div className="text-[11px] text-zinc-600">None yet.</div>
                ) : (
                  <div className="space-y-1">
                    {serverDecisions.map((d) => {
                      const cand = d.candidates.find((c) => c.serverId === server.id);
                      const selected = d.selectedServerId === server.id;
                      return (
                        <button
                          key={d.id}
                          onClick={() => inspectDecision(d)}
                          className="w-full text-left px-2 py-1.5 border border-line rounded-[4px] hover:border-line2 hover:bg-panel2 transition-colors"
                        >
                          <div className="flex items-center justify-between font-mono text-[10px]">
                            <span className="text-zinc-400">{d.id}</span>
                            <span className={selected ? 'text-ok' : cand?.eligible ? 'text-zinc-500' : 'text-warn'}>
                              {selected ? 'SELECTED' : cand?.eligible ? 'candidate' : 'rejected'}
                            </span>
                          </div>
                          <div className="font-mono text-[10px] text-zinc-500 mt-0.5 tnum">
                            {d.playerId} · score {cand ? fmt(cand.score) : '—'}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                )}
              </section>

              {/* recent events */}
              <section>
                <SectionTitle>Recent events</SectionTitle>
                {serverEvents.length === 0 ? (
                  <div className="text-[11px] text-zinc-600">None yet.</div>
                ) : (
                  <div className="border border-line rounded-[4px] overflow-hidden">
                    <EventRows events={serverEvents} />
                  </div>
                )}
              </section>

              {/* fault injection */}
              <section>
                <SectionTitle>Fault injection</SectionTitle>
                {err && <div className="mb-2 font-mono text-[10px] text-err">{err}</div>}
                <div className="grid grid-cols-2 gap-1.5">
                  {FAULTS.map((f) => (
                    <button
                      key={f.type}
                      disabled={busy !== null}
                      onClick={() => inject(f.type)}
                      title={f.hint}
                      className="has-tip text-left px-2 py-1.5 border border-line2 rounded-[4px] font-mono text-[10px] text-zinc-400 hover:text-err hover:border-err/50 disabled:opacity-40 transition-colors"
                      data-tip={f.hint}
                    >
                      {busy === f.type ? '…' : f.label}
                    </button>
                  ))}
                </div>
                <div className="mt-1.5">
                  <Btn accent="green" onClick={recover} disabled={busy !== null}>
                    {busy === 'RECOVER' ? '…' : 'Recover server'}
                  </Btn>
                </div>
                <div className="mt-1 font-mono text-[9px] text-zinc-600">
                  updated {fmtAgo(server.metrics.timestamp, Date.now())} · {fmtTime(server.metrics.timestamp)}
                </div>
              </section>
            </div>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}

function Mini({ label, value, tip, bad, warn }: { label: string; value: string; tip?: string; bad?: boolean; warn?: boolean }) {
  const color = bad ? '#EF4444' : warn ? '#F59E0B' : '#d4d4d8';
  return (
    <div className={tip ? 'has-tip' : ''} data-tip={tip}>
      <div className="text-[9px] uppercase tracking-[0.1em] text-zinc-600">{label}</div>
      <div className="font-mono text-[12px] tnum mt-0.5" style={{ color }}>{value}</div>
    </div>
  );
}

