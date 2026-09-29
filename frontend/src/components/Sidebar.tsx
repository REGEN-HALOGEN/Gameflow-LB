import { NavLink } from 'react-router-dom';
import {
  BarChart3,
  Boxes,
  ChevronLeft,
  ChevronRight,
  FlaskConical,
  Gamepad2,
  LayoutDashboard,
  ListOrdered,
  Network,
  Route,
  Server,
  Settings,
} from 'lucide-react';

const ITEMS = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/topology', label: 'Live Topology', icon: Network },
  { to: '/sessions', label: 'Sessions', icon: Gamepad2 },
  { to: '/servers', label: 'Game Servers', icon: Server },
  { to: '/routing', label: 'Routing', icon: Route },
  { to: '/metrics', label: 'Metrics', icon: BarChart3 },
  { to: '/events', label: 'Events', icon: ListOrdered },
  { to: '/scenarios', label: 'Demo Scenarios', icon: FlaskConical },
  { to: '/architecture', label: 'System Architecture', icon: Boxes },
  { to: '/settings', label: 'Settings', icon: Settings },
];

export default function Sidebar({
  collapsed,
  onToggle,
}: {
  collapsed: boolean;
  onToggle: () => void;
}) {
  return (
    <aside
      className={`shrink-0 bg-panel border-r border-line flex flex-col transition-[width] duration-150 ${
        collapsed ? 'w-[52px]' : 'w-[196px]'
      }`}
    >
      <nav className="flex-1 py-2 overflow-y-auto" aria-label="Primary">
        {ITEMS.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            title={collapsed ? label : undefined}
            className={({ isActive }) =>
              `flex items-center gap-2.5 px-3 h-9 text-[12px] border-l-2 transition-colors ${
                collapsed ? 'justify-center px-0' : ''
              } ${
                isActive
                  ? 'border-info bg-[#141a21] text-zinc-100'
                  : 'border-transparent text-zinc-500 hover:text-zinc-200 hover:bg-panel2'
              }`
            }
          >
            <Icon size={15} className="shrink-0" />
            {!collapsed && <span className="truncate">{label}</span>}
          </NavLink>
        ))}
      </nav>
      <button
        onClick={onToggle}
        className="h-9 flex items-center justify-center text-zinc-600 hover:text-zinc-300 border-t border-line"
        title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      >
        {collapsed ? <ChevronRight size={15} /> : <ChevronLeft size={15} />}
      </button>
    </aside>
  );
}
