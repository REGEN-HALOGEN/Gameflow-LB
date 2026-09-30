import React from 'react';
import type {
  CircuitState,
  EventSeverity,
  RmiStatus,
  ServerState,
  SessionState,
  SimulationState,
  WsStatus,
} from '../types';

// ---------------------------------------------------------------------------
// Status color maps — color always paired with dot + text label.
// ---------------------------------------------------------------------------

export const SERVER_STATE_COLOR: Record<ServerState, string> = {
  HEALTHY: '#22C55E',
  DEGRADED: '#F59E0B',
  UNHEALTHY: '#EF4444',
  OFFLINE: '#6B7280',
  RECOVERING: '#4F8CFF',
  DRAINING: '#A855F7',
};

export const CIRCUIT_COLOR: Record<CircuitState, string> = {
  CLOSED: '#22C55E',
  OPEN: '#EF4444',
  HALF_OPEN: '#F59E0B',
};

export const RMI_COLOR: Record<RmiStatus, string> = {
  CONNECTED: '#22C55E',
  UNREACHABLE: '#EF4444',
};

export const SESSION_STATE_COLOR: Record<SessionState, string> = {
  CREATING: '#4F8CFF',
  ACTIVE: '#22C55E',
  MIGRATING: '#4F8CFF',
  TERMINATING: '#F59E0B',
  TERMINATED: '#6B7280',
  FAILED: '#EF4444',
};

export const SEVERITY_COLOR: Record<EventSeverity, string> = {
  INFO: '#4F8CFF',
  WARN: '#F59E0B',
  ERROR: '#EF4444',
  CRITICAL: '#EF4444',
};

export const WS_COLOR: Record<WsStatus, string> = {
  CONNECTED: '#22C55E',
  CONNECTING: '#F59E0B',
  DISCONNECTED: '#EF4444',
};

export const SIM_STATE_COLOR: Record<SimulationState, string> = {
  RUNNING: '#22C55E',
  PAUSED: '#F59E0B',
  STOPPED: '#6B7280',
};

// ---------------------------------------------------------------------------

export function StatusDot({ color, size = 7 }: { color: string; size?: number }) {
  return (
    <span
      className="inline-block shrink-0 rounded-full"
      style={{ width: size, height: size, background: color }}
    />
  );
}

/** Small "dot + label" badge used for every state in the UI. */
export function StateBadge({
  color,
  label,
  pulse,
}: {
  color: string;
  label: string;
  pulse?: boolean;
}) {
  return (
    <span className="inline-flex items-center gap-1.5 font-mono text-[10px] tracking-wide whitespace-nowrap">
      <StatusDot color={color} size={6} />
      <span style={{ color }} className={pulse ? 'soft-pulse' : undefined}>
        {label}
      </span>
    </span>
  );
}

export function SectionTitle({
  children,
  right,
}: {
  children: React.ReactNode;
  right?: React.ReactNode;
}) {
  return (
    <div className="flex items-center justify-between mb-2">
      <h2 className="text-[10px] font-semibold uppercase tracking-[0.12em] text-zinc-500">{children}</h2>
      {right}
    </div>
  );
}

/** Compact observability stat: label over a mono value. */
export function StatBlock({
  label,
  value,
  unit,
  color,
  sub,
  tip,
}: {
  label: string;
  value: string;
  unit?: string;
  color?: string;
  sub?: string;
  tip?: string;
}) {
  return (
    <div className={`min-w-0 ${tip ? 'has-tip' : ''}`} data-tip={tip}>
      <div className="text-[10px] font-medium uppercase tracking-[0.1em] text-zinc-500 truncate">
        {label}
      </div>
      <div className="mt-0.5 font-mono text-[19px] leading-6 tnum" style={{ color: color || '#e4e4e7' }}>
        {value}
        {unit && <span className="text-[11px] text-zinc-500 ml-1">{unit}</span>}
      </div>
      {sub && <div className="font-mono text-[10px] text-zinc-600 truncate">{sub}</div>}
    </div>
  );
}

/** Horizontal utilization bar with severity coloring. */
export function MetricBar({
  label,
  value,
  max = 100,
  unit = '%',
  tip,
  warnAt = 75,
  critAt = 90,
}: {
  label: string;
  value: number;
  max?: number;
  unit?: string;
  tip?: string;
  warnAt?: number;
  critAt?: number;
}) {
  const pct = Math.max(0, Math.min(100, (value / max) * 100));
  const color = pct >= critAt ? '#EF4444' : pct >= warnAt ? '#F59E0B' : '#4F8CFF';
  return (
    <div className={tip ? 'has-tip' : ''} data-tip={tip}>
      <div className="flex items-baseline justify-between">
        <span className="text-[10px] uppercase tracking-[0.08em] text-zinc-500">{label}</span>
        <span className="font-mono text-[11px] tnum" style={{ color }}>
          {value.toFixed(1)}
          {unit}
        </span>
      </div>
      <div className="mt-1 h-[3px] bg-line rounded-[2px] overflow-hidden">
        <div
          className="h-full rounded-[2px] transition-[width] duration-500"
          style={{ width: pct + '%', background: color }}
        />
      </div>
    </div>
  );
}

export function EmptyState({
  title,
  hint,
  action,
}: {
  title: string;
  hint?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-center border border-dashed border-line rounded-md">
      <div className="text-[12px] font-medium text-zinc-400">{title}</div>
      {hint && <div className="mt-1 text-[11px] text-zinc-600 max-w-[320px]">{hint}</div>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse bg-panel2 rounded-[4px] ${className}`} />;
}

const BTN_BASE =
  'inline-flex items-center gap-1.5 font-mono text-[11px] px-2.5 h-7 rounded-[4px] border border-line2 bg-panel2 text-zinc-300 hover:bg-[#1a212b] hover:text-zinc-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors';

export function Btn({
  children,
  onClick,
  disabled,
  title,
  accent,
}: {
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  title?: string;
  accent?: 'blue' | 'red' | 'green';
}) {
  const accents: Record<string, string> = {
    blue: 'border-info/40 text-info hover:bg-info/10',
    red: 'border-err/40 text-err hover:bg-err/10',
    green: 'border-ok/40 text-ok hover:bg-ok/10',
  };
  return (
    <button className={`${BTN_BASE} ${accent ? accents[accent] : ''}`} onClick={onClick} disabled={disabled} title={title}>
      {children}
    </button>
  );
}

/** Segmented control */
export function Seg<T extends string>({
  options,
  value,
  onChange,
  labels,
}: {
  options: T[];
  value: T;
  onChange: (v: T) => void;
  labels?: Record<string, string>;
}) {
  return (
    <div className="inline-flex border border-line2 rounded-[4px] overflow-hidden">
      {options.map((o) => (
        <button
          key={o}
          onClick={() => onChange(o)}
          className={`px-2 h-6 font-mono text-[10px] border-r border-line2 last:border-r-0 transition-colors ${
            value === o ? 'bg-[#1a212b] text-zinc-100' : 'text-zinc-500 hover:text-zinc-300'
          }`}
        >
          {labels?.[o] ?? o}
        </button>
      ))}
    </div>
  );
}
