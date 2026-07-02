import { useEffect, useState } from 'react';

import {
  ContainerNetworkDashboard,
  getContainerNetworkDashboard
} from '/src/features/containers/lib/container-api-client';
import { ErrorBanner } from '/src/shared/components/feedback';

import styles from './container-components.module.css';

type ContainerNetworkDrawerProps = {
  containerName: string | null;
  displayName?: string;
  isOpen: boolean;
  onClose: () => void;
};

export function ContainerNetworkDrawer({ containerName, displayName, isOpen, onClose }: ContainerNetworkDrawerProps) {
  const [dashboard, setDashboard] = useState<ContainerNetworkDashboard | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!containerName || !isOpen) {
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
  }, [containerName, isOpen]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  return (
    <div className={`${styles.networkDrawerRoot} ${isOpen ? styles.networkDrawerRootOpen : ''}`} aria-hidden={!isOpen}>
      <button type="button" className={styles.networkDrawerBackdrop} aria-label="Close network drawer" onClick={onClose} />
      <aside className={styles.networkDrawer} aria-label="Container network details">
        <header className={styles.networkDrawerHeader}>
          <div>
            <span>Network</span>
            <h2>{displayName || '컨테이너 네트워크'}</h2>
            <p>{containerName || '선택된 컨테이너가 없습니다.'}</p>
          </div>
          <button type="button" className={styles.drawerCloseButton} aria-label="Close" onClick={onClose}>
            ×
          </button>
        </header>

        {errorMessage ? (
          <ErrorBanner>{errorMessage}</ErrorBanner>
        ) : isLoading ? (
          <div className={styles.drawerEmptyState}>Loading network...</div>
        ) : (
          <div className={styles.networkDrawerContent}>
            <section className={styles.networkCard}>
              <h3>IP 주소</h3>
              {dashboard?.networks.length ? (
                <div className={styles.networkList}>
                  {dashboard.networks.map((network) => (
                    <div className={styles.networkRow} key={`${network.name}-${network.ipAddress}`}>
                      <strong>{network.name}</strong>
                      <span>{network.ipAddress || 'unassigned'}</span>
                      <small>{network.gateway ? `gateway ${network.gateway}` : 'no gateway'}</small>
                    </div>
                  ))}
                </div>
              ) : (
                <div className={styles.emptyInline}>연결된 네트워크가 없습니다.</div>
              )}
            </section>

            <section className={styles.networkCard}>
              <h3>포트 포워딩</h3>
              {dashboard?.ports.length ? (
                <div className={styles.portMappingList}>
                  {dashboard.ports.map((port) => (
                    <div className={styles.portMappingRow} key={`${port.protocol}-${port.hostPort}-${port.containerPort}`}>
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
                <div className={styles.emptyInline}>노출된 포트가 없습니다.</div>
              )}
            </section>
          </div>
        )}
      </aside>
    </div>
  );
}
