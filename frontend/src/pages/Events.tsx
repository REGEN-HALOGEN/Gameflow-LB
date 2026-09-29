import { useEffect, useMemo, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { useSim } from '../state/sim';
import { fmtTimeMs } from '../lib/format';
import { BackendDown } from './Overview';
import { SEVERITY_COLOR, Seg, Skeleton } from '../components/ui';
import type { EventSeverity, SystemEvent } from '../types';

const SEVS: ('ALL' | EventSeverity)[] = ['ALL', 'INFO', 'WARN', 'ERROR', 'CRITICAL'];

function EventRow({ e }: { e: SystemEvent }) {
  return (
    <div className="flex gap-2.5 px-3 py-[3px] hover:bg-panel2 border-b border-line/40 font-mono text-[11px] leading-relaxed">
      <span className="text-zinc-600 tnum shrink-0">{fmtTimeMs(e.timestamp)}</span>
      <span className="shrink-0 font-semibold w-[104px]" style={{ color: SEVERITY_COLOR[e.severity] }}>
        {e.severity}
      </span>
      <span className="shrink-0 text-zinc-300 w-[150px] truncate" title={e.type}>{e.type}</span>
      <span className="text-zinc-400 break-all">{e.message}</span>
      {e.serverId && <span className="text-zinc-600 shrink-0 ml-auto">{e.serverId}</span>}
    </div>
  );
}

export default function Events() {
  const { ready, backendUp, events } = useSim();
  const [sev, setSev] = useState<'ALL' | EventSeverity>('ALL');
  const [query, setQuery] = useState('');
  const [autoScroll, setAutoScroll] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);
  const prevEventsLen = useRef(0);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    // oldest-first for log feel
    const asc = [...events].reverse();
    return asc.filter((e) => {
      if (sev !== 'ALL' && e.severity !== sev) return false;
      if (q && !e.type.toLowerCase().includes(q) && !e.message.toLowerCase().includes(q)) return false;
      return true;
    }).slice(-400);
  }, [events, sev, query]);

  useEffect(() => {
    // Only scroll when genuinely new events arrive, not on filter changes.
    if (events.length === prevEventsLen.current) return;
    prevEventsLen.current = events.length;
    if (autoScroll && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [events.length, autoScroll]);

  if (!ready) return <div className="p-4"><Skeleton className="h-[400px]" /></div>;
  if (!backendUp) return <div className="p-4"><BackendDown /></div>;

  return (
    <div className="p-4 flex flex-col h-full">
      <div className="flex items-center gap-3 mb-3 shrink-0 flex-wrap">
        <h1 className="text-[13px] font-semibold text-zinc-200">Events</h1>
        <span className="font-mono text-[10px] text-zinc-600 tnum">{filtered.length} shown</span>
        <div className="flex-1" />
        <div className="relative">
          <Search size={12} className="absolute left-2 top-1/2 -translate-y-1/2 text-zinc-600" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="filter type / message…"
            className="h-7 pl-7 pr-2 w-[220px] bg-panel2 border border-line2 rounded-[4px] font-mono text-[11px] text-zinc-200 placeholder:text-zinc-600"
            aria-label="Filter events"
          />
        </div>
        <Seg options={SEVS} value={sev} onChange={setSev} />
        <label className="flex items-center gap-1.5 font-mono text-[10px] text-zinc-500 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={autoScroll}
            onChange={(e) => setAutoScroll(e.target.checked)}
            className="accent-[#4F8CFF] h-3 w-3"
          />
          auto-scroll
        </label>
      </div>

      <div ref={scrollRef} className="flex-1 min-h-0 border border-line rounded-[6px] overflow-y-auto bg-ink py-1">
        {filtered.length === 0 ? (
          <div className="p-6 text-center font-mono text-[11px] text-zinc-600">
            no events match — the log fills once the simulation runs
          </div>
        ) : (
          filtered.map((e) => <EventRow key={e.id} e={e} />)
        )}
      </div>
    </div>
  );
}
