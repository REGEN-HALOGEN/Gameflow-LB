import { useEffect, useState } from 'react';
import { Pause, Play, RotateCcw, Square } from 'lucide-react';
import * as api from '../api';
import { useSim } from '../state/sim';
import { fmtTime } from '../lib/format';
import { SIM_STATE_COLOR, StateBadge, WS_COLOR } from './ui';

const SPEEDS = [0.5, 1, 2, 5];

export default function TopBar() {
  const { wsStatus, backendUp, simState, speed } = useSim();
  const [now, setNow] = useState(Date.now());
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const run = async (name: string, fn: () => Promise<unknown>) => {
    setBusy(name);
    try {
      await fn();
    } catch {
      /* errors surface via events / banners */
    } finally {
      setBusy(null);
    }
  };

  const ctl =
    'inline-flex items-center gap-1 font-mono text-[10px] px-2 h-7 rounded-[4px] border border-line2 bg-panel2 text-zinc-400 hover:text-zinc-100 hover:bg-[#1a212b] disabled:opacity-40 disabled:cursor-not-allowed transition-colors';

  return (
    <header className="h-12 shrink-0 flex items-center gap-4 px-4 bg-panel border-b border-line">
      {/* wordmark */}
      <div className="flex items-center gap-2 select-none">
        <span className="font-mono text-[13px] font-bold tracking-[0.08em] text-zinc-100">
          GAMEFLOW<span className="text-info"> LB</span>
        </span>
        <span className="hidden xl:inline font-mono text-[10px] text-zinc-600">
          cloud-gaming load balancer
        </span>
      </div>

      <div className="w-px h-5 bg-line" />

      {/* system status */}
      <StateBadge
        color={backendUp ? '#22C55E' : '#EF4444'}
        label={backendUp ? 'SYSTEM UP' : 'BACKEND DOWN'}
        pulse={!backendUp}
      />

      {/* simulation state + speed */}
      <div className="flex items-center gap-2">
        <StateBadge
          color={SIM_STATE_COLOR[simState]}
          label={simState}
          pulse={simState === 'RUNNING'}
        />
        <span className="font-mono text-[10px] text-zinc-500 tnum">{speed}x</span>
      </div>

      <div className="flex-1" />

      {/* clock */}
      <span className="hidden md:inline font-mono text-[11px] text-zinc-500 tnum">{fmtTime(now)}</span>

      {/* ws status */}
      <StateBadge color={WS_COLOR[wsStatus]} label={'WS ' + wsStatus} pulse={wsStatus !== 'CONNECTED'} />

      <div className="w-px h-5 bg-line" />

      {/* global controls */}
      <div className="flex items-center gap-1.5">
        {simState === 'STOPPED' && (
          <button className={ctl} disabled={!backendUp || busy !== null} onClick={() => run('start', api.simStart)} title="Start simulation">
            <Play size={11} /> {busy === 'start' ? '…' : 'START'}
          </button>
        )}
        {simState === 'RUNNING' && (
          <button className={ctl} disabled={!backendUp || busy !== null} onClick={() => run('pause', api.simPause)} title="Pause simulation">
            <Pause size={11} /> {busy === 'pause' ? '…' : 'PAUSE'}
          </button>
        )}
        {simState === 'PAUSED' && (
          <button className={ctl} disabled={!backendUp || busy !== null} onClick={() => run('resume', api.simResume)} title="Resume simulation">
            <Play size={11} /> {busy === 'resume' ? '…' : 'RESUME'}
          </button>
        )}
        {simState !== 'STOPPED' && (
          <button className={ctl} disabled={!backendUp || busy !== null} onClick={() => run('reset', api.simReset)} title="Reset simulation">
            {simState === 'RUNNING' ? <Square size={11} /> : <RotateCcw size={11} />} {busy === 'reset' ? '…' : 'RESET'}
          </button>
        )}
        <select
          className="h-7 bg-panel2 border border-line2 rounded-[4px] font-mono text-[10px] text-zinc-400 px-1.5 disabled:opacity-40"
          value={speed}
          disabled={!backendUp || busy !== null}
          onChange={(e) => run('speed', () => api.simSpeed(parseFloat(e.target.value)))}
          title="Simulation speed"
          aria-label="Simulation speed"
        >
          {SPEEDS.map((s) => (
            <option key={s} value={s}>
              {s}x
            </option>
          ))}
        </select>
      </div>
    </header>
  );
}
