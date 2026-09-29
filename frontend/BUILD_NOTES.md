# GameFlow LB — Frontend Build Notes

React 18 + Vite 5 + TypeScript + Tailwind 3 console for the GameFlow LB
cloud-gaming load balancer simulator. Implements exactly the REST/WebSocket
contract in `~/workspace/gameflow-lb/CONTRACT.md`.

## Run

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173 — proxies /api and /ws to localhost:8080
npm run build    # tsc + vite build -> dist/
```

Requires the Java backend on `http://localhost:8080` (Spring Boot).
Without it, every page shows a "Backend unavailable" empty state with a retry
button — nothing renders fake data.

Production overrides: `VITE_API_URL` (REST base, default `/api`),
`VITE_WS_URL` (default `ws(s)://<host>/ws/events`).

## Architecture

- `src/config.ts` — API/WS URL resolution.
- `src/types.ts` — contract types, verbatim from CONTRACT.md.
- `src/api.ts` — typed REST client + `fetchSeed()` (9 parallel seed calls).
- `src/state/sim.tsx` — `SimulationProvider` (context + useReducer).
  Native WebSocket with exponential-backoff reconnect. On connect it seeds from
  REST (`/api/system`, `/api/servers`, `/api/sessions`, decisions, events,
  strategy, scenarios, games, metrics history), then applies WS frames.
  `METRIC_UPDATE` frames are buffered in a ref and flushed to state at most
  ~2/sec; fleet totals are derived from the flushed metrics.
- `src/lib/charts.ts` — rolling chart buffers (max 600 pts) kept **outside**
  React state. Seeded from `/api/metrics/history`, appended on each flush.
  Chart pages re-render on a 1s tick and slice by window.
- `src/components/TopologyGraph.tsx` — React Flow topology. Custom server
  nodes (state badge, CPU/GPU bars, sessions, latency, loss) and a custom
  `traffic` edge with hover telemetry (req/s, latency, sessions, bandwidth).
  On each `ROUTING_DECISION` the edge to the selected server flashes with an
  animated dash for ~1.5s — only that edge, never decorative.
- Global overlays: `ServerDrawer` (metrics, sparkline, sessions, decisions,
  events, fault injection), `SessionPanel`, `DecisionInspector` (per-candidate
  scores + expandable weighted breakdown, ineligible candidates with penalty
  reasons, NO-CANDIDATE state).

## Pages (routes per CONTRACT §6)

`/` Overview · `/topology` · `/sessions` · `/servers` · `/routing` ·
`/metrics` · `/events` · `/scenarios` · `/architecture` · `/settings`

## Integrator notes / known behaviors

- **Strategy switching** (`PUT /api/routing/strategy`) does not optimistically
  update local state; it waits for the backend's `STRATEGY_CHANGED` WS event,
  which triggers a `GET /api/routing/strategy` refetch. Same for scenarios:
  `SCENARIO_STARTED/STOPPED` events trigger a `GET /api/scenarios` refetch, so
  the `active` flags always reflect the backend.
- **Session duration**: `durationSec` comes from the backend snapshot; rows
  re-render as WS events arrive.
- **Event log** keeps the last 500 events, decisions the last 200, sessions the
  last 500 (client-side caps; backend is authoritative).
- **Migration**: `SESSION_MIGRATING` sets session state to MIGRATING;
  `SESSION_MIGRATED` moves it to the new server and back to ACTIVE.
- **No-candidate decisions** (`selectedServerId: null`) render in red as
  "NO CANDIDATE" in feeds and get a dedicated warning block in the inspector.
- **Paused simulation**: WS still streams; metric bars freeze because the
  backend stops advancing values. Charts have their own "pause live updates"
  toggle independent of simulation state.
- **5x speed**: metric flush stays at 2/sec; chart buffers cap at 600 points
  (~5 min at 2/sec); topology nodes are memoized.
- Timestamps render in the browser's local timezone; event log uses
  `HH:MM:SS.mmm`.
- Bundle is ~900 kB (recharts + reactflow); acceptable for a desktop ops
  console, code-splitting not applied.
- `npm run build` passes (`tsc` strict + vite). Dev server verified to serve
  200 on :5173; never left running.
