import { useEffect, useState } from 'react';

/**
 * Re-render every `ms` while active. Returns a monotonically increasing
 * counter so callers can recompute time-windowed data on each tick —
 * charts read from rolling buffers kept outside React state.
 */
export function useTick(ms: number, active = true): number {
  const [n, setN] = useState(0);
  useEffect(() => {
    if (!active) return;
    const t = setInterval(() => setN((x) => x + 1), ms);
    return () => clearInterval(t);
  }, [ms, active]);
  return n;
}
