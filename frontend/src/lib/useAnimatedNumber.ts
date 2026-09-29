import { useEffect, useRef, useState } from 'react';

/** Smoothly animates toward `target` — for live stat counters. */
export function useAnimatedNumber(target: number, duration = 600): number {
  const [value, setValue] = useState(target);
  const from = useRef(target);
  const raf = useRef(0);

  useEffect(() => {
    const start = from.current;
    if (start === target) return;
    const t0 = performance.now();
    const tick = (now: number) => {
      const t = Math.min(1, (now - t0) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      const v = start + (target - start) * eased;
      setValue(v);
      from.current = v;
      if (t < 1) raf.current = requestAnimationFrame(tick);
    };
    raf.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf.current);
  }, [target, duration]);

  return value;
}
