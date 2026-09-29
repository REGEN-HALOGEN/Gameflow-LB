import { useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import SessionPanel from '../components/SessionPanel';
import { useSim } from '../state/sim';
import { fmtDuration, fmtMs, fmtTime } from '../lib/format';
import { BackendDown } from './Overview';
import { EmptyState, Seg, SESSION_STATE_COLOR, Skeleton, StateBadge } from '../components/ui';
import type { GameSession, SessionState } from '../types';

const STATE_FILTERS: ('ALL' | SessionState)[] = ['ALL', 'ACTIVE', 'CREATING', 'MIGRATING', 'TERMINATING', 'FAILED'];

export default function Sessions() {
  const { ready, backendUp, sessions, serverIds, openDrawer } = useSim();
  const [search, setSearch] = useState('');
  const [stateFilter, setStateFilter] = useState<'ALL' | SessionState>('ALL');
  const [serverFilter, setServerFilter] = useState('ALL');
  const [selected, setSelected] = useState<GameSession | null>(null);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return sessions.filter((s) => {
      if (stateFilter !== 'ALL' && s.state !== stateFilter) return false;
      if (serverFilter !== 'ALL' && s.serverId !== serverFilter) return false;
      if (q && !s.id.toLowerCase().includes(q) && !s.playerId.toLowerCase().includes(q) && !s.game.toLowerCase().includes(q))
        return false;
      return true;
    });
  }, [sessions, search, stateFilter, serverFilter]);

  if (!ready) return <div className="p-4"><Skeleton className="h-[400px]" /></div>;
  if (!backendUp) return <div className="p-4"><BackendDown /></div>;

  return (
    <div className="p-4 flex flex-col h-full">
      <div className="flex items-center gap-3 mb-3 shrink-0 flex-wrap">
        <h1 className="text-[13px] font-semibold text-zinc-200">Sessions</h1>
        <span className="font-mono text-[10px] text-zinc-600 tnum">
          {filtered.length} / {sessions.length} shown
        </span>
        <div className="flex-1" />
        <div className="relative">
          <Search size={12} className="absolute left-2 top-1/2 -translate-y-1/2 text-zinc-600" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="search id, player, game…"
            className="h-7 pl-7 pr-2 w-[220px] bg-panel2 border border-line2 rounded-[4px] font-mono text-[11px] text-zinc-200 placeholder:text-zinc-600"
            aria-label="Search sessions"
          />
        </div>
        <select
          value={serverFilter}
          onChange={(e) => setServerFilter(e.target.value)}
          className="h-7 bg-panel2 border border-line2 rounded-[4px] font-mono text-[10px] text-zinc-400 px-1.5"
          aria-label="Filter by server"
        >
          <option value="ALL">all servers</option>
          {serverIds.map((id) => (
            <option key={id} value={id}>{id}</option>
          ))}
        </select>
        <Seg options={STATE_FILTERS} value={stateFilter} onChange={setStateFilter} />
      </div>

      {filtered.length === 0 ? (
        <EmptyState
          title={sessions.length === 0 ? 'No active sessions' : 'No sessions match the filters'}
          hint={sessions.length === 0 ? 'Start the simulation — players will arrive and the routing engine will place them.' : 'Clear the search or widen the filters.'}
        />
      ) : (
        <div className="flex-1 min-h-0 border border-line rounded-[6px] overflow-auto">
          <table className="w-full text-left border-collapse">
            <thead className="sticky top-0 bg-panel z-10">
              <tr className="border-b border-line font-mono text-[9px] uppercase tracking-[0.1em] text-zinc-600">
                {['Session', 'Player', 'Game', 'Server', 'Latency', 'FPS', 'Duration', 'State'].map((h) => (
                  <th key={h} className="px-3 py-2 font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="font-mono text-[11px]">
              {filtered.slice(0, 300).map((s) => (
                <tr
                  key={s.id}
                  onClick={() => setSelected(s)}
                  className="border-b border-line/60 hover:bg-panel2 cursor-pointer transition-colors"
                  tabIndex={0}
                  onKeyDown={(e) => e.key === 'Enter' && setSelected(s)}
                >
                  <td className="px-3 py-[7px] text-zinc-200">{s.id}</td>
                  <td className="px-3 py-[7px] text-zinc-400">{s.playerId}</td>
                  <td className="px-3 py-[7px] text-zinc-400">{s.game}</td>
                  <td className="px-3 py-[7px] text-info">{s.serverId}</td>
                  <td className="px-3 py-[7px] text-zinc-400 tnum">{fmtMs(s.latencyMs, 0)}</td>
                  <td className="px-3 py-[7px] text-zinc-400 tnum">{s.fps}</td>
                  <td className="px-3 py-[7px] text-zinc-400 tnum" title={'started ' + fmtTime(s.startTime)}>
                    {fmtDuration(s.durationSec)}
                  </td>
                  <td className="px-3 py-[7px]">
                    <StateBadge color={SESSION_STATE_COLOR[s.state]} label={s.state} pulse={s.state === 'MIGRATING'} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filtered.length > 300 && (
            <div className="px-3 py-2 font-mono text-[10px] text-zinc-600">
              showing first 300 of {filtered.length}
            </div>
          )}
        </div>
      )}

      <SessionPanel
        session={selected ? (sessions.find((s) => s.id === selected.id) ?? null) : null}
        onClose={() => setSelected(null)}
        onOpenServer={(id) => { setSelected(null); openDrawer(id); }}
      />
    </div>
  );
}
