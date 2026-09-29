// Rolling chart buffers kept OUTSIDE React state so the UI stays smooth at 5x.
// Seeded from GET /api/metrics/history on connect, appended on each metric flush
// (~2/sec). Charts read from here on a 1s render tick and slice by time window.

import type { HistoryPayload, MetricPoint, ServerMetrics } from '../types';

const MAX_POINTS = 600;

export interface Totals {
  activeSessions: number;
  requestsPerSec: number;
  avgLatencyMs: number;
  healthyServers: number;
  avgGpu: number;
  packetLoss: number;
}

export const serverHistory = new Map<string, MetricPoint[]>();

export const aggHistory = {
  t: [] as number[],
  requestsPerSec: [] as number[],
  avgLatencyMs: [] as number[],
  packetLoss: [] as number[],
  throughputMbps: [] as number[],
};

function trim<T>(arr: T[]): T[] {
  if (arr.length > MAX_POINTS) arr.splice(0, arr.length - MAX_POINTS);
  return arr;
}

export function seedHistory(h: HistoryPayload): void {
  serverHistory.clear();
  for (const [id, pts] of Object.entries(h.servers || {})) {
    serverHistory.set(id, trim((pts || []).slice()));
  }
  const a = h.aggregate || { t: [], requestsPerSec: [], avgLatencyMs: [], packetLoss: [], throughputMbps: [] };
  aggHistory.t = trim((a.t || []).slice());
  aggHistory.requestsPerSec = trim((a.requestsPerSec || []).slice());
  aggHistory.avgLatencyMs = trim((a.avgLatencyMs || []).slice());
  aggHistory.packetLoss = trim((a.packetLoss || []).slice());
  aggHistory.throughputMbps = trim((a.throughputMbps || []).slice());
}

export function pushMetric(serverId: string, m: ServerMetrics): void {
  let arr = serverHistory.get(serverId);
  if (!arr) {
    arr = [];
    serverHistory.set(serverId, arr);
  }
  arr.push({
    t: m.timestamp,
    cpu: m.cpu,
    gpu: m.gpu,
    ram: m.ram,
    vram: m.vram,
    encoding: m.encoding,
    latencyMs: m.latencyMs,
    jitterMs: m.jitterMs,
    packetLoss: m.packetLoss,
    sessions: m.sessions,
    networkMbps: m.networkMbps,
    requestsPerSec: m.requestsPerSec,
  });
  trim(arr);
}

export function pushAggregate(t: number, totals: Totals, throughputMbps: number): void {
  aggHistory.t.push(t);
  aggHistory.requestsPerSec.push(totals.requestsPerSec);
  aggHistory.avgLatencyMs.push(totals.avgLatencyMs);
  aggHistory.packetLoss.push(totals.packetLoss);
  aggHistory.throughputMbps.push(throughputMbps);
  trim(aggHistory.t);
  trim(aggHistory.requestsPerSec);
  trim(aggHistory.avgLatencyMs);
  trim(aggHistory.packetLoss);
  trim(aggHistory.throughputMbps);
}

/** Slice aggregate series to [since, +inf), returned as row objects for recharts. */
export function aggRows(since: number): Array<Record<string, number>> {
  const out: Array<Record<string, number>> = [];
  const n = aggHistory.t.length;
  for (let i = 0; i < n; i++) {
    const t = aggHistory.t[i];
    if (t < since) continue;
    out.push({
      t,
      requestsPerSec: aggHistory.requestsPerSec[i],
      avgLatencyMs: aggHistory.avgLatencyMs[i],
      packetLoss: aggHistory.packetLoss[i],
      throughputMbps: aggHistory.throughputMbps[i],
    });
  }
  return out;
}

/** Per-server series for one metric key, pivoted to rows: { t, [serverId]: value }. */
export function serverRows(
  serverIds: string[],
  key: keyof MetricPoint,
  since: number,
): Array<Record<string, number>> {
  const per = serverIds.map((id) => {
    const arr = serverHistory.get(id) || [];
    let lo = 0;
    while (lo < arr.length && arr[lo].t < since) lo++;
    return { id, pts: arr.slice(lo) };
  });
  const maxLen = Math.max(0, ...per.map((p) => p.pts.length));
  const out: Array<Record<string, number>> = [];
  for (let i = 0; i < maxLen; i++) {
    const row: Record<string, number> = {};
    let t = 0;
    let hasAny = false;
    for (const p of per) {
      const pt = p.pts[i]; // undefined when this server has fewer points — renders a gap
      if (!pt) continue;
      hasAny = true;
      t = Math.max(t, pt.t);
      row[p.id] = pt[key] as number;
    }
    if (!hasAny) continue;
    row.t = t;
    out.push(row);
  }
  return out;
}
