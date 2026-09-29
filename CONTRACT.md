# GameFlow LB — API & WebSocket Contract

This document is the **single source of truth** for the backend/frontend interface.
Both implementations MUST conform to it exactly. Do not invent new field names.

Backend base URL: `http://localhost:8080`
Frontend dev server: `http://localhost:5173` (Vite proxies `/api` and `/ws` to `localhost:8080`)

All timestamps are epoch millis. All percentages are 0–100 floats. Latency in ms.

---

## 1. Enums

- `ServerState`: `HEALTHY | DEGRADED | UNHEALTHY | OFFLINE | RECOVERING`
- `CircuitState`: `CLOSED | OPEN | HALF_OPEN`
- `RmiStatus`: `CONNECTED | UNREACHABLE`
- `SessionState`: `CREATING | ACTIVE | MIGRATING | TERMINATING | TERMINATED | FAILED`
- `SimulationState`: `STOPPED | RUNNING | PAUSED`
- `RoutingStrategy`: `WEIGHTED_GAMING | LEAST_SESSIONS | LOWEST_LATENCY | ROUND_ROBIN`
- `EventSeverity`: `INFO | WARN | ERROR | CRITICAL`
- `FaultType`: `GPU_OVERLOAD | LATENCY_SPIKE | PACKET_LOSS | RMI_FAILURE | CRASH`

---

## 2. JSON shapes

### ServerMetrics
```json
{
  "cpu": 42.3, "gpu": 58.1, "ram": 55.0, "vram": 47.2, "encoding": 61.0,
  "networkMbps": 820.5,
  "latencyMs": 18.4, "jitterMs": 2.1, "packetLoss": 0.2,
  "sessions": 31, "requestsPerSec": 4.2, "temperatureC": 67.0,
  "timestamp": 1730000000000
}
```

### ServerNode (GET /api/servers item)
```json
{
  "id": "GS-MUM-01",
  "region": "MUMBAI",
  "city": "Mumbai",
  "capacity": 60,
  "state": "HEALTHY",
  "circuitState": "CLOSED",
  "rmiStatus": "CONNECTED",
  "eligible": true,
  "weight": 1.0,
  "metrics": { "...ServerMetrics..." }
}
```

### GameSession
```json
{
  "id": "S-10001",
  "playerId": "P-1042",
  "game": "NEON STRIKE",
  "serverId": "GS-MUM-03",
  "playerRegion": "Mumbai",
  "resolution": "1080p",
  "fps": 60,
  "state": "ACTIVE",
  "latencyMs": 21.5,
  "startTime": 1730000000000,
  "durationSec": 125
}
```

### CandidateScore (inside RoutingDecision)
```json
{
  "serverId": "GS-MUM-01",
  "latencyMs": 18.0, "cpu": 42.0, "gpu": 51.0,
  "packetLoss": 0.2, "sessions": 31, "jitterMs": 2.0,
  "score": 26.3,
  "scoreBreakdown": {
    "latency": 6.3, "cpu": 8.4, "gpu": 10.2,
    "packetLoss": 0.2, "sessions": 5.2, "jitter": 0.1, "penalty": 0.0
  },
  "eligible": true,
  "penaltyReason": null
}
```
`scoreBreakdown` values are the weighted contributions that sum to `score`
(lower is better). Ineligible candidates have `"eligible": false` and a
`penaltyReason` string, e.g. `"GPU SATURATION (94%)"`, `"CIRCUIT OPEN"`,
`"LATENCY 142ms > 100ms"`, `"SERVER OFFLINE"`, `"PACKET LOSS 7.2% > 5%"`.

### RoutingDecision
```json
{
  "id": "RD-18421",
  "timestamp": 1730000000000,
  "playerId": "P-1042",
  "game": "NEON STRIKE",
  "playerRegion": "Mumbai",
  "resolution": "1080p",
  "fps": 60,
  "strategy": "WEIGHTED_GAMING",
  "candidates": ["...CandidateScore, one per server..."],
  "selectedServerId": "GS-MUM-03",
  "reason": "Lowest eligible composite gaming score (24.1)."
}
```
`selectedServerId` is null when no eligible candidate exists.

### SystemEvent
```json
{
  "id": "EVT-000123",
  "timestamp": 1730000000000,
  "type": "SESSION_CREATED",
  "severity": "INFO",
  "message": "P-1042 → GS-MUM-03",
  "serverId": "GS-MUM-03"
}
```
Common `type` values: `SESSION_CREATED`, `SESSION_TERMINATED`, `SESSION_MIGRATING`,
`SESSION_MIGRATED`, `ROUTING_DECISION`, `GPU_THRESHOLD`, `HEALTH_CHECK_FAILED`,
`SERVER_STATE_CHANGED`, `CIRCUIT_OPEN`, `CIRCUIT_HALF_OPEN`, `CIRCUIT_CLOSED`,
`FAULT_INJECTED`, `FAULT_CLEARED`, `SCENARIO_STARTED`, `SCENARIO_STOPPED`,
`SCENARIO_COMPLETED`, `SCENARIO_CREATED`, `SCENARIO_DELETED`, `SCENARIO_PHASE`,
`SIMULATION_STARTED`, `SIMULATION_PAUSED`, `SIMULATION_RESUMED`, `SIMULATION_RESET`,
`NO_ROUTING_CANDIDATES`, `STRATEGY_CHANGED`.

### GameProfile (GET /api/games item)
```json
{
  "id": "NEON_STRIKE",
  "name": "NEON STRIKE",
  "genre": "Competitive Shooter",
  "latencySensitive": true,
  "gpuLoad": 0.55,
  "vramLoad": 0.45,
  "networkLoad": 0.85,
  "cpuLoad": 0.5
}
```

### Scenario descriptor (GET /api/scenarios item)
```json
{
  "id": "gpu-saturation",
  "name": "GPU Saturation",
  "description": "Ramps GS-MUM-02 GPU 70% → 97%. Routing should shed load.",
  "active": false,
  "targetServerId": "GS-MUM-02",
  "custom": false
}
```
`custom: true` marks user-defined scenarios (created via `POST /api/scenarios/custom`).

### Custom scenario step
```json
{ "type": "WAIT", "seconds": 10 }
{ "type": "SET_TRAFFIC", "targetSessions": 120 }
{ "type": "FAULT", "serverId": "GS-MUM-02", "faultType": "GPU_OVERLOAD" }
{ "type": "RECOVER", "serverId": "GS-MUM-02" }
{ "type": "STRATEGY", "strategy": "LEAST_SESSIONS" }
```
Steps execute in order on a wall-clock timeline: each step's action fires on
entry, then the timeline dwells — `WAIT` steps dwell `seconds`, action steps
dwell 3s so their effects are visible. The scenario stops itself (event
`SCENARIO_COMPLETED`) after the last step.

### MetricPoint (history)
```json
{ "t": 1730000000000, "cpu": 42.3, "gpu": 58.1, "ram": 55.0, "vram": 47.2,
  "encoding": 61.0, "latencyMs": 18.4, "jitterMs": 2.1, "packetLoss": 0.2,
  "sessions": 31, "networkMbps": 820.5, "requestsPerSec": 4.2 }
```

---

## 3. REST API

| Method | Path | Body / Query | Response |
|---|---|---|---|
| GET | `/api/system` | — | `{ "status": "UP", "simulationState": "RUNNING", "speed": 1.0, "time": 1730000000000, "uptimeSec": 342, "strategy": "WEIGHTED_GAMING", "totals": { "activeSessions": 87, "requestsPerSec": 6.4, "avgLatencyMs": 24.1, "healthyServers": 4, "avgGpu": 52.3, "packetLoss": 0.4 } }` |
| GET | `/api/servers` | — | `ServerNode[]` |
| GET | `/api/servers/{id}` | — | `ServerNode` (404 if unknown) |
| GET | `/api/sessions` | `?state=ACTIVE&serverId=GS-MUM-01&search=P-10` (all optional) | `GameSession[]` |
| GET | `/api/sessions/{id}` | — | `GameSession` (404 if unknown) |
| GET | `/api/routing/decisions` | `?limit=50` (default 50, max 200) | `RoutingDecision[]`, newest first |
| GET | `/api/routing/strategy` | — | `{ "strategy": "WEIGHTED_GAMING" }` |
| PUT | `/api/routing/strategy` | `{ "strategy": "LEAST_SESSIONS" }` | `{ "strategy": "LEAST_SESSIONS" }` |
| GET | `/api/events` | `?limit=100&severity=WARN` (optional) | `SystemEvent[]`, newest first |
| GET | `/api/games` | — | `GameProfile[]` |
| GET | `/api/scenarios` | — | scenario descriptor `[]` |
| POST | `/api/scenarios/{id}/start` | — | `{ "started": true, "scenarioId": "..." }` |
| POST | `/api/scenarios/{id}/stop` | — | `{ "stopped": true }` |
| POST | `/api/scenarios/custom` | `{ "name": "...", "description": "...", "steps": [custom scenario steps] }` (1..20 steps, validated) | scenario descriptor (`custom: true`) |
| DELETE | `/api/scenarios/custom/{id}` | — | `{ "deleted": true, "scenarioId": "..." }` (stops it first if running) |
| DELETE | `/api/sessions/{id}` | — | `{ "terminated": true, "sessionId": "..." }` (404 if unknown) |
| POST | `/api/simulation/start` | — | `{ "state": "RUNNING" }` |
| POST | `/api/simulation/pause` | — | `{ "state": "PAUSED" }` |
| POST | `/api/simulation/resume` | — | `{ "state": "RUNNING" }` |
| POST | `/api/simulation/reset` | — | `{ "state": "STOPPED" }` (clears sessions, decisions, events, faults, scenarios) |
| PUT | `/api/simulation/speed` | `{ "speed": 2.0 }` (allowed: 0.5, 1, 2, 5) | `{ "speed": 2.0 }` |
| POST | `/api/servers/{id}/fault` | `{ "type": "GPU_OVERLOAD" }` | `{ "injected": true, "type": "GPU_OVERLOAD" }` |
| POST | `/api/servers/{id}/recover` | — | `{ "recovering": true }` |
| GET | `/api/metrics/history` | `?windowSec=300` (60–600, default 300) | `{ "servers": { "GS-MUM-01": [MetricPoint...], ... }, "aggregate": { "t": [...], "requestsPerSec": [...], "avgLatencyMs": [...], "packetLoss": [...], "throughputMbps": [...] } }` — points sampled ~1/sec, oldest first |

All POST/PUT responses are JSON. Errors: `{ "error": "message" }` with proper HTTP status.

---

## 4. WebSocket — `ws://localhost:8080/ws/events`

Plain JSON text frames, server → client. Every message has `type` and `timestamp`.

| type | payload fields |
|---|---|
| `METRIC_UPDATE` | `serverId`, `metrics` (ServerMetrics) |
| `SERVER_STATE_CHANGED` | `serverId`, `oldState`, `newState` |
| `SESSION_CREATED` | `session` (GameSession) |
| `SESSION_TERMINATED` | `sessionId`, `playerId`, `serverId` |
| `SESSION_MIGRATING` | `sessionId`, `playerId`, `fromServerId`, `toServerId` (toServerId may be null while selecting) |
| `SESSION_MIGRATED` | `sessionId`, `playerId`, `fromServerId`, `toServerId` |
| `ROUTING_DECISION` | `decision` (RoutingDecision — the FULL object, including candidates and breakdown) |
| `CIRCUIT_CHANGED` | `serverId`, `oldState`, `newState` |
| `SYSTEM_EVENT` | `event` (SystemEvent) |
| `SIMULATION_STATE_CHANGED` | `state` (SimulationState), `speed` |

Frontend must seed initial state from REST, then apply WS events. No JS-timer fake data.

---

## 5. Seed data (backend)

Servers:
- `GS-MUM-01`, Mumbai, MUMBAI, capacity 60
- `GS-MUM-02`, Mumbai, MUMBAI, capacity 60
- `GS-SIN-01`, Singapore, SINGAPORE, capacity 80
- `GS-BLR-01`, Bangalore, BANGALORE, capacity 60

Players originate from: Mumbai, Bangalore, Delhi, Singapore, Chennai, Hyderabad.
Base latency matrix (ms, approximate, jitter added by sim):
player Mumbai → GS-MUM-*: ~18, GS-BLR-01: ~35, GS-SIN-01: ~65
player Singapore → GS-SIN-01: ~12, GS-MUM-*: ~65, GS-BLR-01: ~55
player Delhi → GS-MUM-*: ~45, GS-BLR-01: ~50, GS-SIN-01: ~80
player Bangalore → GS-BLR-01: ~10, GS-MUM-*: ~35, GS-SIN-01: ~60
player Chennai → GS-BLR-01: ~20, GS-MUM-*: ~30, GS-SIN-01: ~55
player Hyderabad → GS-MUM-*: ~28, GS-BLR-01: ~25, GS-SIN-01: ~62

Games (GameProfile): NEON STRIKE (competitive shooter, latency-sensitive),
ORBITAL RACER (racer), IRON FRONT (open-world, GPU/VRAM heavy), NIGHT CITY
(open-world RPG, GPU heavy).

Scenarios (ids): `normal-traffic`, `gpu-saturation`, `latency-spike`,
`packet-loss`, `server-failure`, `server-recovery`, `traffic-surge`.

---

## 6. Frontend route map (React Router)

- `/` Overview
- `/topology` Live Topology
- `/sessions` Sessions
- `/servers` Game Servers
- `/routing` Routing (decisions + strategy)
- `/metrics` Metrics (charts)
- `/events` Events
- `/scenarios` Demo Scenarios
- `/architecture` System Architecture
- `/settings` Settings

WebSocket URL: built from `window.location` in dev (`ws://` + host + `/ws/events`);
configurable via `VITE_API_URL` / `VITE_WS_URL` env for production.
