import { ReactNode } from 'react';

export type AppMenuKey = 'containers';

type AppShellProps = {
  activeMenu: AppMenuKey;
  children: ReactNode;
  onSelectMenu: (menu: AppMenuKey) => void;
};

const navItems: Array<{ key: AppMenuKey; label: string; description: string }> = [
  {
    key: 'containers',
    label: '컨테이너',
    description: 'CRUD'
  }
];

export function AppShell({ activeMenu, children, onSelectMenu }: AppShellProps) {
  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Main navigation">
        <div className="sidebar-brand">
          <strong>Linux Terminal</strong>
          <span>Platform</span>
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
      </aside>
      <div className="app-content">{children}</div>
    </div>
  );
}
