import TopologyGraph from '../components/TopologyGraph';
import { GraphErrorBoundary } from '../components/GraphErrorBoundary';
import { useSim } from '../state/sim';
import { BackendDown } from './Overview';
import { Skeleton } from '../components/ui';

export default function TopologyPage() {
  const { ready, backendUp, activeRoute, decisions } = useSim();

  const last = activeRoute ? decisions.find((d) => d.id === activeRoute.decisionId) : undefined;

  return (
    <div className="h-full flex flex-col">
      <div className="px-4 pt-3 pb-2 flex items-center justify-between shrink-0">
        <div>
          <h1 className="text-[13px] font-semibold text-zinc-200">Live Topology</h1>
          <p className="font-mono text-[10px] text-zinc-600 mt-0.5">
            player requests → load balancer → routing engine → game servers (RMI)
          </p>
        </div>
        {last?.selectedServerId && (
          <div className="font-mono text-[10px] text-zinc-500 tnum">
            last route <span className="text-info">{last.playerId}</span> →{' '}
            <span className="text-zinc-200">{last.selectedServerId}</span>{' '}
            <span className="text-zinc-600">{last.id}</span>
          </div>
        )}
      </div>
      <div className="flex-1 min-h-0 px-4 pb-4">
        {!ready ? (
          <Skeleton className="h-full" />
        ) : !backendUp ? (
          <BackendDown />
        ) : (
          <div className="h-full border border-line rounded-[6px] overflow-hidden bg-ink">
            <GraphErrorBoundary>
              <TopologyGraph />
            </GraphErrorBoundary>
          </div>
        )}
      </div>
    </div>
  );
}
