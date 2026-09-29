import { API_BASE } from './config';
import type {
  CustomScenarioRequest,
  FaultType,
  GameProfile,
  GameSession,
  HistoryPayload,
  RoutingDecision,
  RoutingStrategy,
  Scenario,
  ServerNode,
  SimulationState,
  SystemEvent,
  SystemInfo,
} from './types';

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(API_BASE + path, {
      headers: { 'Content-Type': 'application/json' },
      ...init,
    });
  } catch (e) {
    throw new Error('Backend unreachable at ' + API_BASE);
  }
  if (!res.ok) {
    let msg = 'HTTP ' + res.status;
    try {
      const body = await res.json();
      if (body && typeof body.error === 'string') msg = body.error;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  return (await res.json()) as T;
}

export const getSystem = () => req<SystemInfo>('/system');
export const getServers = () => req<ServerNode[]>('/servers');
export const getServer = (id: string) => req<ServerNode>('/servers/' + encodeURIComponent(id));
export const getSessions = (params?: { state?: string; serverId?: string; search?: string }) => {
  const q = new URLSearchParams();
  if (params?.state) q.set('state', params.state);
  if (params?.serverId) q.set('serverId', params.serverId);
  if (params?.search) q.set('search', params.search);
  const s = q.toString();
  return req<GameSession[]>('/sessions' + (s ? '?' + s : ''));
};
export const getSession = (id: string) => req<GameSession>('/sessions/' + encodeURIComponent(id));
export const terminateSession = (id: string) =>
  req<{ terminated: boolean; sessionId: string }>('/sessions/' + encodeURIComponent(id), {
    method: 'DELETE',
  });
export const getDecisions = (limit = 50) => req<RoutingDecision[]>('/routing/decisions?limit=' + limit);
export const getStrategy = () => req<{ strategy: RoutingStrategy }>('/routing/strategy');
export const putStrategy = (strategy: RoutingStrategy) =>
  req<{ strategy: RoutingStrategy }>('/routing/strategy', {
    method: 'PUT',
    body: JSON.stringify({ strategy }),
  });
export const getEvents = (limit = 100, severity?: string) =>
  req<SystemEvent[]>('/events?limit=' + limit + (severity ? '&severity=' + severity : ''));
export const getGames = () => req<GameProfile[]>('/games');
export const getScenarios = () => req<Scenario[]>('/scenarios');
export const startScenario = (id: string) =>
  req<{ started: boolean; scenarioId: string }>('/scenarios/' + encodeURIComponent(id) + '/start', { method: 'POST' });
export const stopScenario = (id: string) =>
  req<{ stopped: boolean }>('/scenarios/' + encodeURIComponent(id) + '/stop', { method: 'POST' });
export const createCustomScenario = (body: CustomScenarioRequest) =>
  req<Scenario>('/scenarios/custom', { method: 'POST', body: JSON.stringify(body) });
export const deleteCustomScenario = (id: string) =>
  req<{ deleted: boolean; scenarioId: string }>('/scenarios/custom/' + encodeURIComponent(id), {
    method: 'DELETE',
  });

export const simStart = () => req<{ state: SimulationState }>('/simulation/start', { method: 'POST' });
export const simPause = () => req<{ state: SimulationState }>('/simulation/pause', { method: 'POST' });
export const simResume = () => req<{ state: SimulationState }>('/simulation/resume', { method: 'POST' });
export const simReset = () => req<{ state: SimulationState }>('/simulation/reset', { method: 'POST' });
export const simSpeed = (speed: number) =>
  req<{ speed: number }>('/simulation/speed', { method: 'PUT', body: JSON.stringify({ speed }) });

export const injectFault = (serverId: string, type: FaultType) =>
  req<{ injected: boolean; type: FaultType }>('/servers/' + encodeURIComponent(serverId) + '/fault', {
    method: 'POST',
    body: JSON.stringify({ type }),
  });
export const recoverServer = (serverId: string) =>
  req<{ recovering: boolean }>('/servers/' + encodeURIComponent(serverId) + '/recover', { method: 'POST' });

export const getHistory = (windowSec = 300) =>
  req<HistoryPayload>('/metrics/history?windowSec=' + windowSec);

/** Seed payload assembled on connect: REST snapshot of the whole world. */
export interface SeedPayload {
  system: SystemInfo;
  servers: ServerNode[];
  sessions: GameSession[];
  decisions: RoutingDecision[];
  events: SystemEvent[];
  strategy: RoutingStrategy;
  scenarios: Scenario[];
  games: GameProfile[];
  history: HistoryPayload;
}

export async function fetchSeed(): Promise<SeedPayload> {
  const [system, servers, sessions, decisions, events, strategyRes, scenarios, games, history] =
    await Promise.all([
      getSystem(),
      getServers(),
      getSessions(),
      getDecisions(50),
      getEvents(100),
      getStrategy(),
      getScenarios(),
      getGames(),
      getHistory(300),
    ]);
  return {
    system,
    servers,
    sessions,
    decisions,
    events,
    strategy: strategyRes.strategy,
    scenarios,
    games,
    history,
  };
}
