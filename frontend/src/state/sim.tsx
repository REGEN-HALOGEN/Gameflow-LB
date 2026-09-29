import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useRef,
} from 'react';
import * as api from '../api';
import type { SeedPayload } from '../api';
import { WS_URL } from '../config';
import { pushAggregate, pushMetric, seedHistory, type Totals } from '../lib/charts';
import type {
  CircuitState,
  GameProfile,
  GameSession,
  RoutingDecision,
  RoutingStrategy,
  Scenario,
  ServerMetrics,
  ServerNode,
  ServerState,
  SimulationState,
  SystemEvent,
  WsStatus,
} from '../types';

// ---------------------------------------------------------------------------

export interface ActiveRoute {
  decisionId: string;
  serverId: string | null;
  at: number;
  /** Monotonic token so the clear-timer effect re-runs for every decision,
   *  even when two decisions land in the same millisecond (Date.now()
   *  collisions at high traffic left the edge animation stuck on). */
  seq: number;
}

export interface SimState {
  ready: boolean;
  backendUp: boolean;
  seedError: string | null;
  wsStatus: WsStatus;
  simState: SimulationState;
  speed: number;
  strategy: RoutingStrategy;
  totals: Totals;
  servers: Record<string, ServerNode>;
  serverIds: string[];
  sessions: GameSession[];
  decisions: RoutingDecision[];
  events: SystemEvent[];
  scenarios: Scenario[];
  games: GameProfile[];
  drawerServerId: string | null;
  inspectedDecision: RoutingDecision | null;
  activeRoute: ActiveRoute | null;
  lastMetricAt: number;
}

const INITIAL_TOTALS: Totals = {
  activeSessions: 0,
  requestsPerSec: 0,
  avgLatencyMs: 0,
  healthyServers: 0,
  avgGpu: 0,
  packetLoss: 0,
};

const initialState: SimState = {
  ready: false,
  backendUp: true,
  seedError: null,
  wsStatus: 'CONNECTING',
  simState: 'STOPPED',
  speed: 1,
  strategy: 'WEIGHTED_GAMING',
  totals: INITIAL_TOTALS,
  servers: {},
  serverIds: [],
  sessions: [],
  decisions: [],
  events: [],
  scenarios: [],
  games: [],
  drawerServerId: null,
  inspectedDecision: null,
  activeRoute: null,
  lastMetricAt: 0,
};

// ---------------------------------------------------------------------------

type Action =
  | { type: 'WS_STATUS'; status: WsStatus }
  | { type: 'SEED'; payload: SeedPayload }
  | { type: 'SEED_ERROR'; error: string }
  | { type: 'RETRY_SEED' }
  | {
      type: 'METRICS_FLUSH';
      metrics: Record<string, ServerMetrics>;
      totals: Totals;
      at: number;
    }
  | { type: 'SERVER_STATE_CHANGED'; serverId: string; newState: ServerState }
  | { type: 'CIRCUIT_CHANGED'; serverId: string; newState: CircuitState }
  | { type: 'SESSION_CREATED'; session: GameSession }
  | { type: 'SESSION_TERMINATED'; sessionId: string }
  | { type: 'SESSION_MIGRATING'; sessionId: string; toServerId: string | null }
  | { type: 'SESSION_MIGRATED'; sessionId: string; toServerId: string }
  | { type: 'ROUTING_DECISION'; decision: RoutingDecision }
  | { type: 'SYSTEM_EVENT'; event: SystemEvent }
  | { type: 'SIMULATION_STATE_CHANGED'; state: SimulationState; speed: number }
  | { type: 'STRATEGY'; strategy: RoutingStrategy }
  | { type: 'SCENARIOS'; scenarios: Scenario[] }
  | { type: 'DRAWER'; serverId: string | null }
  | { type: 'INSPECT'; decision: RoutingDecision | null }
  | { type: 'CLEAR_ROUTE'; decisionId: string };

function patchServer(state: SimState, id: string, patch: Partial<ServerNode>): SimState {
  const s = state.servers[id];
  if (!s) return state;
  return { ...state, servers: { ...state.servers, [id]: { ...s, ...patch } } };
}

// Monotonic token for ActiveRoute.seq. Module-level (not in the reducer's
// state) so every ROUTING_DECISION gets a unique ordering key even when two
// decisions share the same Date.now() millisecond.
let routeSeq = 0;

function reducer(state: SimState, action: Action): SimState {
  switch (action.type) {
    case 'WS_STATUS':
      return { ...state, wsStatus: action.status };
    case 'RETRY_SEED':
      return { ...state, seedError: null, backendUp: true };
    case 'SEED_ERROR':
      return { ...state, backendUp: false, seedError: action.error, ready: true, wsStatus: 'DISCONNECTED' };
    case 'SEED': {
      const p = action.payload;
      const servers: Record<string, ServerNode> = {};
      for (const s of p.servers) servers[s.id] = s;
      // Preserve drawer/inspector across reconnects so fault-injection doesn't
      // close the drawer mid-use. Only clear drawerServerId if that server is
      // no longer present in the new seed (e.g. was permanently removed).
      const keepDrawer = state.drawerServerId && servers[state.drawerServerId]
        ? state.drawerServerId
        : null;
      const keepInspect = state.inspectedDecision
        ? p.decisions.find((d) => d.id === state.inspectedDecision!.id) ?? state.inspectedDecision
        : null;
      return {
        ...state,
        ready: true,
        backendUp: true,
        seedError: null,
        simState: p.system.simulationState,
        speed: p.system.speed,
        strategy: p.system.strategy,
        totals: { ...p.system.totals },
        servers,
        serverIds: p.servers.map((s) => s.id),
        sessions: p.sessions,
        decisions: p.decisions,
        events: p.events,
        scenarios: p.scenarios,
        games: p.games,
        activeRoute: null,
        drawerServerId: keepDrawer,
        inspectedDecision: keepInspect,
      };
    }
    case 'METRICS_FLUSH': {
      const servers = { ...state.servers };
      let changed = false;
      for (const [id, m] of Object.entries(action.metrics)) {
        const s = servers[id];
        if (s) {
          servers[id] = { ...s, metrics: m };
          changed = true;
        }
      }
      if (!changed) return state;
      return { ...state, servers, totals: action.totals, lastMetricAt: action.at };
    }
    case 'SERVER_STATE_CHANGED':
      return patchServer(state, action.serverId, { state: action.newState });
    case 'CIRCUIT_CHANGED':
      return patchServer(state, action.serverId, { circuitState: action.newState });
    case 'SESSION_CREATED': {
      // O(1) dedup: event IDs are unique sequences, duplicate arrivals are rare
      // but can happen on WS reconnect if the seed and first live frame overlap.
      if (state.sessions.some((s) => s.id === action.session.id)) return state;
      return { ...state, sessions: [action.session, ...state.sessions].slice(0, 500) };
    }
    case 'SESSION_TERMINATED':
      return { ...state, sessions: state.sessions.filter((s) => s.id !== action.sessionId) };
    case 'SESSION_MIGRATING':
      return {
        ...state,
        sessions: state.sessions.map((s) =>
          s.id === action.sessionId ? { ...s, state: 'MIGRATING' as const } : s,
        ),
      };
    case 'SESSION_MIGRATED':
      return {
        ...state,
        sessions: state.sessions.map((s) =>
          s.id === action.sessionId
            ? { ...s, state: 'ACTIVE' as const, serverId: action.toServerId }
            : s,
        ),
      };
    case 'ROUTING_DECISION': {
      const decisions = [action.decision, ...state.decisions.filter((d) => d.id !== action.decision.id)].slice(0, 200);
      // Only flash a real server selection — skip NO_CANDIDATE (selectedServerId null)
      // because there's no edge to highlight and the null serverId confuses the
      // edge active-check (null === serverId is always false → edge never clears).
      const newRoute = action.decision.selectedServerId
        ? { decisionId: action.decision.id, serverId: action.decision.selectedServerId, at: Date.now(), seq: ++routeSeq }
        : state.activeRoute; // keep current flash, don't reset it to null-server
      return { ...state, decisions, activeRoute: newRoute };
    }
    case 'SYSTEM_EVENT': {
      // Event IDs are monotonically unique (EVT-000NNN) — drop the O(n) filter.
      const events = [action.event, ...state.events].slice(0, 500);
      return { ...state, events };
    }
    case 'SIMULATION_STATE_CHANGED':
      return { ...state, simState: action.state, speed: action.speed };
    case 'STRATEGY':
      return { ...state, strategy: action.strategy };
    case 'SCENARIOS':
      return { ...state, scenarios: action.scenarios };
    case 'DRAWER':
      return { ...state, drawerServerId: action.serverId };
    case 'INSPECT':
      return { ...state, inspectedDecision: action.decision };
    case 'CLEAR_ROUTE':
      return state.activeRoute && state.activeRoute.decisionId === action.decisionId
        ? { ...state, activeRoute: null }
        : state;
    default:
      return state;
  }
}

// ---------------------------------------------------------------------------

function computeTotals(servers: Record<string, ServerNode>): { totals: Totals; throughput: number } {
  const list = Object.values(servers);
  let activeSessions = 0;
  let requestsPerSec = 0;
  let latW = 0;
  let latSum = 0;
  let gpuSum = 0;
  let plW = 0;
  let plSum = 0;
  let healthyServers = 0;
  let throughput = 0;
  for (const s of list) {
    const m = s.metrics;
    activeSessions += m.sessions;
    requestsPerSec += m.requestsPerSec;
    throughput += m.networkMbps;
    gpuSum += m.gpu;
    if (s.state === 'HEALTHY') healthyServers++;
    // Skip OFFLINE/UNHEALTHY servers from latency & loss averages — their
    // metrics are frozen at last-known (possibly fault-inflated) values and
    // they carry zero live traffic, so including them misleads the stat strip.
    if (s.state === 'OFFLINE' || s.state === 'UNHEALTHY') continue;
    const w = Math.max(1, m.sessions);
    latW += w;
    latSum += m.latencyMs * w;
    plW += w;
    plSum += m.packetLoss * w;
  }
  return {
    totals: {
      activeSessions,
      requestsPerSec,
      avgLatencyMs: latW > 0 ? latSum / latW : 0,
      healthyServers,
      avgGpu: list.length > 0 ? gpuSum / list.length : 0,
      packetLoss: plW > 0 ? plSum / plW : 0,
    },
    throughput,
  };
}

interface SimContextValue extends SimState {
  openDrawer: (serverId: string | null) => void;
  inspectDecision: (d: RoutingDecision | null) => void;
  retrySeed: () => void;
  /** Re-fetch scenarios from REST — fallback for start/stop when the
   *  SCENARIO_STARTED/STOPPED WS event is missed. */
  refreshScenarios: () => void;
}

const SimContext = createContext<SimContextValue | null>(null);

export function useSim(): SimContextValue {
  const ctx = useContext(SimContext);
  if (!ctx) throw new Error('useSim must be used inside SimulationProvider');
  return ctx;
}

export function SimulationProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const stateRef = useRef(state);
  stateRef.current = state;
  const retryRef = useRef(0);
  // Single timer ref — always cancel the previous one before setting a new one.
  // Keyed on activeRoute.seq (monotonic) so SEED resets and rapid decisions
  // never orphan a stale CLEAR_ROUTE that can't match the current decisionId.
  const routeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const openDrawer = useCallback((serverId: string | null) => dispatch({ type: 'DRAWER', serverId }), []);
  const inspectDecision = useCallback((d: RoutingDecision | null) => dispatch({ type: 'INSPECT', decision: d }), []);
  const retrySeed = useCallback(() => {
    retryRef.current++;
    dispatch({ type: 'RETRY_SEED' });
  }, []);
  const refreshScenarios = useCallback(() => {
    api
      .getScenarios()
      .then((scenarios) => dispatch({ type: 'SCENARIOS', scenarios }))
      .catch(() => {});
  }, []);

  // Clear the routing-edge highlight ~1.5s after a decision.
  // Keyed on activeRoute.seq (monotonic) instead of the wall-clock timestamp
  // so rapid back-to-back decisions — including two in the same millisecond —
  // and SEED resets never orphan a stale CLEAR_ROUTE that can't match the
  // current decisionId (that left the edge animation stuck on).
  useEffect(() => {
    if (routeTimerRef.current !== null) {
      clearTimeout(routeTimerRef.current);
      routeTimerRef.current = null;
    }
    if (!state.activeRoute) return;
    const id = state.activeRoute.decisionId;
    routeTimerRef.current = setTimeout(() => {
      routeTimerRef.current = null;
      dispatch({ type: 'CLEAR_ROUTE', decisionId: id });
    }, 1500);
    // No cleanup return — the ref handles cancellation on the next run.
  }, [state.activeRoute?.seq]);  // depend on the monotonic token, not the timestamp

  useEffect(() => {
    let ws: WebSocket | null = null;
    let closed = false;
    let attempt = 0;
    let retryTimer: ReturnType<typeof setTimeout> | null = null;
    const metricsBuf = new Map<string, ServerMetrics>();

    const flushMetrics = () => {
      if (metricsBuf.size === 0) return;
      const s = stateRef.current;
      const nextServers: Record<string, ServerNode> = { ...s.servers };
      const at = Date.now();
      for (const [id, m] of metricsBuf) {
        const srv = nextServers[id];
        if (srv) nextServers[id] = { ...srv, metrics: m };
        pushMetric(id, m);
      }
      metricsBuf.clear();
      const { totals, throughput } = computeTotals(nextServers);
      pushAggregate(at, totals, throughput);
      const m: Record<string, ServerMetrics> = {};
      for (const [id, srv] of Object.entries(nextServers)) m[id] = srv.metrics;
      dispatch({ type: 'METRICS_FLUSH', metrics: m, totals, at });
    };
    const flushTimer = setInterval(flushMetrics, 500);

    const route = (msg: any) => {
      switch (msg.type) {
        case 'METRIC_UPDATE':
          if (msg.serverId && msg.metrics) metricsBuf.set(msg.serverId, msg.metrics as ServerMetrics);
          break;
        case 'SERVER_STATE_CHANGED':
          dispatch({ type: 'SERVER_STATE_CHANGED', serverId: msg.serverId, newState: msg.newState });
          break;
        case 'CIRCUIT_CHANGED':
          dispatch({ type: 'CIRCUIT_CHANGED', serverId: msg.serverId, newState: msg.newState });
          break;
        case 'SESSION_CREATED':
          if (msg.session) dispatch({ type: 'SESSION_CREATED', session: msg.session });
          break;
        case 'SESSION_TERMINATED':
          if (msg.sessionId) dispatch({ type: 'SESSION_TERMINATED', sessionId: msg.sessionId });
          break;
        case 'SESSION_MIGRATING':
          if (msg.sessionId)
            dispatch({ type: 'SESSION_MIGRATING', sessionId: msg.sessionId, toServerId: msg.toServerId ?? null });
          break;
        case 'SESSION_MIGRATED':
          if (msg.sessionId && msg.toServerId)
            dispatch({ type: 'SESSION_MIGRATED', sessionId: msg.sessionId, toServerId: msg.toServerId });
          break;
        case 'ROUTING_DECISION':
          if (msg.decision) dispatch({ type: 'ROUTING_DECISION', decision: msg.decision });
          break;
        case 'SYSTEM_EVENT':
          if (msg.event) {
            dispatch({ type: 'SYSTEM_EVENT', event: msg.event });
            const t = msg.event.type as string;
            if (t === 'SCENARIO_STARTED' || t === 'SCENARIO_STOPPED') {
              api
                .getScenarios()
                .then((scenarios) => dispatch({ type: 'SCENARIOS', scenarios }))
                .catch(() => {});
            }
            if (t === 'STRATEGY_CHANGED') {
              api
                .getStrategy()
                .then((r) => dispatch({ type: 'STRATEGY', strategy: r.strategy }))
                .catch(() => {});
            }
          }
          break;
        case 'SIMULATION_STATE_CHANGED':
          dispatch({ type: 'SIMULATION_STATE_CHANGED', state: msg.state, speed: msg.speed ?? 1 });
          break;
        default:
          break;
      }
    };

    const scheduleReconnect = () => {
      if (closed) return;
      const delay = Math.min(1000 * Math.pow(2, attempt), 30000);
      attempt++;
      retryTimer = setTimeout(connect, delay);
    };

    const connect = async () => {
      if (closed) return;
      dispatch({ type: 'WS_STATUS', status: 'CONNECTING' });
      // Seed from REST first; if backend is down, back off and retry.
      try {
        const seed = await api.fetchSeed();
        if (closed) return;
        seedHistory(seed.history);
        dispatch({ type: 'SEED', payload: seed });
        attempt = 0;
      } catch (e) {
        if (closed) return;
        dispatch({ type: 'SEED_ERROR', error: e instanceof Error ? e.message : 'Seed failed' });
        scheduleReconnect();
        return;
      }
      try {
        ws = new WebSocket(WS_URL);
      } catch {
        scheduleReconnect();
        return;
      }
      ws.onopen = () => {
        attempt = 0;
        dispatch({ type: 'WS_STATUS', status: 'CONNECTED' });
      };
      ws.onmessage = (ev) => {
        try {
          route(JSON.parse(ev.data));
        } catch {
          /* malformed frame — ignore */
        }
      };
      ws.onclose = () => {
        dispatch({ type: 'WS_STATUS', status: 'DISCONNECTED' });
        scheduleReconnect();
      };
      ws.onerror = () => {
        try {
          ws?.close();
        } catch {
          /* ignore */
        }
      };
    };

    connect();
    return () => {
      closed = true;
      clearInterval(flushTimer);
      if (retryTimer) clearTimeout(retryTimer);
      try {
        ws?.close();
      } catch {
        /* ignore */
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [retryRef.current]);

  const value = useMemo<SimContextValue>(
    () => ({ ...state, openDrawer, inspectDecision, retrySeed, refreshScenarios }),
    [state, openDrawer, inspectDecision, retrySeed, refreshScenarios],
  );

  return <SimContext.Provider value={value}>{children}</SimContext.Provider>;
}
