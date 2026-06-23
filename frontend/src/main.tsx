import React from 'react';
import ReactDOM from 'react-dom/client';
import { useState } from 'react';
import { ContainerDashboard } from './containers/ContainerDashboard';
import { TerminalView } from './terminal/TerminalView';
import './styles.css';

type SelectedContainer = {
  containerName: string;
  displayName: string;
};

function App() {
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

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
