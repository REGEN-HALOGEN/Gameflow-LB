# GameFlow LB — Backend Build Notes

For the frontend/integrator. The full API + WebSocket contract is
`~/workspace/gameflow-lb/CONTRACT.md` — this backend implements it exactly.

## Ports & URLs

- HTTP/WebSocket: `http://localhost:8080` (REST under `/api/*`, WS at `/ws/events`)
- RMI registry: port `1099`, bind names `gameflow/GS-MUM-01` … `gameflow/GS-BLR-01`
  (full URL form `rmi://127.0.0.1:1099/gameflow/GS-MUM-01`)
- `java.rmi.server.hostname` defaults to `127.0.0.1` (set in `GameFlowApplication`).
  Override with `-Djava.rmi.server.hostname=<host>` if the frontend ever runs
  on another machine (it doesn't need RMI, but stubs carry this address).

## Run

```bash
cd backend
mvn spring-boot:run          # backend on :8080
```

Requires Java 21 and Maven. No database, no other services.

## Behavior quirks the frontend must know

1. **Simulation starts STOPPED.** Nothing routes until `POST /api/simulation/start`.
   Metric ticks (1/sec) and health checks (2/sec) run in every state, so the UI
   gets live `METRIC_UPDATE`s immediately — but with zero sessions until started.
2. **Player-specific latency.** `CandidateScore.latencyMs` is computed per player
   (geography matrix + injected fault offset + jitter), not the server's
   self-measured `metrics.latencyMs`. Both are exposed; don't mix them up.
3. **Session IDs** look like `S-10001`, decisions `RD-00001`, events `EVT-000123`.
4. **ROUTING_DECISION events are chatty.** Every routing decision emits both a
   `ROUTING_DECISION` WebSocket message (full object) and a `SYSTEM_EVENT`
   wrapping a `SystemEvent` of type `ROUTING_DECISION`. At 5x speed with a
   traffic surge this can be dozens per second — the event ring buffer holds 500.
5. **Migration flow.** On server failure the backend emits `SESSION_MIGRATING`
   with `toServerId: null`, then a `ROUTING_DECISION`, then `SESSION_MIGRATED`.
   If no replacement exists the session goes to `FAILED` (terminal, kept in the
   store so the UI can show it).
6. **Recovery path.** `POST /api/servers/{id}/recover` → state `RECOVERING`,
   circuit stays `OPEN` for up to 10s → `HALF_OPEN` probe → `CLOSED`, and the
   server needs 2 consecutive passing health checks to reach `HEALTHY`.
   New sessions only route to it once `eligible: true`.
7. **Stopping the `server-failure` scenario does NOT restore the server.**
   Use `POST /api/servers/{id}/recover` (or simulation reset). Other scenarios
   clear their fault overrides on stop.
8. **`PUT /api/routing/strategy`** accepts exactly the four enum names;
   anything else is a 400.
9. **History** (`GET /api/metrics/history`) is sampled ~1/sec per server,
   kept for ~10 minutes; `windowSec` is clamped to 60–600.
10. **RMI is real but in-JVM.** The registry, stubs (`Naming.lookup`) and
    unexport/unbind on crash are genuine RMI — just co-located in one process
    for the demo. `RMI_FAILURE` makes the impl's methods throw `RemoteException`
    while staying bound; `CRASH` unexports + unbinds.

## Thresholds (routing exclusion)

- GPU > 90% → ineligible (`"GPU SATURATION (94%)"`)
- Packet loss > 5% → ineligible (`"PACKET LOSS 7.2% > 5%"`)
- Player latency > 100ms → ineligible (`"LATENCY 142ms > 100ms"`)
- Circuit `OPEN`, or state `UNHEALTHY`/`OFFLINE`/`RECOVERING` → ineligible
- Weighted score: latency .35, cpu .20, gpu .20, packetLoss .10, sessions .10,
  jitter .05, scaled 0–100; ineligible candidates take a +25 penalty (still shown
  with their breakdown so the UI can explain the rejection).

## Timings

- Metric pull: 1s · Health check: 2s · Traffic tick: 1000/speed ms
- Circuit: 3 consecutive failures → OPEN; 10s → HALF_OPEN; probe decides
- Health: 1–2 failures → DEGRADED; ≥3 → UNHEALTHY; RemoteException → OFFLINE

## Tests

`mvn test` — covers weighted-score math, all exclusion rules, latency
dominance, circuit-breaker transitions (incl. half-open probe success/failure),
session creation/failover/migration, health state transitions, and RMI failure
→ OFFLINE via a Mockito-thrown `RemoteException`.
