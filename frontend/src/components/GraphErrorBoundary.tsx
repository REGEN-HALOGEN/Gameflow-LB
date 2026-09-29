import React from 'react';

/**
 * Catches render crashes inside the topology graph (e.g. a node component
 * throwing on unexpected server data during fault injection) so the whole
 * panel doesn't go blank. Shows a recoverable fallback instead.
 */
export class GraphErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { error: Error | null }
> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error) {
    // eslint-disable-next-line no-console
    console.error('[TopologyGraph] render crash:', error);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="h-full flex flex-col items-center justify-center gap-2 text-[12px] text-zinc-500">
          <span>Graph render hit an error and was reset.</span>
          <button
            className="px-3 py-1 rounded border border-line2 text-zinc-300 hover:border-zinc-500 font-mono text-[11px]"
            onClick={() => this.setState({ error: null })}
          >
            Retry
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
