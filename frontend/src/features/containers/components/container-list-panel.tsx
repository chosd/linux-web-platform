import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  ContainerSummary,
  deleteContainer,
  listContainers,
  runContainerAction,
  updateContainer
} from '/src/features/containers/lib/container-api-client';
import { ContainerNetworkDrawer } from '/src/features/containers/components/container-network-dashboard';
import { Button } from '/src/shared/components/button';
import { StatusBadge } from '/src/shared/components/status-badge';

type ContainerListPanelProps = {
  onConnectTerminal: (containerName: string, displayName: string) => void;
};

export function ContainerListPanel({ onConnectTerminal }: ContainerListPanelProps) {
  const [containers, setContainers] = useState<ContainerSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const [editingContainerName, setEditingContainerName] = useState<string | null>(null);
  const [editingDisplayName, setEditingDisplayName] = useState('');
  const [editingCpuCores, setEditingCpuCores] = useState('');
  const [editingMemoryMb, setEditingMemoryMb] = useState('');
  const [busyContainerName, setBusyContainerName] = useState<string | null>(null);
  const [openActionMenuName, setOpenActionMenuName] = useState<string | null>(null);
  const [networkContainer, setNetworkContainer] = useState<ContainerSummary | null>(null);

  const sortedContainers = useMemo(
    () =>
      [...containers].sort(
        (first, second) => new Date(second.createdAt).getTime() - new Date(first.createdAt).getTime()
      ),
    [containers]
  );

  const loadContainers = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      setContainers(await listContainers());
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load containers.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadContainers();
  }, [loadContainers]);

  const beginEdit = (container: ContainerSummary) => {
    setOpenActionMenuName(null);
    setEditingContainerName(container.containerName);
    setEditingDisplayName(container.displayName);
    setEditingCpuCores(String(container.cpuCores ?? 0.5));
    setEditingMemoryMb(String(container.memoryMb ?? 256));
  };

  const cancelEdit = () => {
    setEditingContainerName(null);
    setEditingDisplayName('');
    setEditingCpuCores('');
    setEditingMemoryMb('');
  };

  const saveEdit = async (containerName: string) => {
    const trimmedDisplayName = editingDisplayName.trim();
    const parsedCpuCores = Number(editingCpuCores);
    const parsedMemoryMb = Number(editingMemoryMb);
    if (!trimmedDisplayName || !isValidResourceLimits(parsedCpuCores, parsedMemoryMb)) {
      return;
    }

    setBusyContainerName(containerName);
    setErrorMessage('');
    try {
      const updatedContainer = await updateContainer(containerName, trimmedDisplayName, {
        cpuCores: parsedCpuCores,
        memoryMb: parsedMemoryMb
      });
      setContainers((current) =>
        current.map((container) =>
          container.containerName === updatedContainer.containerName ? updatedContainer : container
        )
      );
      cancelEdit();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to update container.');
    } finally {
      setBusyContainerName(null);
    }
  };

  const handleLifecycleAction = async (action: 'start' | 'stop' | 'restart', containerName: string) => {
    setOpenActionMenuName(null);
    setBusyContainerName(containerName);
    setErrorMessage('');
    try {
      const updatedContainer = await runContainerAction(action, containerName);
      setContainers((current) =>
        current.map((container) =>
          container.containerName === updatedContainer.containerName ? updatedContainer : container
        )
      );
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : `Failed to ${action} container.`);
    } finally {
      setBusyContainerName(null);
    }
  };

  const handleDelete = async (containerName: string) => {
    setOpenActionMenuName(null);
    const confirmed = window.confirm('이 컨테이너를 삭제할까요? 실행 중인 컨테이너도 제거됩니다.');
    if (!confirmed) {
      return;
    }

    setBusyContainerName(containerName);
    setErrorMessage('');
    try {
      await deleteContainer(containerName);
      setContainers((current) => current.filter((container) => container.containerName !== containerName));
      if (networkContainer?.containerName === containerName) {
        setNetworkContainer(null);
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to delete container.');
    } finally {
      setBusyContainerName(null);
    }
  };

  return (
    <>
      <section className="container-table-panel">
        <header className="panel-header">
          <div>
            <h2>실행 중인 컨테이너</h2>
            <p>컨테이너 상태, 리소스 제한, 네트워크 정보를 확인하고 작업을 실행합니다.</p>
          </div>
          <Button type="button" onClick={loadContainers} disabled={isLoading}>
            새로고침
          </Button>
        </header>

        {errorMessage && <div className="error-banner">{errorMessage}</div>}

        {isLoading ? (
          <div className="empty-state">Loading containers...</div>
        ) : sortedContainers.length === 0 ? (
          <div className="empty-state">No containers yet.</div>
        ) : (
          <div className="container-table-scroll">
            <table className="container-table">
              <thead>
                <tr>
                  <th>이름 / ID</th>
                  <th>상태</th>
                  <th>자원 제한</th>
                  <th>생성일</th>
                  <th>작업</th>
                </tr>
              </thead>
              <tbody>
                {sortedContainers.map((container) => {
                  const isEditing = editingContainerName === container.containerName;
                  const isBusy = busyContainerName === container.containerName;
                  const isRunning = container.status === 'RUNNING';
                  const isStopped = container.status === 'STOPPED' || container.status === 'EXITED';
                  const shortContainerId = getShortContainerId(container.containerName);
                  const isActionMenuOpen = openActionMenuName === container.containerName;
                  return (
                    <tr key={container.containerName}>
                      <td>
                        {isEditing ? (
                          <div className="inline-edit-grid">
                            <input
                              className="inline-edit-input"
                              value={editingDisplayName}
                              onChange={(event) => setEditingDisplayName(event.target.value)}
                            />
                            <input
                              className="inline-edit-input"
                              type="number"
                              min="0.1"
                              max="4"
                              step="0.1"
                              value={editingCpuCores}
                              onChange={(event) => setEditingCpuCores(event.target.value)}
                              aria-label="CPU limit"
                            />
                            <input
                              className="inline-edit-input"
                              type="number"
                              min="128"
                              max="4096"
                              step="64"
                              value={editingMemoryMb}
                              onChange={(event) => setEditingMemoryMb(event.target.value)}
                              aria-label="Memory limit"
                            />
                          </div>
                        ) : (
                          <div className="container-identity-cell">
                            <strong>{container.displayName}</strong>
                            <span title={container.containerName}>{shortContainerId}</span>
                          </div>
                        )}
                      </td>
                      <td>
                        <StatusBadge label={container.status} />
                      </td>
                      <td>
                        <div className="resource-limit-cell">
                          <span>{formatCpuCores(container.cpuCores ?? 0.5)} Cores</span>
                          <small>{container.memoryMb ?? 256} MB</small>
                        </div>
                      </td>
                      <td>{new Date(container.createdAt).toLocaleString()}</td>
                      <td>
                        <div className="table-actions">
                          {isEditing ? (
                            <>
                              <Button
                                type="button"
                                variant="primary"
                                onClick={() => saveEdit(container.containerName)}
                                disabled={isBusy || !editingDisplayName.trim()}
                              >
                                저장
                              </Button>
                              <Button type="button" onClick={cancelEdit} disabled={isBusy}>
                                취소
                              </Button>
                            </>
                          ) : (
                            <>
                              <Button
                                type="button"
                                onClick={() => onConnectTerminal(container.containerName, container.displayName)}
                                disabled={isBusy || container.status === 'NOT_FOUND'}
                              >
                                터미널
                              </Button>
                              <Button
                                type="button"
                                onClick={() => setNetworkContainer(container)}
                                disabled={isBusy || !isRunning}
                              >
                                네트워크
                              </Button>
                              {isRunning && (
                                <Button
                                  type="button"
                                  onClick={() => handleLifecycleAction('stop', container.containerName)}
                                  disabled={isBusy}
                                >
                                  중지
                                </Button>
                              )}
                              {isStopped && (
                                <Button
                                  type="button"
                                  onClick={() => handleLifecycleAction('start', container.containerName)}
                                  disabled={isBusy}
                                >
                                  시작
                                </Button>
                              )}
                              <div className="action-menu">
                                <button
                                  type="button"
                                  className="action-menu-trigger"
                                  aria-label={`${container.displayName} more actions`}
                                  aria-expanded={isActionMenuOpen}
                                  onClick={() =>
                                    setOpenActionMenuName(isActionMenuOpen ? null : container.containerName)
                                  }
                                  disabled={isBusy}
                                >
                                  ⋮
                                </button>
                                {isActionMenuOpen && (
                                  <div className="action-menu-popover" role="menu">
                                    <button type="button" role="menuitem" onClick={() => beginEdit(container)}>
                                      수정
                                    </button>
                                    <button
                                      type="button"
                                      role="menuitem"
                                      onClick={() => handleLifecycleAction('restart', container.containerName)}
                                      disabled={container.status === 'NOT_FOUND'}
                                    >
                                      재시작
                                    </button>
                                    <button
                                      type="button"
                                      role="menuitem"
                                      className="danger-menu-item"
                                      onClick={() => handleDelete(container.containerName)}
                                    >
                                      삭제
                                    </button>
                                  </div>
                                )}
                              </div>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <ContainerNetworkDrawer
        containerName={networkContainer?.containerName ?? null}
        displayName={networkContainer?.displayName}
        isOpen={networkContainer !== null}
        onClose={() => setNetworkContainer(null)}
      />
    </>
  );
}

function isValidResourceLimits(cpuCores: number, memoryMb: number) {
  return cpuCores >= 0.1 && cpuCores <= 4 && memoryMb >= 128 && memoryMb <= 4096;
}

function getShortContainerId(containerName: string) {
  return containerName.length > 12 ? `${containerName.slice(0, 12)}...` : containerName;
}

function formatCpuCores(cpuCores: number) {
  return Number.isInteger(cpuCores) ? cpuCores.toFixed(0) : cpuCores.toFixed(1);
}
