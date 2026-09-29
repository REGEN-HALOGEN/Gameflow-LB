import { useState } from 'react';
import { ChevronDown } from 'lucide-react';

interface ArchNode {
  id: string;
  title: string;
  sub: string;
  desc: string;
  tags: string[];
  accent?: boolean;
}

const NODES: Record<string, ArchNode> = {
  frontend: {
    id: 'frontend',
    title: 'React Frontend',
    sub: 'vite · react 18 · typescript',
    desc: 'Operations console. Renders live topology (React Flow), charts (Recharts) and ops logs. Holds no simulation state of its own — every number on screen originates from the Java backend, seeded over REST and kept live over WebSocket.',
    tags: ['reactflow', 'recharts', 'websocket client', 'no fake timers'],
  },
  rest: {
    id: 'rest',
    title: 'REST API',
    sub: 'spring mvc · /api/*',
    desc: 'Synchronous control plane: system snapshot, server/session listings, routing decisions, event history, strategy switching, simulation controls, fault injection. Used for seeding state and for every user-initiated action.',
    tags: ['GET /api/system', 'GET /api/servers', 'POST /api/scenarios/{id}/start'],
  },
  ws: {
    id: 'ws',
    title: 'WebSocket',
    sub: '/ws/events · json frames',
    desc: 'Push channel from backend to UI. METRIC_UPDATE (buffered, flushed ~2/s), ROUTING_DECISION (full candidate scoring), SESSION_* lifecycle, SERVER_STATE_CHANGED, CIRCUIT_CHANGED, SYSTEM_EVENT, SIMULATION_STATE_CHANGED. Auto-reconnects with exponential backoff.',
    tags: ['METRIC_UPDATE', 'ROUTING_DECISION', 'CIRCUIT_CHANGED'],
  },
  lb: {
    id: 'lb',
    title: 'Spring Boot Load Balancer',
    sub: 'java 21 · webflux · the brain',
    desc: 'Owns all distributed-systems logic. Receives player requests, scores candidates in the RoutingEngine, creates sessions through SessionManager, and drives the simulation clock. Game servers are never contacted directly by the frontend.',
    tags: ['spring boot 3', 'java 21', 'scheduled executors'],
  },
  routing: {
    id: 'routing',
    title: 'RoutingEngine',
    sub: 'strategy pattern',
    desc: 'Scores every eligible game server per player request. Default WeightedGamingStrategy: latency ×0.35, cpu ×0.20, gpu ×0.20, packet-loss ×0.10, sessions ×0.10, jitter ×0.05 — lower is better. Hard-excludes servers past critical thresholds (gpu >90%, loss >5%, latency >100ms, open circuit, offline). Swappable: LeastSessions, LowestLatency, RoundRobin.',
    tags: ['WeightedGamingStrategy', 'explainable scoring'],
  },
  sessions: {
    id: 'sessions',
    title: 'SessionManager',
    sub: 'concurrenthashmap<string, gamesession>',
    desc: 'Thread-safe registry of persistent cloud-gaming sessions. Lifecycle CREATING → ACTIVE → MIGRATING → TERMINATED. On server failure it re-runs the routing algorithm for every affected session and reassigns them — this is the migration you watch in the UI.',
    tags: ['ConcurrentHashMap', 'session migration'],
  },
  health: {
    id: 'health',
    title: 'HealthManager',
    sub: 'rmi health polling',
    desc: 'Polls each game server over RMI on a fixed schedule. Repeated healthCheck() failures move a node HEALTHY → DEGRADED → UNHEALTHY → OFFLINE, which immediately removes it from routing eligibility and raises system events.',
    tags: ['ScheduledExecutorService', 'state machine'],
  },
  circuit: {
    id: 'circuit',
    title: 'CircuitBreaker',
    sub: 'per-server breaker',
    desc: 'Classic CLOSED → OPEN → HALF_OPEN breaker per game server. Repeated RMI failures trip it OPEN (node excluded from routing); after a recovery timeout a single probe is allowed in HALF_OPEN — success closes it, failure re-opens. Traffic returns gradually, never all at once.',
    tags: ['fail-fast', 'gradual recovery'],
  },
  metrics: {
    id: 'metrics',
    title: 'MetricsService',
    sub: 'aggregation + history',
    desc: 'Collects per-server metrics arriving over RMI, maintains rolling history buffers, and computes fleet aggregates (requests/sec, avg latency, packet loss, throughput) that feed the charts and the top stat strip.',
    tags: ['rolling windows', 'fleet aggregates'],
  },
  sim: {
    id: 'sim',
    title: 'SimulationEngine',
    sub: 'traffic + metric simulation',
    desc: 'Generates the synthetic world: player arrivals from six regions with a realistic base-latency matrix, per-game workload profiles (NEON STRIKE is latency-sensitive, IRON FRONT is GPU-heavy), and bounded random-walk metric evolution so values drift plausibly instead of jumping.',
    tags: ['traffic generator', 'scenario engine'],
  },
  rmi: {
    id: 'rmi',
    title: 'Java RMI',
    sub: 'rmiregistry · gameServerRemote',
    desc: 'The genuine remoting layer between load balancer and game servers. Every server exposes GameServerRemote: getMetrics(), healthCheck(), canAcceptSession(), createSession(PlayerRequest), terminateSession(id). The balancer calls these as remote stubs — inject RMI_FAILURE and watch health checks fail, the breaker open, and sessions migrate. Nothing here is mocked.',
    tags: ['getMetrics()', 'healthCheck()', 'createSession()', 'real remote calls'],
    accent: true,
  },
  gs1: {
    id: 'gs1',
    title: 'GS-MUM-01 · GS-MUM-02',
    sub: 'mumbai · gameserverimpl (jvm)',
    desc: 'Two Mumbai nodes, 60 sessions each. GameServerImpl binds GameServerRemote into the RMI registry and simulates GPU/CPU/encoder load from the sessions it hosts.',
    tags: ['rmi server', 'capacity 60'],
  },
  gs2: {
    id: 'gs2',
    title: 'GS-SIN-01',
    sub: 'singapore · gameserverimpl (jvm)',
    desc: 'Singapore node, 80 sessions. Lowest latency for Singapore-region players (~12ms); Mumbai players see ~65ms — the routing engine weighs this heavily for latency-sensitive games.',
    tags: ['rmi server', 'capacity 80'],
  },
  gs3: {
    id: 'gs3',
    title: 'GS-BLR-01',
    sub: 'bangalore · gameserverimpl (jvm)',
    desc: 'Bangalore node, 60 sessions. Serves Bangalore (~10ms), Chennai (~20ms) and Hyderabad (~25ms) players best.',
    tags: ['rmi server', 'capacity 60'],
  },
};

function Box({ n, selected, onSelect }: { n: ArchNode; selected: boolean; onSelect: () => void }) {
  return (
    <button
      onClick={onSelect}
      className={`w-full text-left px-3 py-2.5 border rounded-[6px] transition-colors ${
        selected
          ? 'border-info/60 bg-info/[0.06]'
          : n.accent
            ? 'border-info/40 bg-panel hover:border-info/60'
            : 'border-line2 bg-panel hover:border-zinc-500'
      }`}
    >
      <div className={`font-mono text-[11px] font-bold ${n.accent ? 'text-info' : 'text-zinc-100'}`}>{n.title}</div>
      <div className="font-mono text-[10px] text-zinc-500 mt-0.5">{n.sub}</div>
    </button>
  );
}

function Link({ label }: { label?: string }) {
  return (
    <div className="flex flex-col items-center py-0.5" aria-hidden>
      <div className="w-px h-3 bg-line2" />
      {label && <div className="font-mono text-[9px] text-info my-0.5">{label}</div>}
      <ChevronDown size={12} className="text-zinc-600 -mt-0.5" />
    </div>
  );
}

export default function Architecture() {
  const [sel, setSel] = useState<string>('rmi');
  const n = NODES[sel];

  return (
    <div className="p-4 grid grid-cols-1 xl:grid-cols-[440px_1fr] gap-4 h-full">
      <div className="overflow-y-auto pr-1">
        <h1 className="text-[13px] font-semibold text-zinc-200 mb-1">System Architecture</h1>
        <p className="font-mono text-[10px] text-zinc-600 mb-3">click any component for its responsibility</p>

        <Box n={NODES.frontend} selected={sel === 'frontend'} onSelect={() => setSel('frontend')} />
        <Link />
        <div className="grid grid-cols-2 gap-2">
          <Box n={NODES.rest} selected={sel === 'rest'} onSelect={() => setSel('rest')} />
          <Box n={NODES.ws} selected={sel === 'ws'} onSelect={() => setSel('ws')} />
        </div>
        <Link />
        <Box n={NODES.lb} selected={sel === 'lb'} onSelect={() => setSel('lb')} />
        <div className="ml-4 mt-2 grid grid-cols-2 gap-2 border-l border-line2 pl-3">
          {['routing', 'sessions', 'health', 'circuit', 'metrics', 'sim'].map((id) => (
            <Box key={id} n={NODES[id]} selected={sel === id} onSelect={() => setSel(id)} />
          ))}
        </div>
        <Link label="java rmi — remote method invocation" />
        <Box n={NODES.rmi} selected={sel === 'rmi'} onSelect={() => setSel('rmi')} />
        <Link label="rmi stubs" />
        <div className="space-y-2">
          {['gs1', 'gs2', 'gs3'].map((id) => (
            <Box key={id} n={NODES[id]} selected={sel === id} onSelect={() => setSel(id)} />
          ))}
        </div>
      </div>

      <div className="xl:sticky xl:top-4 self-start">
        <div className="border border-line2 rounded-[6px] bg-panel p-4">
          <div className="text-[10px] uppercase tracking-[0.12em] text-zinc-500 mb-1">Component</div>
          <h2 className={`font-mono text-[15px] font-bold ${n.accent ? 'text-info' : 'text-zinc-100'}`}>{n.title}</h2>
          <div className="font-mono text-[11px] text-zinc-500 mt-0.5">{n.sub}</div>
          <p className="mt-3 text-[12px] leading-relaxed text-zinc-400">{n.desc}</p>
          <div className="mt-3 flex flex-wrap gap-1.5">
            {n.tags.map((t) => (
              <span key={t} className="font-mono text-[10px] px-2 py-0.5 border border-line2 rounded-[4px] text-zinc-400">
                {t}
              </span>
            ))}
          </div>
        </div>

        <div className="mt-3 border border-info/30 rounded-[6px] bg-info/[0.04] p-4">
          <div className="text-[10px] uppercase tracking-[0.12em] text-info mb-1">Why RMI?</div>
          <p className="text-[12px] leading-relaxed text-zinc-400">
            RMI is the project's distributed-systems backbone: the load balancer and the game servers are
            separate JVM processes communicating over real remote method calls. Health checks, metric
            collection and session creation all cross a process boundary — so failures (timeouts,
            unreachable registries) behave like production failures, and the circuit breaker, health
            manager and migration logic react to genuine remoting faults, not in-memory flags.
          </p>
        </div>
      </div>
    </div>
  );
}
