import { Link } from 'react-router-dom';
import { useSim } from '../state/sim';
import { fmt, fmtInt, fmtMs, fmtPct } from '../lib/format';
import TopologyGraph from '../components/TopologyGraph';
import { EventRows } from '../components/EventTail';
import { EmptyState, SectionTitle, Skeleton, StatBlock } from '../components/ui';

export default function Overview() {
  const { ready, backendUp, totals, decisions, events, inspectDecision, simState } = useSim();

  if (!ready) {
    return (
      <div className="p-4 grid grid-cols-6 gap-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton key={i} className="h-[64px]" />
        ))}
      </div>
    );
  }

  if (!backendUp) {
    return (
      <div className="p-4">
        <BackendDown />
      </div>
    );
  }

  return (
    <div className="p-4 space-y-4">
      {/* compact stat strip */}
      <section aria-label="System statistics">
        <div className="grid grid-cols-3 md:grid-cols-6 gap-3 border-b border-line pb-3">
          <StatBlock label="Active sessions" value={fmtInt(totals.activeSessions)} />
          <StatBlock label="Requests / sec" value={fmt(totals.requestsPerSec)} />
          <StatBlock
            label="Avg latency"
            value={fmt(totals.avgLatencyMs)}
            unit="ms"
            color={totals.avgLatencyMs > 100 ? '#EF4444' : totals.avgLatencyMs > 50 ? '#F59E0B' : undefined}
            tip="Session-weighted mean round-trip latency across healthy servers."
          />
          <StatBlock label="Healthy servers" value={fmtInt(totals.healthyServers)} />
          <StatBlock
            label="Avg GPU"
            value={fmt(totals.avgGpu)}
            unit="%"
            color={totals.avgGpu > 90 ? '#EF4444' : totals.avgGpu > 75 ? '#F59E0B' : undefined}
            tip="Mean GPU utilization across all game servers."
          />
          <StatBlock
            label="Packet loss"
            value={fmt(totals.packetLoss, 2)}
            unit="%"
            color={totals.packetLoss > 5 ? '#EF4444' : totals.packetLoss > 1 ? '#F59E0B' : undefined}
            tip="Session-weighted mean packet loss. Above 5% a server is excluded from routing."
          />
        </div>
      </section>

      {/* mini topology */}
      <section aria-label="Live infrastructure">
        <SectionTitle
          right={
            <Link to="/topology" className="font-mono text-[10px] text-info hover:underline">
              open full topology →
            </Link>
          }
        >
          Live infrastructure
        </SectionTitle>
        <div className="h-[340px] border border-line rounded-[6px] overflow-hidden bg-ink">
          <TopologyGraph compact />
        </div>
      </section>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        {/* recent routing decisions */}
        <section aria-label="Recent routing decisions">
          <SectionTitle
            right={
              <Link to="/routing" className="font-mono text-[10px] text-info hover:underline">
                all decisions →
              </Link>
            }
          >
            Recent routing decisions
          </SectionTitle>
          {decisions.length === 0 ? (
            <EmptyState
              title="No routing decisions yet"
              hint={simState === 'STOPPED' ? 'Start the simulation to see the routing engine place players.' : 'Waiting for player requests…'}
            />
          ) : (
            <div className="border border-line rounded-[6px] divide-y divide-line">
              {decisions.slice(0, 8).map((d) => (
                <button
                  key={d.id}
                  onClick={() => inspectDecision(d)}
                  className="w-full text-left px-3 py-1.5 hover:bg-panel2 transition-colors flex items-center gap-3"
                >
                  <span className="font-mono text-[10px] text-zinc-500 w-[70px]">{d.id}</span>
                  <span className="font-mono text-[11px] text-zinc-300">{d.playerId}</span>
                  <span className="font-mono text-[10px] text-zinc-600 truncate flex-1">{d.game}</span>
                  <span className="font-mono text-[11px] tnum" style={{ color: d.selectedServerId ? '#22C55E' : '#EF4444' }}>
                    {d.selectedServerId ?? 'NO CANDIDATE'}
                  </span>
                </button>
              ))}
            </div>
          )}
        </section>

        {/* event tail */}
        <section aria-label="Live events">
          <SectionTitle
            right={
              <Link to="/events" className="font-mono text-[10px] text-info hover:underline">
                full log →
              </Link>
            }
          >
            Live events
          </SectionTitle>
          {events.length === 0 ? (
            <EmptyState title="No events yet" hint="System events will appear here once the simulation runs." />
          ) : (
            <div className="border border-line rounded-[6px] overflow-hidden max-h-[268px] overflow-y-auto py-1">
              <EventRows events={events.slice(0, 30)} />
            </div>
          )}
        </section>
      </div>

      <div className="font-mono text-[10px] text-zinc-600">
        avg session latency {fmtMs(totals.avgLatencyMs)} · all figures stream from the Java backend over WebSocket
      </div>
    </div>
  );
}

export function BackendDown({ onRetry }: { onRetry?: () => void }) {
  const { retrySeed } = useSim();
  return (
    <EmptyState
      title="Backend unavailable"
      hint="Could not reach the Spring Boot backend. Start it with `mvn spring-boot:run` (port 8080), then retry."
      action={
        <button
          onClick={onRetry ?? retrySeed}
          className="inline-flex items-center font-mono text-[11px] px-3 h-7 rounded-[4px] border border-info/40 text-info hover:bg-info/10 transition-colors"
        >
          Retry connection
        </button>
      }
    />
  );
}
