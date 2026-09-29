import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';
import { useState } from 'react';
import * as api from '../api';
import { useSim } from '../state/sim';
import { useTick } from '../lib/useTick';
import { fmtDuration, fmtMs, fmtTime } from '../lib/format';
import { SESSION_STATE_COLOR, StateBadge } from './ui';
import type { GameSession } from '../types';

/** Right-side panel with full session detail. */
export default function SessionPanel({
  session,
  onClose,
  onOpenServer,
}: {
  session: GameSession | null;
  onClose: () => void;
  onOpenServer: (id: string) => void;
}) {
  const { games } = useSim();
  const [terminating, setTerminating] = useState(false);
  const [termErr, setTermErr] = useState<string | null>(null);
  useTick(1000, !!session);
  const game = games.find((g) => g.name === session?.game || g.id === session?.game);

  const terminate = async () => {
    if (!session || terminating) return;
    if (!window.confirm(`Terminate session ${session.id} (${session.playerId})?`)) return;
    setTerminating(true);
    setTermErr(null);
    try {
      await api.terminateSession(session.id);
      onClose(); // SESSION_TERMINATED also arrives over WS and removes it
    } catch (e) {
      setTermErr(e instanceof Error ? e.message : 'Terminate failed');
    } finally {
      setTerminating(false);
    }
  };

  return (
    <AnimatePresence>
      {session && (
        <>
          <motion.div
            className="fixed inset-0 bg-black/50 z-40"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />
          <motion.aside
            className="fixed top-0 right-0 bottom-0 w-[360px] max-w-[92vw] bg-panel border-l border-line z-50 flex flex-col"
            initial={{ x: 380 }}
            animate={{ x: 0 }}
            exit={{ x: 380 }}
            transition={{ type: 'tween', duration: 0.18 }}
            role="dialog"
            aria-label={'Session ' + session.id}
          >
            <div className="px-4 py-3 border-b border-line flex items-start justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-[14px] font-bold text-zinc-100">{session.id}</span>
                  <StateBadge
                    color={SESSION_STATE_COLOR[session.state]}
                    label={session.state}
                    pulse={session.state === 'MIGRATING' || session.state === 'CREATING'}
                  />
                </div>
                <div className="font-mono text-[10px] text-zinc-500 mt-0.5">
                  player {session.playerId} · {session.playerRegion}
                </div>
              </div>
              <button
                onClick={onClose}
                className="p-1.5 text-zinc-500 hover:text-zinc-200 rounded-[4px]"
                aria-label="Close session details"
              >
                <X size={15} />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4">
              <div className="grid grid-cols-2 gap-px bg-line border border-line rounded-[6px] overflow-hidden">
                {[
                  ['Game', session.game],
                  ['Server', session.serverId],
                  ['Resolution', session.resolution],
                  ['FPS target', String(session.fps)],
                  ['Latency', fmtMs(session.latencyMs)],
                  ['Duration', fmtDuration(session.durationSec)],
                  ['Started', fmtTime(session.startTime)],
                  ['Genre', game?.genre ?? '—'],
                ].map(([k, v]) => (
                  <div key={k} className="bg-panel px-2.5 py-2">
                    <div className="text-[9px] uppercase tracking-[0.1em] text-zinc-600">{k}</div>
                    <div className="font-mono text-[11px] text-zinc-200 mt-0.5 truncate">{v}</div>
                  </div>
                ))}
              </div>

              {game && (
                <div>
                  <div className="text-[10px] font-semibold uppercase tracking-[0.12em] text-zinc-500 mb-2">
                    Workload profile
                  </div>
                  <div className="space-y-2">
                    {[
                      ['GPU load', game.gpuLoad],
                      ['VRAM load', game.vramLoad],
                      ['Network load', game.networkLoad],
                      ['CPU load', game.cpuLoad],
                    ].map(([k, v]) => (
                      <div key={k as string}>
                        <div className="flex justify-between text-[10px] font-mono text-zinc-500">
                          <span className="uppercase tracking-[0.08em]">{k}</span>
                          <span className="tnum">{Math.round((v as number) * 100)}%</span>
                        </div>
                        <div className="mt-1 h-[3px] bg-line rounded-[2px] overflow-hidden">
                          <div className="h-full bg-info rounded-[2px]" style={{ width: (v as number) * 100 + '%' }} />
                        </div>
                      </div>
                    ))}
                  </div>
                  {game.latencySensitive && (
                    <div className="mt-2 font-mono text-[10px] text-info">LATENCY-SENSITIVE workload</div>
                  )}
                </div>
              )}

              <button
                onClick={() => onOpenServer(session.serverId)}
                disabled={session.state === 'MIGRATING'}
                className="w-full text-left px-2.5 py-2 border border-line2 rounded-[4px] font-mono text-[11px] text-zinc-300 hover:border-info/50 hover:text-info disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                title={session.state === 'MIGRATING' ? 'Server assignment in progress…' : undefined}
              >
                Open server {session.serverId} →
              </button>

              {session.state === 'MIGRATING' && (
                <div className="border border-info/40 bg-info/10 rounded-[6px] px-3 py-2.5 font-mono text-[11px] text-info">
                  MIGRATING — routing engine is selecting a replacement node…
                </div>
              )}

              {session.state !== 'TERMINATING' && (
                <button
                  onClick={terminate}
                  disabled={terminating}
                  className="w-full text-center px-2.5 py-2 border border-err/40 rounded-[4px] font-mono text-[11px] text-err hover:bg-err/10 disabled:opacity-40 transition-colors"
                >
                  {terminating ? 'terminating…' : 'Terminate session'}
                </button>
              )}
              {termErr && <div className="font-mono text-[10px] text-err">{termErr}</div>}
            </div>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}
