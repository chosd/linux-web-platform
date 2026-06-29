import { useState } from 'react';

import { AppMenuKey, AppShell } from '/src/layouts/app-shell';
import { TerminalView } from '/src/features/terminal/components/terminal-view';
import { ContainerCrudPage } from '/src/pages/container-crud-page';

type SelectedContainer = {
  containerName: string;
  displayName: string;
};

export function App() {
  const [activeMenu, setActiveMenu] = useState<AppMenuKey>('containers');
  const [selectedContainer, setSelectedContainer] = useState<SelectedContainer | null>(null);

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
    <AppShell activeMenu={activeMenu} onSelectMenu={setActiveMenu}>
      {activeMenu === 'containers' && (
        <ContainerCrudPage
          onConnectTerminal={(containerName, displayName) => setSelectedContainer({ containerName, displayName })}
        />
      )}
    </AppShell>
  );
}
