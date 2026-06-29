import { useState } from 'react';

import { ContainerDashboard } from '/src/features/containers/components/container-dashboard';
import { TerminalView } from '/src/features/terminal/components/terminal-view';

type SelectedContainer = {
  containerName: string;
  displayName: string;
};

export function App() {
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
    <ContainerDashboard
      onConnectTerminal={(containerName, displayName) => setSelectedContainer({ containerName, displayName })}
    />
  );
}
