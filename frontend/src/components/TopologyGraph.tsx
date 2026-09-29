import React, { memo, useMemo } from 'react';
import ReactFlow, {
  BaseEdge,
  Controls,
  EdgeLabelRenderer,
  Handle,
  Position,
  getBezierPath,
  type Edge,
  type EdgeProps,
  type Node,
  type NodeProps,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { useSim } from '../state/sim';
import { fmtInt } from '../lib/format';
import { SERVER_STATE_COLOR, StateBadge } from './ui';
import type { ServerNode as ServerNodeT } from '../types';

// ---------------------------------------------------------------------------
// Custom nodes
// ---------------------------------------------------------------------------

const nodeShell =
  'bg-panel border border-line2 rounded-[6px] px-3 py-2 w-[228px] select-none';

function PlayersNode({ data }: NodeProps<{ rps: number; flashing: boolean }>) {
  return (
    <div className={`${nodeShell} text-center`}>
      <Handle type="source" position={Position.Bottom} style={{ background: '#4F8CFF' }} />
      <div className="text-[10px] font-semibold uppercase tracking-[0.12em] text-zinc-400">
        Player Requests
      </div>
      <div className="mt-1 flex items-center justify-center gap-2">
        <span
          className={`inline-block h-[7px] w-[7px] rounded-full bg-info ${data.flashing ? 'soft-pulse' : ''}`}
        />
        <span className="font-mono text-[15px] tnum text-zinc-100">{data.rps.toFixed(1)}</span>
        <span className="font-mono text-[10px] text-zinc-500">req/s</span>
      </div>
    </div>
  );
}

function LbNode() {
  return (
    <div className={`${nodeShell} text-center`}>
      <Handle type="target" position={Position.Top} style={{ background: '#4F8CFF' }} />
      <Handle type="source" position={Position.Bottom} style={{ background: '#4F8CFF' }} />
      <div className="text-[10px] font-semibold uppercase tracking-[0.12em] text-zinc-400">
        Load Balancer
      </div>
      <div className="font-mono text-[10px] text-zinc-600 mt-0.5">spring-boot · rmi client</div>
    </div>
  );
}

function RouterNode({ data }: NodeProps<{ strategy: string }>) {
  return (
    <div className={`${nodeShell} text-center`} style={{ borderColor: '#4F8CFF55' }}>
      <Handle type="target" position={Position.Top} style={{ background: '#4F8CFF' }} />
      <Handle type="source" position={Position.Bottom} style={{ background: '#4F8CFF' }} id="out" />
      <div className="text-[10px] font-semibold uppercase tracking-[0.12em] text-info">
        Routing Engine
      </div>
      <div className="font-mono text-[10px] text-zinc-500 mt-0.5">{data.strategy}</div>
    </div>
  );
}

const ServerNodeView = memo(function ServerNodeView({
  data,
}: NodeProps<{ serverId: string }>) {
  // Read the live server from context instead of taking a snapshot in `data`:
  // that keeps the node's data reference stable across metric flushes so
  // React Flow doesn't tear down and rebuild the graph at 2Hz.
  const { servers } = useSim();
  const s = servers[data.serverId];
  if (!s) return null;
  // Metrics can be momentarily absent during fault transitions / re-seeds;
  // render a placeholder instead of crashing the whole graph.
  const m = s.metrics ?? {
    cpu: 0, gpu: 0, ram: 0, vram: 0, encoding: 0,
    networkMbps: 0, latencyMs: 0, jitterMs: 0, packetLoss: 0,
    sessions: 0, requestsPerSec: 0,
  };
  const bar = (v: number) => {
    const c = v >= 90 ? '#EF4444' : v >= 75 ? '#F59E0B' : '#4F8CFF';
    return (
      <div className="flex-1 h-[3px] bg-line rounded-[2px] overflow-hidden">
        <div className="h-full rounded-[2px]" style={{ width: Math.min(100, v) + '%', background: c }} />
      </div>
    );
  };
  const dimmed = s.state === 'OFFLINE';
  return (
    <div
      className={`${nodeShell} cursor-pointer hover:border-zinc-500 transition-colors ${dimmed ? 'opacity-60' : ''}`}
      style={
        s.circuitState === 'OPEN'
          ? { borderColor: '#EF444455' }
          : s.state !== 'HEALTHY'
            ? { borderColor: '#F59E0B44' }
            : undefined
      }
    >
      <Handle type="target" position={Position.Top} style={{ background: '#4F8CFF' }} />
      <div className="flex items-center justify-between">
        <span className="font-mono text-[11px] font-bold text-zinc-100">{s.id}</span>
        <StateBadge color={SERVER_STATE_COLOR[s.state]} label={s.state} />
      </div>
      <div className="font-mono text-[10px] text-zinc-500 mt-0.5">
        {s.city} · {s.region}
      </div>
      <div className="mt-1.5 space-y-1">
        <div className="flex items-center gap-1.5">
          <span className="font-mono text-[9px] text-zinc-600 w-7">CPU</span>
          {bar(m.cpu)}
          <span className="font-mono text-[9px] text-zinc-400 w-8 text-right tnum">{fmtInt(m.cpu)}%</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="font-mono text-[9px] text-zinc-600 w-7">GPU</span>
          {bar(m.gpu)}
          <span className="font-mono text-[9px] text-zinc-400 w-8 text-right tnum">{fmtInt(m.gpu)}%</span>
        </div>
      </div>
      <div className="mt-1.5 flex items-center justify-between font-mono text-[9px] text-zinc-500 tnum">
        <span>
          sess <span className="text-zinc-300">{m.sessions}/{s.capacity}</span>
        </span>
        <span>{m.latencyMs.toFixed(0)} ms</span>
        <span>loss {m.packetLoss.toFixed(1)}%</span>
      </div>
      {s.circuitState !== 'CLOSED' && (
        <div className="mt-1 font-mono text-[9px] tracking-wide" style={{ color: s.circuitState === 'OPEN' ? '#EF4444' : '#F59E0B' }}>
          CIRCUIT {s.circuitState}
        </div>
      )}
    </div>
  );
});

// ---------------------------------------------------------------------------
// Custom edge: routing-engine -> server, with hover telemetry + route flash
// ---------------------------------------------------------------------------

function TrafficEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
}: EdgeProps) {
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  });
  const [hover, setHover] = React.useState(false);
  const active = !!data?.active;
  // Live server for the hover tooltip — read from context so the edge's `data`
  // stays a stable { serverId, active } reference across metric flushes.
  const { servers } = useSim();
  const s: ServerNodeT | undefined = data?.serverId ? servers[data.serverId] : undefined;
  return (
    <>
      <path
        d={edgePath}
        fill="none"
        stroke="transparent"
        strokeWidth={20}
        style={{ pointerEvents: 'stroke', cursor: 'crosshair' }}
        onMouseEnter={() => setHover(true)}
        onMouseLeave={() => setHover(false)}
      />
      <BaseEdge
        id={id}
        path={edgePath}
        style={{
          stroke: active ? '#4F8CFF' : '#2A323D',
          strokeWidth: active ? 2.5 : 1.5,
          strokeDasharray: active ? '7 5' : undefined,
          animation: active ? 'edgeflow 0.55s linear infinite' : undefined,
        }}
      />
      {hover && s && (
        <EdgeLabelRenderer>
          <div
            className="absolute z-50 pointer-events-none bg-panel2 border border-line2 rounded-[4px] px-2 py-1.5 font-mono text-[10px] text-zinc-300 whitespace-nowrap"
            style={{ transform: `translate(-50%,-130%) translate(${labelX}px,${labelY}px)` }}
          >
            <div className="text-zinc-100 font-bold mb-0.5">{s.id}</div>
            <div className="tnum">req/s <span className="text-zinc-100">{s.metrics.requestsPerSec.toFixed(1)}</span></div>
            <div className="tnum">latency <span className="text-zinc-100">{s.metrics.latencyMs.toFixed(1)} ms</span></div>
            <div className="tnum">sessions <span className="text-zinc-100">{s.metrics.sessions}/{s.capacity}</span></div>
            <div className="tnum">bw <span className="text-zinc-100">{s.metrics.networkMbps.toFixed(0)} Mbps</span></div>
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

const nodeTypes = {
  players: PlayersNode,
  lb: LbNode,
  router: RouterNode,
  server: ServerNodeView,
};
const edgeTypes = { traffic: TrafficEdge };

// ---------------------------------------------------------------------------

export default function TopologyGraph({ compact }: { compact?: boolean }) {
  const { serverIds, totals, strategy, activeRoute, openDrawer, simState } = useSim();

  const nodes: Node[] = useMemo(() => {
    const list: Node[] = [
      {
        id: 'players',
        type: 'players',
        position: { x: 510, y: 10 },
        data: { rps: totals.requestsPerSec, flashing: !!activeRoute },
      },
      { id: 'lb', type: 'lb', position: { x: 510, y: 120 }, data: {} },
      {
        id: 'router',
        type: 'router',
        position: { x: 510, y: 225 },
        data: { strategy },
      },
    ];
    const xs = [90, 400, 710, 1020];
    serverIds.forEach((id, i) => {
      list.push({
        id: 'srv-' + id,
        type: 'server',
        position: { x: xs[i % xs.length], y: 380 },
        // Stable data: the node component reads the live server from context.
        data: { serverId: id },
      });
    });
    return list;
    // NOTE: intentionally not depending on `servers` — server values stream in
    // via context inside the node component; depending on it rebuilt the whole
    // graph on every 500ms metric flush.
  }, [serverIds, totals.requestsPerSec, strategy, activeRoute]);

  const edges: Edge[] = useMemo(() => {
    const list: Edge[] = [
      { id: 'e-players-lb', source: 'players', target: 'lb', style: { stroke: '#2A323D', strokeWidth: 1.5 } },
      { id: 'e-lb-router', source: 'lb', target: 'router', style: { stroke: '#2A323D', strokeWidth: 1.5 } },
    ];
    for (const id of serverIds) {
      list.push({
        id: 'rs-' + id,
        source: 'router',
        target: 'srv-' + id,
        type: 'traffic',
        // Stable data: the edge component reads the live server from context.
        data: { serverId: id, active: activeRoute?.serverId === id },
      });
    }
    return list;
    // NOTE: intentionally not depending on `servers` (see nodes above).
  }, [serverIds, activeRoute]);

  if (serverIds.length === 0) {
    return (
      <div className="h-full flex items-center justify-center text-[12px] text-zinc-600">
        {simState === 'STOPPED' ? 'No servers yet — start the simulation.' : 'Loading topology…'}
      </div>
    );
  }

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      onNodeClick={(_, n) => {
        if (n.type === 'server') openDrawer(n.data.serverId as string);
      }}
      nodesDraggable={false}
      nodesConnectable={false}
      fitView
      fitViewOptions={{ padding: 0.08 }}
      minZoom={0.4}
      maxZoom={1.6}
      proOptions={{ hideAttribution: true }}
      zoomOnScroll={!compact}
    >
      {!compact && <Controls showInteractive={false} position="bottom-right" />}
    </ReactFlow>
  );
}
