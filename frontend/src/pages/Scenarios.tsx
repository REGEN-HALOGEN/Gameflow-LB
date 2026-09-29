import { useState } from 'react';
import { ArrowDown, ArrowUp, FlaskConical, Plus, Square, Trash2, X } from 'lucide-react';
import * as api from '../api';
import { useSim } from '../state/sim';
import { BackendDown } from './Overview';
import { Btn, SectionTitle, Skeleton, StateBadge } from '../components/ui';
import type {
  CustomScenarioRequest,
  CustomStep,
  CustomStepType,
  FaultType,
  RoutingStrategy,
  Scenario,
} from '../types';

const FAULTS: { type: FaultType; label: string }[] = [
  { type: 'GPU_OVERLOAD', label: 'GPU Overload' },
  { type: 'LATENCY_SPIKE', label: 'Latency Spike' },
  { type: 'PACKET_LOSS', label: 'Packet Loss' },
  { type: 'RMI_FAILURE', label: 'RMI Failure' },
  { type: 'CRASH', label: 'Crash Server' },
];

function ScenarioCard({ sc }: { sc: Scenario }) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const { refreshScenarios } = useSim();

  const toggle = async () => {
    setBusy(true);
    setErr(null);
    try {
      if (sc.active) await api.stopScenario(sc.id);
      else await api.startScenario(sc.id);
      // Authoritative SCENARIO_STARTED/STOPPED events arrive over WS, but
      // refresh anyway in case the event was missed on a flaky socket.
      setTimeout(refreshScenarios, 400);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Scenario request failed');
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!window.confirm(`Delete custom scenario "${sc.name}"?`)) return;
    setBusy(true);
    setErr(null);
    try {
      await api.deleteCustomScenario(sc.id);
      refreshScenarios();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Delete failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className={`border rounded-[6px] p-3 bg-panel transition-colors ${
        sc.active ? 'border-warn/50' : 'border-line hover:border-line2'
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <FlaskConical size={13} className={sc.active ? 'text-warn' : 'text-zinc-600'} />
          <span className="text-[12px] font-semibold text-zinc-200">{sc.name}</span>
        </div>
        <div className="flex items-center gap-1.5">
          {sc.custom && (
            <span className="font-mono text-[9px] px-1.5 py-0.5 rounded-[3px] border border-info/40 text-info">
              CUSTOM
            </span>
          )}
          {sc.active && <StateBadge color="#F59E0B" label="ACTIVE" pulse />}
          {sc.custom && !sc.active && (
            <button
              onClick={remove}
              disabled={busy}
              className="p-1 text-zinc-600 hover:text-err rounded-[3px] disabled:opacity-40"
              title="Delete custom scenario"
              aria-label={'Delete ' + sc.name}
            >
              <Trash2 size={12} />
            </button>
          )}
        </div>
      </div>
      <p className="mt-1.5 text-[11px] leading-relaxed text-zinc-500">{sc.description}</p>
      <div className="mt-2 flex items-center justify-between">
        <span className="font-mono text-[10px] text-zinc-600">
          {sc.targetServerId ? 'target ' + sc.targetServerId : 'fleet-wide'} · {sc.id}
        </span>
        <button
          onClick={toggle}
          disabled={busy}
          className={`inline-flex items-center gap-1.5 font-mono text-[10px] px-2.5 h-7 rounded-[4px] border transition-colors disabled:opacity-40 ${
            sc.active
              ? 'border-err/40 text-err hover:bg-err/10'
              : 'border-line2 text-zinc-300 hover:bg-panel2'
          }`}
        >
          {sc.active ? <Square size={10} /> : null}
          {busy ? '…' : sc.active ? 'STOP' : 'START'}
        </button>
      </div>
      {err && <div className="mt-1.5 font-mono text-[10px] text-err">{err}</div>}
    </div>
  );
}

const STEP_TYPES: { type: CustomStepType; label: string }[] = [
  { type: 'WAIT', label: 'Wait' },
  { type: 'SET_TRAFFIC', label: 'Set traffic' },
  { type: 'FAULT', label: 'Inject fault' },
  { type: 'RECOVER', label: 'Recover server' },
  { type: 'STRATEGY', label: 'Switch strategy' },
];

const STRATEGY_IDS: RoutingStrategy[] = [
  'WEIGHTED_GAMING',
  'LEAST_SESSIONS',
  'LOWEST_LATENCY',
  'ROUND_ROBIN',
];

function defaultStep(type: CustomStepType, serverId: string): CustomStep {
  switch (type) {
    case 'WAIT':
      return { type, seconds: 10 };
    case 'SET_TRAFFIC':
      return { type, targetSessions: 90 };
    case 'FAULT':
      return { type, serverId, faultType: 'GPU_OVERLOAD' };
    case 'RECOVER':
      return { type, serverId };
    case 'STRATEGY':
      return { type, strategy: 'LEAST_SESSIONS' };
  }
}

function describeStep(s: CustomStep): string {
  switch (s.type) {
    case 'WAIT':
      return `wait ${s.seconds ?? 0}s`;
    case 'SET_TRAFFIC':
      return `traffic → ${s.targetSessions ?? 0} sessions`;
    case 'FAULT':
      return `fault ${s.faultType} on ${s.serverId}`;
    case 'RECOVER':
      return `recover ${s.serverId}`;
    case 'STRATEGY':
      return `strategy → ${s.strategy}`;
  }
}

const inputCls =
  'h-7 bg-panel2 border border-line2 rounded-[4px] font-mono text-[11px] text-zinc-200 px-2';

function StepEditor({
  step,
  index,
  serverIds,
  onChange,
  onRemove,
  onMove,
  isFirst,
  isLast,
}: {
  step: CustomStep;
  index: number;
  serverIds: string[];
  onChange: (s: CustomStep) => void;
  onRemove: () => void;
  onMove: (dir: -1 | 1) => void;
  isFirst: boolean;
  isLast: boolean;
}) {
  const set = (patch: Partial<CustomStep>) => onChange({ ...step, ...patch });
  return (
    <div className="flex items-center gap-2 border border-line rounded-[4px] bg-panel2/50 px-2 py-1.5 flex-wrap">
      <span className="font-mono text-[10px] text-zinc-600 w-6 tnum">{index + 1}</span>
      <select
        value={step.type}
        onChange={(e) => onChange(defaultStep(e.target.value as CustomStepType, serverIds[0] || ''))}
        className={inputCls}
        aria-label={'Step ' + (index + 1) + ' type'}
      >
        {STEP_TYPES.map((t) => (
          <option key={t.type} value={t.type}>
            {t.label}
          </option>
        ))}
      </select>

      {step.type === 'WAIT' && (
        <label className="flex items-center gap-1.5 font-mono text-[10px] text-zinc-500">
          seconds
          <input
            type="number"
            min={1}
            max={600}
            value={step.seconds ?? 10}
            onChange={(e) => set({ seconds: Math.max(1, Math.min(600, Number(e.target.value) || 1)) })}
            className={`${inputCls} w-[70px]`}
          />
        </label>
      )}
      {step.type === 'SET_TRAFFIC' && (
        <label className="flex items-center gap-1.5 font-mono text-[10px] text-zinc-500">
          target sessions
          <input
            type="number"
            min={0}
            max={1000}
            value={step.targetSessions ?? 90}
            onChange={(e) =>
              set({ targetSessions: Math.max(0, Math.min(1000, Number(e.target.value) || 0)) })
            }
            className={`${inputCls} w-[80px]`}
          />
        </label>
      )}
      {(step.type === 'FAULT' || step.type === 'RECOVER') && (
        <select
          value={step.serverId || ''}
          onChange={(e) => set({ serverId: e.target.value })}
          className={inputCls}
          aria-label="Step target server"
        >
          {serverIds.map((id) => (
            <option key={id} value={id}>
              {id}
            </option>
          ))}
        </select>
      )}
      {step.type === 'FAULT' && (
        <select
          value={step.faultType || 'GPU_OVERLOAD'}
          onChange={(e) => set({ faultType: e.target.value as FaultType })}
          className={inputCls}
          aria-label="Fault type"
        >
          {FAULTS.map((f) => (
            <option key={f.type} value={f.type}>
              {f.label}
            </option>
          ))}
        </select>
      )}
      {step.type === 'STRATEGY' && (
        <select
          value={step.strategy || 'WEIGHTED_GAMING'}
          onChange={(e) => set({ strategy: e.target.value as RoutingStrategy })}
          className={inputCls}
          aria-label="Routing strategy"
        >
          {STRATEGY_IDS.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      )}

      <span className="flex-1" />
      <span className="font-mono text-[10px] text-zinc-600 hidden lg:inline">{describeStep(step)}</span>
      <button
        onClick={() => onMove(-1)}
        disabled={isFirst}
        className="p-1 text-zinc-600 hover:text-zinc-200 disabled:opacity-30"
        aria-label="Move step up"
      >
        <ArrowUp size={12} />
      </button>
      <button
        onClick={() => onMove(1)}
        disabled={isLast}
        className="p-1 text-zinc-600 hover:text-zinc-200 disabled:opacity-30"
        aria-label="Move step down"
      >
        <ArrowDown size={12} />
      </button>
      <button
        onClick={onRemove}
        className="p-1 text-zinc-600 hover:text-err"
        aria-label="Remove step"
      >
        <X size={12} />
      </button>
    </div>
  );
}

function CustomScenarioBuilder() {
  const { serverIds, refreshScenarios } = useSim();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [steps, setSteps] = useState<CustomStep[]>([]);
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const addStep = () =>
    setSteps((s) => [...s, defaultStep('WAIT', serverIds[0] || '')]);

  const move = (i: number, dir: -1 | 1) =>
    setSteps((s) => {
      const j = i + dir;
      if (j < 0 || j >= s.length) return s;
      const next = [...s];
      [next[i], next[j]] = [next[j], next[i]];
      return next;
    });

  const save = async () => {
    setErr(null);
    if (!name.trim()) {
      setErr('Give the scenario a name.');
      return;
    }
    if (steps.length === 0) {
      setErr('Add at least one step.');
      return;
    }
    setSaving(true);
    try {
      const body: CustomScenarioRequest = {
        name: name.trim(),
        description: description.trim() || undefined,
        steps,
      };
      await api.createCustomScenario(body);
      setName('');
      setDescription('');
      setSteps([]);
      setOpen(false);
      refreshScenarios();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Create failed');
    } finally {
      setSaving(false);
    }
  };

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="mt-6 inline-flex items-center gap-1.5 font-mono text-[11px] px-3 h-8 rounded-[4px] border border-dashed border-line2 text-zinc-400 hover:text-zinc-100 hover:border-zinc-500 transition-colors"
      >
        <Plus size={13} /> Build a custom scenario
      </button>
    );
  }

  return (
    <section className="mt-6 border border-line rounded-[6px] bg-panel p-3">
      <div className="flex items-center justify-between mb-3">
        <SectionTitle>Custom scenario builder</SectionTitle>
        <button
          onClick={() => setOpen(false)}
          className="p-1 text-zinc-600 hover:text-zinc-200"
          aria-label="Close builder"
        >
          <X size={13} />
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-2 mb-3">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Scenario name — e.g. prime-time failover drill"
          maxLength={80}
          className={`${inputCls} h-8`}
          aria-label="Scenario name"
        />
        <input
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Description (optional)"
          className={`${inputCls} h-8`}
          aria-label="Scenario description"
        />
      </div>

      <div className="space-y-1.5 mb-3">
        {steps.map((s, i) => (
          <StepEditor
            key={i}
            step={s}
            index={i}
            serverIds={serverIds}
            onChange={(ns) => setSteps((all) => all.map((x, j) => (j === i ? ns : x)))}
            onRemove={() => setSteps((all) => all.filter((_, j) => j !== i))}
            onMove={(dir) => move(i, dir)}
            isFirst={i === 0}
            isLast={i === steps.length - 1}
          />
        ))}
        {steps.length === 0 && (
          <div className="font-mono text-[10px] text-zinc-600 border border-dashed border-line rounded-[4px] px-3 py-4 text-center">
            no steps yet — add the first one below
          </div>
        )}
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        <Btn onClick={addStep}>
          <Plus size={12} /> Add step
        </Btn>
        <div className="flex-1" />
        {err && <span className="font-mono text-[10px] text-err">{err}</span>}
        <Btn accent="green" onClick={save} disabled={saving || steps.length === 0}>
          {saving ? 'saving…' : 'Save scenario'}
        </Btn>
      </div>
      <p className="mt-2 font-mono text-[10px] text-zinc-600">
        steps run in order on a wall-clock timeline · action steps pause 3s so effects are
        visible · use WAIT steps for pacing · the scenario stops itself when the last step completes
      </p>
    </section>
  );
}

export default function Scenarios() {
  const { ready, backendUp, scenarios, serverIds } = useSim();
  const [target, setTarget] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const server = target || serverIds[0] || '';

  const inject = async (type: FaultType) => {
    if (!server) return;
    setBusy(type);
    setErr(null);
    try {
      await api.injectFault(server, type);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Fault injection failed');
    } finally {
      setBusy(null);
    }
  };

  const recover = async () => {
    if (!server) return;
    setBusy('RECOVER');
    setErr(null);
    try {
      await api.recoverServer(server);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Recover failed');
    } finally {
      setBusy(null);
    }
  };

  if (!ready) return <div className="p-4"><Skeleton className="h-[300px]" /></div>;
  if (!backendUp) return <div className="p-4"><BackendDown /></div>;

  return (
    <div className="p-4 max-w-[1100px]">
      <h1 className="text-[13px] font-semibold text-zinc-200">Demo Scenarios</h1>
      <p className="font-mono text-[10px] text-zinc-600 mt-0.5 mb-3">
        deterministic scenario scripts that manipulate real simulation state — for live demonstrations
      </p>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
        {scenarios.map((sc) => (
          <ScenarioCard key={sc.id} sc={sc} />
        ))}
      </div>
      {scenarios.length === 0 && (
        <div className="font-mono text-[11px] text-zinc-600">no scenarios reported by backend</div>
      )}

      <CustomScenarioBuilder />

      <section className="mt-6">
        <SectionTitle>Manual fault injection</SectionTitle>
        <div className="border border-line rounded-[6px] bg-panel p-3 flex items-center gap-2 flex-wrap">
          <span className="font-mono text-[10px] text-zinc-500 uppercase tracking-[0.08em]">target</span>
          <select
            value={server}
            onChange={(e) => setTarget(e.target.value)}
            className="h-7 bg-panel2 border border-line2 rounded-[4px] font-mono text-[11px] text-zinc-200 px-2"
            aria-label="Fault target server"
          >
            {serverIds.map((id) => (
              <option key={id} value={id}>{id}</option>
            ))}
          </select>
          <div className="w-px h-5 bg-line" />
          {FAULTS.map((f) => (
            <Btn key={f.type} accent="red" disabled={busy !== null || !server} onClick={() => inject(f.type)}>
              {busy === f.type ? '…' : f.label}
            </Btn>
          ))}
          <Btn accent="green" disabled={busy !== null || !server} onClick={recover}>
            {busy === 'RECOVER' ? '…' : 'Recover'}
          </Btn>
        </div>
        {err && <div className="mt-2 font-mono text-[10px] text-err">{err}</div>}
        <p className="mt-2 font-mono text-[10px] text-zinc-600">
          faults are injected into the java simulation — watch topology, routing and events react
        </p>
      </section>
    </div>
  );
}
