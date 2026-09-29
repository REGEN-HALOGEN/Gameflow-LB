import { useState } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { SimulationProvider, useSim } from './state/sim';
import TopBar from './components/TopBar';
import Sidebar from './components/Sidebar';
import ServerDrawer from './components/ServerDrawer';
import DecisionInspector from './components/DecisionInspector';
import Overview from './pages/Overview';
import TopologyPage from './pages/TopologyPage';
import Sessions from './pages/Sessions';
import Servers from './pages/Servers';
import Routing from './pages/Routing';
import Metrics from './pages/Metrics';
import Events from './pages/Events';
import Scenarios from './pages/Scenarios';
import Architecture from './pages/Architecture';
import Settings from './pages/Settings';

function WsBanner() {
  const { wsStatus, ready, backendUp } = useSim();
  if (!ready || !backendUp || wsStatus !== 'DISCONNECTED') return null;
  return (
    <div className="shrink-0 h-8 px-4 flex items-center gap-2 bg-warn/10 border-b border-warn/30 font-mono text-[11px] text-warn">
      <AlertTriangle size={12} />
      <span>WebSocket disconnected — reconnecting with backoff. Live updates paused; last known state shown.</span>
    </div>
  );
}

function Shell() {
  const [collapsed, setCollapsed] = useState(false);
  return (
    <div className="h-screen flex flex-col bg-ink text-zinc-200 overflow-hidden">
      <TopBar />
      <WsBanner />
      <div className="flex-1 flex min-h-0">
        <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((c) => !c)} />
        <main className="flex-1 min-w-0 overflow-y-auto" id="main">
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/topology" element={<TopologyPage />} />
            <Route path="/sessions" element={<Sessions />} />
            <Route path="/servers" element={<Servers />} />
            <Route path="/routing" element={<Routing />} />
            <Route path="/metrics" element={<Metrics />} />
            <Route path="/events" element={<Events />} />
            <Route path="/scenarios" element={<Scenarios />} />
            <Route path="/architecture" element={<Architecture />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="*" element={<Overview />} />
          </Routes>
        </main>
      </div>
      <ServerDrawer />
      <DecisionInspector />
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <SimulationProvider>
        <Shell />
      </SimulationProvider>
    </BrowserRouter>
  );
}
