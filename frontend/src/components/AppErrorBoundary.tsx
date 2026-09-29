import React from 'react';

/** Last-resort boundary: if anything above the page level crashes,
 *  show a recoverable screen instead of a blank app. */
export class AppErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { error: Error | null }
> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error) {
    // eslint-disable-next-line no-console
    console.error('[App] uncaught render crash:', error);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="h-screen flex flex-col items-center justify-center gap-3 bg-ink text-zinc-300">
          <div className="font-mono text-[13px] text-red-400">Something crashed the UI.</div>
          <div className="font-mono text-[11px] text-zinc-600 max-w-[520px] text-center px-4">
            {String(this.state.error.message || this.state.error).slice(0, 200)}
          </div>
          <button
            className="px-4 py-1.5 rounded border border-line2 hover:border-zinc-500 font-mono text-[12px]"
            onClick={() => window.location.reload()}
          >
            Reload app
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
