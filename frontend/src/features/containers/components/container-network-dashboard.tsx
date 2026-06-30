import { useEffect, useState } from 'react';

import {
  ContainerNetworkDashboard,
  getContainerNetworkDashboard
} from '/src/features/containers/lib/container-api-client';

type ContainerNetworkDashboardPanelProps = {
  containerName: string | null;
  displayName?: string;
};

export function ContainerNetworkDashboardPanel({ containerName, displayName }: ContainerNetworkDashboardPanelProps) {
  const [dashboard, setDashboard] = useState<ContainerNetworkDashboard | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!containerName) {
      setDashboard(null);
      setErrorMessage('');
      return;
    }

    let isActive = true;
    setIsLoading(true);
    setErrorMessage('');
    getContainerNetworkDashboard(containerName)
      .then((response) => {
        if (isActive) {
          setDashboard(response);
        }
      })
      .catch((error) => {
        if (isActive) {
          setErrorMessage(error instanceof Error ? error.message : 'Failed to load network dashboard.');
        }
      })
      .finally(() => {
        if (isActive) {
          setIsLoading(false);
        }
      });

    return () => {
      isActive = false;
    };
  }, [containerName]);

  return (
    <section className="network-panel">
      <header className="panel-header">
        <div>
          <h2>네트워크 대시보드</h2>
          <p>{displayName || '컨테이너를 선택하면 IP와 포트 매핑이 표시됩니다.'}</p>
        </div>
      </header>

      {!containerName ? (
        <div className="empty-state">네트워크 정보를 확인할 컨테이너를 선택하세요.</div>
      ) : errorMessage ? (
        <div className="error-banner">{errorMessage}</div>
      ) : isLoading ? (
        <div className="empty-state">Loading network...</div>
      ) : (
        <div className="network-dashboard-grid">
          <div className="network-card">
            <h3>IP 주소</h3>
            {dashboard?.networks.length ? (
              <div className="network-list">
                {dashboard.networks.map((network) => (
                  <div className="network-row" key={`${network.name}-${network.ipAddress}`}>
                    <strong>{network.name}</strong>
                    <span>{network.ipAddress || 'unassigned'}</span>
                    <small>{network.gateway ? `gateway ${network.gateway}` : 'no gateway'}</small>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty-inline">네트워크 없음</div>
            )}
          </div>

          <div className="network-card">
            <h3>포트 포워딩</h3>
            {dashboard?.ports.length ? (
              <div className="port-mapping-list">
                {dashboard.ports.map((port) => (
                  <div className="port-mapping-row" key={`${port.protocol}-${port.hostPort}-${port.containerPort}`}>
                    <span>
                      {port.containerPort}/{port.protocol.toLowerCase()}
                    </span>
                    {port.url ? (
                      <a href={port.url} target="_blank" rel="noreferrer">
                        localhost:{port.hostPort}
                      </a>
                    ) : (
                      <small>not published</small>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty-inline">노출된 포트 없음</div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
