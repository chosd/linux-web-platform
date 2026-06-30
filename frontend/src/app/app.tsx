import { useState } from 'react';

import { AppMenuKey, AppShell } from '/src/layouts/app-shell';
import { TerminalView } from '/src/features/terminal/components/terminal-view';
import { ContainerCrudPage } from '/src/pages/container-crud-page';
import { DashboardPage } from '/src/pages/dashboard-page';
import { FileExplorerPage } from '/src/pages/file-explorer-page';
import { useHostResourceStats } from '/src/features/dashboard/lib/use-host-resource-stats';

type SelectedContainer = {
  containerName: string;
  displayName: string;
};

export function App() {
  const [activeMenu, setActiveMenu] = useState<AppMenuKey>('dashboard');
  const [selectedContainer, setSelectedContainer] = useState<SelectedContainer | null>(null);
  const hostResourceState = useHostResourceStats();

  if (selectedContainer) {
    return (
      <TerminalView
        containerName={selectedContainer.containerName}
        displayName={selectedContainer.displayName}
        onBack={() => setSelectedContainer(null)}
      />
    );
  }

  return (
    <AppShell activeMenu={activeMenu} hostResourceStats={hostResourceState.stats} onSelectMenu={setActiveMenu}>
      {activeMenu === 'dashboard' && (
        <DashboardPage
          hostResourceStats={hostResourceState.stats}
          isLoadingHostResources={hostResourceState.isLoading}
          hostResourceErrorMessage={hostResourceState.errorMessage}
          onRefreshHostResources={hostResourceState.reload}
        />
      )}
      {activeMenu === 'containers' && (
        <ContainerCrudPage
          onConnectTerminal={(containerName, displayName) => setSelectedContainer({ containerName, displayName })}
        />
      )}
      {activeMenu === 'files' && <FileExplorerPage />}
    </AppShell>
  );
}
