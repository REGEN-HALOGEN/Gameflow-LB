import { memo } from 'react';
import { fmtTimeMs } from '../lib/format';
import { SEVERITY_COLOR } from './ui';
import type { SystemEvent } from '../types';

/** Ops-log style event rows: HH:MM:SS.mmm TYPE message */
export const EventRows = memo(function EventRows({ events }: { events: SystemEvent[] }) {
  return (
    <div className="font-mono text-[11px] leading-[1.7]">
      {events.map((e) => (
        <div key={e.id} className="flex gap-2 px-2 py-px hover:bg-panel2 whitespace-nowrap">
          <span className="text-zinc-600 tnum shrink-0">{fmtTimeMs(e.timestamp)}</span>
          <span className="shrink-0 font-semibold w-[118px] truncate" style={{ color: SEVERITY_COLOR[e.severity] }}>
            {e.type}
          </span>
          <span className="text-zinc-400 truncate">{e.message}</span>
          {e.serverId && <span className="text-zinc-600 shrink-0">{e.serverId}</span>}
        </div>
      ))}
    </div>
  );
});
