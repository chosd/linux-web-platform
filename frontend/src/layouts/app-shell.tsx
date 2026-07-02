import { ReactNode } from 'react';

import { HostResourceStatsSample } from '/src/features/dashboard/lib/dashboard-api-client';
import { useTheme } from '/src/shared/contexts/theme-context';

export type AppMenuKey = 'dashboard' | 'containers' | 'files';

type AppShellProps = {
  activeMenu: AppMenuKey;
  children: ReactNode;
  hostResourceStats?: HostResourceStatsSample | null;
  onSelectMenu: (menu: AppMenuKey) => void;
};

const navItems: Array<{ key: AppMenuKey; label: string; description: string }> = [
  {
    key: 'dashboard',
    label: '대시보드',
    description: 'Host resources'
  },
  {
    key: 'containers',
    label: '컨테이너 관리',
    description: 'CRUD'
  },
  {
    key: 'files',
    label: '파일 탐색기',
    description: 'Upload / Download'
  }
];

export function AppShell({ activeMenu, children, hostResourceStats, onSelectMenu }: AppShellProps) {
  const { isDark, toggleTheme } = useTheme();

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Main navigation">
        <div className="sidebar-header">
          <div className="sidebar-brand">
            <strong>Linux Terminal</strong>
            <span>Platform</span>
          </div>
          <button
            type="button"
            className="theme-toggle-button"
            aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
            aria-pressed={isDark}
            onClick={toggleTheme}
          >
            <span>{isDark ? 'Light' : 'Dark'}</span>
          </button>
        </div>
        <nav className="sidebar-nav">
          {navItems.map((item) => (
            <button
              key={item.key}
              type="button"
              className={`sidebar-tab ${activeMenu === item.key ? 'sidebar-tab-active' : ''}`}
              onClick={() => onSelectMenu(item.key)}
            >
              <span>{item.label}</span>
              <small>{item.description}</small>
            </button>
          ))}
        </nav>
        <SidebarResourceSummary stats={hostResourceStats} />
      </aside>
      <div className="app-content">{children}</div>
    </div>
  );
}

type SidebarResourceSummaryProps = {
  stats?: HostResourceStatsSample | null;
};

function SidebarResourceSummary({ stats }: SidebarResourceSummaryProps) {
  return (
    <section className="sidebar-resource-summary" aria-label="Host resource summary">
      <div>
        <span>Host CPU</span>
        <strong>{stats ? `${stats.cpuPercent.toFixed(1)}%` : '-'}</strong>
      </div>
      <div className="sidebar-resource-track">
        <span style={{ width: `${stats ? clampPercent(stats.cpuPercent) : 0}%` }} />
      </div>
      <div>
        <span>Host Memory</span>
        <strong>{stats ? `${stats.memoryPercent.toFixed(1)}%` : '-'}</strong>
      </div>
      <div className="sidebar-resource-track">
        <span style={{ width: `${stats ? clampPercent(stats.memoryPercent) : 0}%` }} />
      </div>
    </section>
  );
}

function clampPercent(value: number) {
  return Math.max(0, Math.min(100, value));
}
