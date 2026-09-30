import { useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { AlertTriangle, ChevronDown, X } from 'lucide-react';
import { useSim } from '../state/sim';
import { fmt, fmtTimeMs } from '../lib/format';
import { SectionTitle } from './ui';
import type { CandidateScore, RoutingDecision } from '../types';

const BREAKDOWN_ROWS: { key: string; label: string; color: string }[] = [
  { key: 'latency', label: 'Latency', color: '#4F8CFF' },
  { key: 'costPerHour', label: 'Cost ($/hr)', color: '#38BDF8' },
  { key: 'slaPenalty', label: 'SLA Penalty (>60ms)', color: '#FB923C' },
  { key: 'cpu', label: 'CPU × 0.20', color: '#22C55E' },
  { key: 'gpu', label: 'GPU × 0.20', color: '#A78BFA' },
  { key: 'packetLoss', label: 'Packet loss × 0.10', color: '#F59E0B' },
  { key: 'sessions', label: 'Sessions × 0.10', color: '#94A3B8' },
  { key: 'jitter', label: 'Jitter × 0.05', color: '#64748B' },
  { key: 'penalty', label: 'Penalty', color: '#EF4444' },
];

function Candidate({ c, selected, open, globalMax, onToggle }: { c: CandidateScore; selected: boolean; open: boolean; globalMax: number; onToggle: () => void }) {
  return (
    <div
      className={`border rounded-[4px] transition-colors ${
        selected ? 'border-ok/50 bg-ok/[0.04]' : c.eligible ? 'border-line' : 'border-warn/30'
      }`}
    >
      <button onClick={onToggle} className="w-full text-left px-2.5 py-2 flex items-center gap-2">
        <ChevronDown size={13} className={`text-zinc-500 transition-transform ${open ? '' : '-rotate-90'}`} />
        <span className="font-mono text-[11px] font-bold text-zinc-200">{c.serverId}</span>
        <span
          className="font-mono text-[10px]"
          style={{ color: selected ? '#22C55E' : c.eligible ? '#a1a1aa' : '#F59E0B' }}
        >
          {selected ? 'SELECTED' : c.eligible ? 'eligible' : 'INELIGIBLE'}
        </span>
        <span className="flex-1" />
        <span className="font-mono text-[12px] tnum text-zinc-100">{fmt(c.score)}</span>
      </button>

      <div className="px-2.5 pb-2 grid grid-cols-3 gap-x-4 gap-y-1 font-mono text-[10px] tnum text-zinc-500">
        <span>latency <b className="text-zinc-300 font-normal">{fmt(c.latencyMs)} ms</b></span>
        <span>cpu <b className="text-zinc-300 font-normal">{fmt(c.cpu)}%</b></span>
        <span>gpu <b className="text-zinc-300 font-normal">{fmt(c.gpu)}%</b></span>
        <span>loss <b className="text-zinc-300 font-normal">{fmt(c.packetLoss)}%</b></span>
        <span>sessions <b className="text-zinc-300 font-normal">{c.sessions}</b></span>
        <span>jitter <b className="text-zinc-300 font-normal">{fmt(c.jitterMs)} ms</b></span>
      </div>

      {!c.eligible && c.penaltyReason && (
        <div className="mx-2.5 mb-2 px-2 py-1 bg-warn/10 border border-warn/30 rounded-[4px] font-mono text-[10px] text-warn flex items-center gap-1.5">
          <AlertTriangle size={11} /> {c.penaltyReason}
        </div>
      )}

      {open && (
        <div className="px-2.5 pb-2.5 pt-1 border-t border-line">
          <div className="text-[9px] uppercase tracking-[0.1em] text-zinc-600 mb-1.5">
            Score breakdown — weighted contributions (lower is better)
          </div>
          <div className="space-y-1.5">
            {BREAKDOWN_ROWS.filter(({ key }) => c.scoreBreakdown[key] !== undefined).map(({ key, label, color }) => {
              const v = c.scoreBreakdown[key] ?? 0;
              return (
                <div key={key} className="flex items-center gap-2">
                  <span className="font-mono text-[10px] text-zinc-500 w-[124px] truncate">{label}</span>
                  <div className="flex-1 h-[4px] bg-line rounded-[2px] overflow-hidden">
                    <div className="h-full rounded-[2px]" style={{ width: Math.min(100, (v / (globalMax || 1)) * 100) + '%', background: color }} />
                  </div>
                  <span className="font-mono text-[10px] text-zinc-300 w-10 text-right tnum">{fmt(v)}</span>
                </div>
              );
            })}
          </div>
          <div className="mt-1.5 pt-1.5 border-t border-line flex justify-between font-mono text-[10px]">
            <span className="text-zinc-500">total score</span>
            <span className="text-zinc-100 tnum">{fmt(c.score)}</span>
          </div>
        </div>
      )}
    </div>
  );
}

/** Global modal: full inspection of one routing decision. */
export default function DecisionInspector() {
  const { inspectedDecision, inspectDecision } = useSim();
  const [openId, setOpenId] = useState<string | null>(null);

  const d: RoutingDecision | null = inspectedDecision;
  const sorted = useMemo(
    () => (d ? [...d.candidates].sort((a, b) => a.score - b.score) : []),
    [d],
  );
  // Global max across ALL candidates and ALL breakdown keys so bars are comparable.
  const globalMax = useMemo(
    () => Math.max(1, ...(d?.candidates ?? []).flatMap((c) => BREAKDOWN_ROWS.map((r) => c.scoreBreakdown[r.key] ?? 0))),
    [d],
  );

  return (
    <AnimatePresence>
      {d && (
        <>
          <motion.div
            className="fixed inset-0 bg-black/60 z-[60]"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => inspectDecision(null)}
          />
          <motion.div
            className="fixed inset-0 z-[61] flex items-center justify-center p-6 pointer-events-none"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 8 }}
            transition={{ duration: 0.15 }}
          >
            <div
              className="pointer-events-auto w-[720px] max-w-full max-h-[86vh] bg-panel border border-line2 rounded-[6px] flex flex-col"
              role="dialog"
              aria-label={'Routing decision ' + d.id}
            >
              <div className="px-4 py-3 border-b border-line flex items-start justify-between shrink-0">
                <div>
                  <div className="font-mono text-[13px] font-bold text-zinc-100">
                    ROUTING DECISION <span className="text-info">{d.id}</span>
                  </div>
                  <div className="font-mono text-[10px] text-zinc-500 mt-0.5 tnum">
                    {fmtTimeMs(d.timestamp)} · strategy {d.strategy}
                  </div>
                </div>
                <button
                  onClick={() => inspectDecision(null)}
                  className="p-1.5 text-zinc-500 hover:text-zinc-200 rounded-[4px]"
                  aria-label="Close decision inspector"
                >
                  <X size={15} />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4">
                {/* request summary */}
                <div className="grid grid-cols-4 gap-px bg-line border border-line rounded-[6px] overflow-hidden">
                  {[
                    ['Player', d.playerId],
                    ['Game', d.game],
                    ['Region', d.playerRegion],
                    ['Target', `${d.resolution} / ${d.fps} FPS`],
                  ].map(([k, v]) => (
                    <div key={k} className="bg-panel px-2.5 py-2">
                      <div className="text-[9px] uppercase tracking-[0.1em] text-zinc-600">{k}</div>
                      <div className="font-mono text-[11px] text-zinc-200 mt-0.5 truncate">{v}</div>
                    </div>
                  ))}
                </div>

                {/* candidates */}
                <section>
                  <SectionTitle>Candidates — scored lowest-first</SectionTitle>
                  <div className="space-y-1.5">
                    {sorted.map((c) => (
                      <Candidate
                        key={c.serverId}
                        c={c}
                        selected={d.selectedServerId === c.serverId}
                        open={openId === c.serverId}
                        globalMax={globalMax}
                        onToggle={() => setOpenId(openId === c.serverId ? null : c.serverId)}
                      />
                    ))}
                  </div>
                </section>

                {/* verdict */}
                <section className={`border rounded-[6px] px-3 py-2.5 ${d.selectedServerId ? 'border-ok/40 bg-ok/[0.05]' : 'border-err/40 bg-err/[0.05]'}`}>
                  <div className="text-[9px] uppercase tracking-[0.12em] text-zinc-500">Selected</div>
                  {d.selectedServerId ? (
                    <>
                      <div className="font-mono text-[15px] font-bold text-ok mt-0.5">{d.selectedServerId}</div>
                      <div className="text-[11px] text-zinc-400 mt-1">{d.reason}</div>
                    </>
                  ) : (
                    <>
                      <div className="font-mono text-[15px] font-bold text-err mt-0.5">NO ELIGIBLE CANDIDATE</div>
                      <div className="text-[11px] text-zinc-400 mt-1">
                        {d.reason || 'All servers were ineligible — the request could not be placed.'}
                      </div>
                    </>
                  )}
                </section>
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
