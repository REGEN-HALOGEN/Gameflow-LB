import { useEffect, useRef } from 'react';
import { useReactFlow, useViewport } from 'reactflow';
import { useSim } from '../state/sim';

// ---------------------------------------------------------------------------
// PacketFlow — a canvas overlay that animates live traffic as particles:
//   players → load balancer → routing engine → game server (over RMI).
//
// Particles spawn at a rate proportional to each server's live requests/sec,
// so the graph literally shows where the load is going. Colors follow server
// health; a red spark burst fires when a server drops. Pure canvas, cheap.
// ---------------------------------------------------------------------------

type P = {
  path: { x: number; y: number }[]; // flow coordinates
  segLen: number[];
  total: number;
  d: number; // distance travelled
  speed: number; // flow-units per second
  color: string;
  size: number;
  jitter: number;
};

const MAX_PARTICLES = 420;

function healthColor(state: string, circuit: string): string {
  if (state === 'OFFLINE') return '#EF4444';
  if (circuit === 'OPEN') return '#F59E0B';
  if (state === 'HEALTHY') return '#4F8CFF';
  if (state === 'DEGRADED') return '#F59E0B';
  return '#EF4444';
}

export default function PacketFlow() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const { getNodes } = useReactFlow();
  const viewport = useViewport();
  const sim = useSim();

  // Mirror the bits the animation loop needs, without re-subscribing it.
  const live = useRef(sim);
  live.current = sim;
  const vp = useRef(viewport);
  vp.current = viewport;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let raf = 0;
    let last = performance.now();
    const particles: P[] = [];
    const prevState = new Map<string, string>();
    let spawnAcc = 0;

    const resize = () => {
      const parent = canvas.parentElement;
      if (!parent) return;
      const r = parent.getBoundingClientRect();
      const dpr = Math.min(2, window.devicePixelRatio || 1);
      canvas.width = Math.max(1, Math.floor(r.width * dpr));
      canvas.height = Math.max(1, Math.floor(r.height * dpr));
      canvas.style.width = r.width + 'px';
      canvas.style.height = r.height + 'px';
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    resize();
    const ro = new ResizeObserver(resize);
    if (canvas.parentElement) ro.observe(canvas.parentElement);

    const nodeCenter = (id: string, edge: 'top' | 'bottom') => {
      const n = getNodes().find((x) => x.id === id);
      if (!n || n.width == null || n.height == null) return null;
      const cx = n.position.x + n.width / 2;
      return { x: cx, y: edge === 'top' ? n.position.y : n.position.y + n.height };
    };

    const buildPath = (serverId: string) => {
      const p0 = nodeCenter('players', 'bottom');
      const p1 = nodeCenter('lb', 'top');
      const p2 = nodeCenter('lb', 'bottom');
      const p3 = nodeCenter('router', 'top');
      const p4 = nodeCenter('router', 'bottom');
      const p5 = nodeCenter('srv-' + serverId, 'top');
      if (!p0 || !p1 || !p2 || !p3 || !p4 || !p5) return null;
      return [p0, p1, p2, p3, p4, p5];
    };

    const spawn = (serverId: string, color: string) => {
      if (particles.length >= MAX_PARTICLES) particles.shift();
      const path = buildPath(serverId);
      if (!path) return;
      const segLen: number[] = [];
      let total = 0;
      for (let i = 1; i < path.length; i++) {
        const dx = path[i].x - path[i - 1].x;
        const dy = path[i].y - path[i - 1].y;
        const l = Math.hypot(dx, dy);
        segLen.push(l);
        total += l;
      }
      particles.push({
        path,
        segLen,
        total,
        d: 0,
        speed: 260 + Math.random() * 140,
        color,
        size: 1.6 + Math.random() * 1.8,
        jitter: (Math.random() - 0.5) * 14,
      });
    };

    /** Red sparks that fly outward from a dying server. */
    const burst = (serverId: string) => {
      const c = nodeCenter('srv-' + serverId, 'top');
      if (!c) return;
      for (let i = 0; i < 26 && particles.length < MAX_PARTICLES; i++) {
        const a = Math.random() * Math.PI * 2;
        const r = 20 + Math.random() * 90;
        const path = [
          { x: c.x, y: c.y + 10 },
          { x: c.x + Math.cos(a) * r, y: c.y + 10 + Math.sin(a) * r * 0.6 },
        ];
        particles.push({
          path,
          segLen: [r],
          total: r,
          d: 0,
          speed: 120 + Math.random() * 160,
          color: '#EF4444',
          size: 1.5 + Math.random() * 2,
          jitter: 0,
        });
      }
    };

    const frame = (now: number) => {
      raf = requestAnimationFrame(frame);
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      const { servers, serverIds, simState } = live.current;
      const { x: vx, y: vy, zoom } = vp.current;

      // Spawn: rate follows live traffic per server.
      if (simState === 'RUNNING') {
        for (const id of serverIds) {
          const s = servers[id];
          if (!s) continue;
          const rps = s.metrics?.requestsPerSec ?? 0;
          // ~1 particle per 2 req/s, min trickle so healthy links stay alive.
          const rate = s.state === 'OFFLINE' ? 0 : 1.2 + rps * 0.55;
          spawnAcc += rate * dt;
          while (spawnAcc >= 1) {
            spawnAcc -= 1;
            spawn(id, healthColor(s.state, s.circuitState));
          }
          // Fault burst on state transitions into trouble.
          const prev = prevState.get(id);
          if (prev && prev !== s.state && (s.state === 'OFFLINE' || s.state === 'UNHEALTHY')) {
            burst(id);
          }
          prevState.set(id, s.state);
        }
      }

      const w = canvas.width / Math.min(2, window.devicePixelRatio || 1);
      const h = canvas.height / Math.min(2, window.devicePixelRatio || 1);
      ctx.clearRect(0, 0, w, h);
      ctx.globalCompositeOperation = 'lighter';

      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.d += p.speed * dt;
        if (p.d >= p.total) {
          particles.splice(i, 1);
          continue;
        }
        // Walk segments.
        let d = p.d;
        let seg = 0;
        while (seg < p.segLen.length - 1 && d > p.segLen[seg]) {
          d -= p.segLen[seg];
          seg++;
        }
        const a = p.path[seg];
        const b = p.path[seg + 1];
        const t = p.segLen[seg] === 0 ? 0 : d / p.segLen[seg];
        const fx = a.x + (b.x - a.x) * t;
        const fy = a.y + (b.y - a.y) * t;
        // Flow → screen.
        const sx = fx * zoom + vx + (seg >= 2 ? p.jitter * Math.sin(p.d * 0.05) : 0);
        const sy = fy * zoom + vy;
        const fade = 1 - p.d / p.total;
        ctx.globalAlpha = Math.min(1, fade * 1.6) * 0.9;
        ctx.fillStyle = p.color;
        ctx.beginPath();
        ctx.arc(sx, sy, p.size, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;
      ctx.globalCompositeOperation = 'source-over';
    };

    raf = requestAnimationFrame(frame);
    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
    };
    // getNodes is stable; viewport + sim are mirrored via refs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <canvas
      ref={canvasRef}
      style={{
        position: 'absolute',
        inset: 0,
        zIndex: 10,
        pointerEvents: 'none',
      }}
      aria-hidden
    />
  );
}
