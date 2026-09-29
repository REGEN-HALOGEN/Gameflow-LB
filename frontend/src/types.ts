// ---------------------------------------------------------------------------
// GameFlow LB contract types — mirrors ~/workspace/gameflow-lb/CONTRACT.md
// ---------------------------------------------------------------------------

export type ServerState = 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'OFFLINE' | 'RECOVERING';
export type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN';
export type RmiStatus = 'CONNECTED' | 'UNREACHABLE';
export type SessionState = 'CREATING' | 'ACTIVE' | 'MIGRATING' | 'TERMINATING' | 'TERMINATED' | 'FAILED';
export type SimulationState = 'STOPPED' | 'RUNNING' | 'PAUSED';
export type RoutingStrategy = 'WEIGHTED_GAMING' | 'LEAST_SESSIONS' | 'LOWEST_LATENCY' | 'ROUND_ROBIN';
export type EventSeverity = 'INFO' | 'WARN' | 'ERROR' | 'CRITICAL';
export type FaultType = 'GPU_OVERLOAD' | 'LATENCY_SPIKE' | 'PACKET_LOSS' | 'RMI_FAILURE' | 'CRASH';
export type WsStatus = 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED';

export interface ServerMetrics {
  cpu: number;
  gpu: number;
  ram: number;
  vram: number;
  encoding: number;
  networkMbps: number;
  latencyMs: number;
  jitterMs: number;
  packetLoss: number;
  sessions: number;
  requestsPerSec: number;
  temperatureC: number;
  timestamp: number;
}

export interface ServerNode {
  id: string;
  region: string;
  city: string;
  capacity: number;
  state: ServerState;
  circuitState: CircuitState;
  rmiStatus: RmiStatus;
  eligible: boolean;
  weight: number;
  metrics: ServerMetrics;
}

export interface GameSession {
  id: string;
  playerId: string;
  game: string;
  serverId: string;
  playerRegion: string;
  resolution: string;
  fps: number;
  state: SessionState;
  latencyMs: number;
  startTime: number;
  durationSec: number;
}

export interface ScoreBreakdown {
  latency: number;
  cpu: number;
  gpu: number;
  packetLoss: number;
  sessions: number;
  jitter: number;
  penalty: number;
}

export interface CandidateScore {
  serverId: string;
  latencyMs: number;
  cpu: number;
  gpu: number;
  packetLoss: number;
  sessions: number;
  jitterMs: number;
  score: number;
  scoreBreakdown: ScoreBreakdown;
  eligible: boolean;
  penaltyReason: string | null;
}

export interface RoutingDecision {
  id: string;
  timestamp: number;
  playerId: string;
  game: string;
  playerRegion: string;
  resolution: string;
  fps: number;
  strategy: RoutingStrategy;
  candidates: CandidateScore[];
  selectedServerId: string | null;
  reason: string;
}

export interface SystemEvent {
  id: string;
  timestamp: number;
  type: string;
  severity: EventSeverity;
  message: string;
  serverId: string | null;
}

export interface GameProfile {
  id: string;
  name: string;
  genre: string;
  latencySensitive: boolean;
  gpuLoad: number;
  vramLoad: number;
  networkLoad: number;
  cpuLoad: number;
}

export interface Scenario {
  id: string;
  name: string;
  description: string;
  active: boolean;
  targetServerId: string | null;
}

export interface MetricPoint {
  t: number;
  cpu: number;
  gpu: number;
  ram: number;
  vram: number;
  encoding: number;
  latencyMs: number;
  jitterMs: number;
  packetLoss: number;
  sessions: number;
  networkMbps: number;
  requestsPerSec: number;
}

export interface HistoryPayload {
  servers: Record<string, MetricPoint[]>;
  aggregate: {
    t: number[];
    requestsPerSec: number[];
    avgLatencyMs: number[];
    packetLoss: number[];
    throughputMbps: number[];
  };
}

export interface SystemInfo {
  status: string;
  simulationState: SimulationState;
  speed: number;
  time: number;
  uptimeSec: number;
  strategy: RoutingStrategy;
  totals: {
    activeSessions: number;
    requestsPerSec: number;
    avgLatencyMs: number;
    healthyServers: number;
    avgGpu: number;
    packetLoss: number;
  };
}
