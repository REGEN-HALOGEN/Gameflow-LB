// Number / time formatting helpers. All numeric values rendered in mono font.

export function fmt(n: number, digits = 1): string {
  if (!isFinite(n)) return '—';
  return n.toFixed(digits);
}

export function fmtInt(n: number): string {
  if (!isFinite(n)) return '—';
  return Math.round(n).toString();
}

export function fmtMs(n: number, digits = 1): string {
  return fmt(n, digits) + ' ms';
}

export function fmtPct(n: number, digits = 1): string {
  return fmt(n, digits) + '%';
}

function pad2(n: number): string {
  return n < 10 ? '0' + n : '' + n;
}

/** HH:MM:SS in local time */
export function fmtTime(t: number): string {
  const d = new Date(t);
  return pad2(d.getHours()) + ':' + pad2(d.getMinutes()) + ':' + pad2(d.getSeconds());
}

/** HH:MM:SS.mmm in local time — for the ops event log */
export function fmtTimeMs(t: number): string {
  const d = new Date(t);
  return fmtTime(t) + '.' + String(d.getMilliseconds()).padStart(3, '0');
}

/** 125 -> "2m 05s", 3725 -> "1h 02m" */
export function fmtDuration(sec: number): string {
  if (!isFinite(sec) || sec < 0) return '—';
  const s = Math.floor(sec);
  if (s < 60) return s + 's';
  const m = Math.floor(s / 60);
  if (m < 60) return m + 'm ' + pad2(s % 60) + 's';
  const h = Math.floor(m / 60);
  return h + 'h ' + pad2(m % 60) + 'm';
}

export function fmtUptime(sec: number): string {
  return fmtDuration(sec);
}

/** Compact relative "12s ago" */
export function fmtAgo(t: number, now: number): string {
  const s = Math.max(0, Math.floor((now - t) / 1000));
  if (s < 5) return 'just now';
  if (s < 60) return s + 's ago';
  const m = Math.floor(s / 60);
  if (m < 60) return m + 'm ago';
  return Math.floor(m / 60) + 'h ago';
}
