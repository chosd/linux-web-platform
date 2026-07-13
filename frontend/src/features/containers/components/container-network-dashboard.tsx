import { useEffect, useMemo, useRef, useState } from 'react';

import {
  changeContainerNetwork,
  ContainerNetworkDashboard,
  createDockerNetwork,
  DockerNetwork,
  getContainerNetworkDashboard,
  listDockerNetworks
} from '/src/features/containers/lib/container-api-client';
import { Button } from '/src/shared/components/button';
import { ConfirmDialog, TextInputDialog } from '/src/shared/components/dialog';
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
  const [networks, setNetworks] = useState<DockerNetwork[]>([]);
  const [selectedNetwork, setSelectedNetwork] = useState('');
  const [isMutating, setIsMutating] = useState(false);
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isDisconnectDialogOpen, setIsDisconnectDialogOpen] = useState(false);
  const drawerRef = useRef<HTMLElement | null>(null);

  const connectedNetworkNames = useMemo(
    () => new Set(dashboard?.networks.map((network) => network.name) ?? []),
    [dashboard]
  );
  const isSelectedConnected = connectedNetworkNames.has(selectedNetwork);

  const loadDashboard = async (activeContainerName: string) => {
    const [dashboardResponse, networkResponse] = await Promise.all([
      getContainerNetworkDashboard(activeContainerName),
      listDockerNetworks()
    ]);
    setDashboard(dashboardResponse);
    setNetworks(networkResponse);
    setSelectedNetwork((current) =>
      networkResponse.some((network) => network.name === current) ? current : networkResponse[0]?.name || ''
    );
  };

  useEffect(() => {
    if (!containerName || !isOpen) {
      setDashboard(null);
      setErrorMessage('');
      return;
    }
    let active = true;
    setIsLoading(true);
    setErrorMessage('');
    loadDashboard(containerName)
      .catch((error) => {
        if (active) setErrorMessage(error instanceof Error ? error.message : 'Failed to load network dashboard.');
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [containerName, isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    drawerRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const mutateNetwork = async (mutation: () => Promise<void>) => {
    if (!containerName) return;
    setIsMutating(true);
    setErrorMessage('');
    try {
      await mutation();
      await loadDashboard(containerName);
      setIsCreateDialogOpen(false);
      setIsDisconnectDialogOpen(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Network operation failed.');
    } finally {
      setIsMutating(false);
    }
  };

  const createNetwork = (name: string) => {
    void mutateNetwork(async () => {
      await createDockerNetwork(name);
      setSelectedNetwork(name);
    });
  };

  const connectNetwork = () => {
    void mutateNetwork(() => changeContainerNetwork(containerName!, selectedNetwork, 'connect').then(() => undefined));
  };

  const disconnectNetwork = () => {
    void mutateNetwork(() => changeContainerNetwork(containerName!, selectedNetwork, 'disconnect').then(() => undefined));
  };

  return (
    <div className={`${styles.networkDrawerRoot} ${isOpen ? styles.networkDrawerRootOpen : ''}`} aria-hidden={!isOpen}>
      <button className={styles.networkDrawerBackdrop} aria-label="네트워크 패널 닫기" onClick={onClose} type="button" />
      <aside
        aria-label="Container network details"
        className={styles.networkDrawer}
        ref={drawerRef}
        tabIndex={-1}
      >
        <header className={styles.networkDrawerHeader}>
          <div>
            <span>Container connectivity</span>
            <h2>{displayName || '컨테이너 네트워크'}</h2>
            <p>{containerName || '선택된 컨테이너가 없습니다.'}</p>
          </div>
          <button className={styles.drawerCloseButton} aria-label="닫기" onClick={onClose} type="button">×</button>
        </header>

        {errorMessage && <ErrorBanner className={styles.networkError}>{errorMessage}</ErrorBanner>}
        {isLoading ? (
          <div className={styles.drawerEmptyState}>네트워크 정보를 불러오는 중입니다...</div>
        ) : (
          <div className={styles.networkDrawerContent}>
            <section className={styles.networkHero}>
              <div>
                <span>Connected networks</span>
                <strong>{dashboard?.networks.length ?? 0}</strong>
              </div>
              <div>
                <span>Published ports</span>
                <strong>{dashboard?.ports.length ?? 0}</strong>
              </div>
              <p>bridge 네트워크를 선택해 현재 컨테이너에 연결하거나 해제할 수 있습니다.</p>
            </section>

            <section className={styles.networkCard}>
              <div className={styles.networkCardHeader}>
                <div>
                  <span>Network action</span>
                  <h3>bridge 네트워크 연결</h3>
                </div>
                <Button type="button" onClick={() => setIsCreateDialogOpen(true)} disabled={isMutating}>새 네트워크</Button>
              </div>
              <label className={styles.networkSelectLabel}>
                대상 네트워크
                <select value={selectedNetwork} onChange={(event) => setSelectedNetwork(event.target.value)}>
                  {networks.length === 0 && <option value="">사용 가능한 bridge 네트워크가 없습니다.</option>}
                  {networks.map((network) => (
                    <option key={network.id} value={network.name}>{network.name} · {network.scope}</option>
                  ))}
                </select>
              </label>
              <div className={styles.networkActionRow}>
                <Button
                  type="button"
                  variant="primary"
                  onClick={connectNetwork}
                  disabled={isMutating || !selectedNetwork || isSelectedConnected}
                >
                  {isSelectedConnected ? '연결됨' : '연결'}
                </Button>
                <Button
                  type="button"
                  onClick={() => setIsDisconnectDialogOpen(true)}
                  disabled={isMutating || !selectedNetwork || !isSelectedConnected}
                >
                  연결 해제
                </Button>
              </div>
            </section>

            <section className={styles.networkCard}>
              <div className={styles.networkCardHeader}>
                <div>
                  <span>Active connections</span>
                  <h3>연결된 네트워크</h3>
                </div>
              </div>
              {dashboard?.networks.length ? (
                <div className={styles.networkList}>
                  {dashboard.networks.map((network) => (
                    <article className={styles.networkRow} key={`${network.name}-${network.ipAddress}`}>
                      <div className={styles.networkRowTitle}>
                        <strong>{network.name}</strong>
                        <span>Connected</span>
                      </div>
                      <dl className={styles.networkMetadata}>
                        <div><dt>IP</dt><dd>{network.ipAddress || 'unassigned'}</dd></div>
                        <div><dt>Gateway</dt><dd>{network.gateway || '-'}</dd></div>
                      </dl>
                    </article>
                  ))}
                </div>
              ) : <div className={styles.emptyInline}>연결된 네트워크가 없습니다.</div>}
            </section>

            <section className={styles.networkCard}>
              <div className={styles.networkCardHeader}>
                <div>
                  <span>Port exposure</span>
                  <h3>포트 포워딩</h3>
                </div>
              </div>
              {dashboard?.ports.length ? (
                <div className={styles.portMappingList}>
                  {dashboard.ports.map((port) => (
                    <article className={styles.portMappingRow} key={`${port.protocol}-${port.hostPort}-${port.containerPort}`}>
                      <span>{port.containerPort}/{port.protocol.toLowerCase()}</span>
                      {port.url ? (
                        <a href={port.url} rel="noreferrer" target="_blank">localhost:{port.hostPort}</a>
                      ) : <small>외부에 노출되지 않음</small>}
                    </article>
                  ))}
                </div>
              ) : <div className={styles.emptyInline}>노출된 포트가 없습니다.</div>}
            </section>
          </div>
        )}
      </aside>

      <TextInputDialog
        confirmLabel="생성"
        description="Docker bridge 네트워크를 만들고 목록을 새로 고칩니다."
        isOpen={isCreateDialogOpen}
        isSubmitting={isMutating}
        label="네트워크 이름"
        onClose={() => setIsCreateDialogOpen(false)}
        onConfirm={createNetwork}
        placeholder="예: project-bridge"
        title="새 bridge 네트워크"
      />
      <ConfirmDialog
        confirmLabel="연결 해제"
        description={`${selectedNetwork || '선택한 네트워크'}에서 이 컨테이너의 연결을 해제합니다.`}
        isOpen={isDisconnectDialogOpen}
        isSubmitting={isMutating}
        onClose={() => setIsDisconnectDialogOpen(false)}
        onConfirm={disconnectNetwork}
        title="네트워크 연결 해제"
        tone="danger"
      />
    </div>
  );
}
