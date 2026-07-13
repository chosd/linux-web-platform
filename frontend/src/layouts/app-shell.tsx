import { ReactNode } from 'react';

import { HostResourceStatsSample } from '/src/features/dashboard/lib/dashboard-api-client';
import { useTheme } from '/src/shared/contexts/theme-context';

import styles from './app-shell.module.css';

export type AppMenuKey = 'dashboard' | 'containers' | 'images';

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
    label: 'Containers',
    description: 'Create and manage'
  },
  {
    key: 'images',
    label: 'Images',
    description: 'Local and registry'
  }
];

export function AppShell({ activeMenu, children, hostResourceStats, onSelectMenu }: AppShellProps) {
  const { isDark, toggleTheme } = useTheme();

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <div className={styles.brandArea}>
          <div className={styles.brand}>
            <strong>Linux Terminal</strong>
            <span>Platform</span>
          </div>
        </div>
        <nav className={styles.topNav} aria-label="Main navigation">
          {navItems.map((item) => (
            <button
              key={item.key}
              type="button"
              className={`${styles.navTab} ${activeMenu === item.key ? styles.navTabActive : ''}`}
              onClick={() => onSelectMenu(item.key)}
            >
              <span>{item.label}</span>
              <small>{item.description}</small>
            </button>
          ))}
        </nav>
      </aside>
      <header className={styles.topbar}>
        <div className={styles.topbarTitle}><strong>{navItems.find((item) => item.key === activeMenu)?.label}</strong><span>Docker workspace</span></div>
        <div className={styles.topbarTools}>
          <ResourceSummary stats={hostResourceStats} />
          <button
            type="button"
            className={styles.themeToggle}
            aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
            aria-pressed={isDark}
            onClick={toggleTheme}
          >
            <span>{isDark ? 'Light' : 'Dark'}</span>
          </button>
        </div>
      </header>
      <div className={styles.content}>{children}</div>
    </div>
  );
}

type ResourceSummaryProps = {
  stats?: HostResourceStatsSample | null;
};

function ResourceSummary({ stats }: ResourceSummaryProps) {
  return (
    <section className={styles.resourceSummary} aria-label="Host resource summary">
      <div className={styles.resourceItem}>
        <div>
          <span>CPU</span>
          <strong>{stats ? `${stats.cpuPercent.toFixed(1)}%` : '-'}</strong>
        </div>
        <div className={styles.resourceTrack}>
          <span style={{ width: `${stats ? clampPercent(stats.cpuPercent) : 0}%` }} />
        </div>
      </div>
      <div className={styles.resourceItem}>
        <div>
          <span>Memory</span>
          <strong>{stats ? `${stats.memoryPercent.toFixed(1)}%` : '-'}</strong>
        </div>
        <div className={styles.resourceTrack}>
          <span style={{ width: `${stats ? clampPercent(stats.memoryPercent) : 0}%` }} />
        </div>
      </div>
    </section>
  );
}

function clampPercent(value: number) {
  return Math.max(0, Math.min(100, value));
}
