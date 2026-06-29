import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import {
  ContainerSummary,
  createContainer,
  deleteContainer,
  listContainers,
  runContainerAction,
  updateContainer
} from '/src/features/containers/lib/container-api-client';
import { Button } from '/src/shared/components/button';
import { StatusBadge } from '/src/shared/components/status-badge';

type ContainerCrudToolProps = {
  onConnectTerminal: (containerName: string, displayName: string) => void;
};

export function ContainerCrudTool({ onConnectTerminal }: ContainerCrudToolProps) {
  const [containers, setContainers] = useState<ContainerSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [editingContainerName, setEditingContainerName] = useState<string | null>(null);
  const [editingDisplayName, setEditingDisplayName] = useState('');
  const [busyContainerName, setBusyContainerName] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

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

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedDisplayName = displayName.trim();
    if (!trimmedDisplayName) {
      return;
    }

    setIsCreating(true);
    setErrorMessage('');
    try {
      const createdContainer = await createContainer(trimmedDisplayName);
      setContainers((current) => [createdContainer, ...current]);
      setDisplayName('');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to create container.');
    } finally {
      setIsCreating(false);
    }
  };

  const beginEdit = (container: ContainerSummary) => {
    setEditingContainerName(container.containerName);
    setEditingDisplayName(container.displayName);
  };

  const cancelEdit = () => {
    setEditingContainerName(null);
    setEditingDisplayName('');
  };

  const saveEdit = async (containerName: string) => {
    const trimmedDisplayName = editingDisplayName.trim();
    if (!trimmedDisplayName) {
      return;
    }

    setBusyContainerName(containerName);
    setErrorMessage('');
    try {
      const updatedContainer = await updateContainer(containerName, trimmedDisplayName);
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
    const confirmed = window.confirm('이 컨테이너를 삭제할까요? 실행 중인 컨테이너도 제거됩니다.');
    if (!confirmed) {
      return;
    }

    setBusyContainerName(containerName);
    setErrorMessage('');
    try {
      await deleteContainer(containerName);
      setContainers((current) => current.filter((container) => container.containerName !== containerName));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to delete container.');
    } finally {
      setBusyContainerName(null);
    }
  };

  return (
    <section className="crud-tool" aria-busy={isLoading}>
      <form className="create-panel" onSubmit={handleCreate}>
        <div>
          <h2>새 컨테이너</h2>
          <p>컨테이너를 생성합니다.</p>
        </div>
        <div className="create-form-row">
          <label htmlFor="container-display-name">컨테이너 이름</label>
          <input
            id="container-display-name"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder="Input Container name"
          />
          <Button type="submit" variant="primary" disabled={isCreating || !displayName.trim()}>
            생성
          </Button>
        </div>
      </form>

      {errorMessage && <div className="error-banner">{errorMessage}</div>}

      <div className="container-table-panel">
        <header className="panel-header">
          <div>
            <h2>컨테이너 목록</h2>
            <p>생성, 조회, 이름 변경, 삭제와 실행 상태 변경을 수행합니다.</p>
          </div>
          <Button type="button" onClick={loadContainers} disabled={isLoading}>
            새로고침
          </Button>
        </header>

        {isLoading ? (
          <div className="empty-state">Loading containers...</div>
        ) : sortedContainers.length === 0 ? (
          <div className="empty-state">No containers yet.</div>
        ) : (
          <div className="container-table-scroll">
            <table className="container-table">
              <thead>
                <tr>
                  <th>이름</th>
                  <th>상태</th>
                  <th>컨테이너</th>
                  <th>생성일</th>
                  <th>작업</th>
                </tr>
              </thead>
              <tbody>
                {sortedContainers.map((container) => {
                  const isEditing = editingContainerName === container.containerName;
                  const isBusy = busyContainerName === container.containerName;
                  return (
                    <tr key={container.containerName}>
                      <td>
                        {isEditing ? (
                          <input
                            className="inline-edit-input"
                            value={editingDisplayName}
                            onChange={(event) => setEditingDisplayName(event.target.value)}
                          />
                        ) : (
                          <span className="container-name-cell">{container.displayName}</span>
                        )}
                      </td>
                      <td>
                        <StatusBadge label={container.status} />
                      </td>
                      <td className="mono-cell">{container.containerName}</td>
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
                              <Button type="button" onClick={() => beginEdit(container)} disabled={isBusy}>
                                수정
                              </Button>
                              <Button
                                type="button"
                                onClick={() => onConnectTerminal(container.containerName, container.displayName)}
                                disabled={isBusy || container.status === 'NOT_FOUND'}
                              >
                                터미널
                              </Button>
                              <Button
                                type="button"
                                onClick={() => handleLifecycleAction('start', container.containerName)}
                                disabled={isBusy || container.status === 'RUNNING'}
                              >
                                시작
                              </Button>
                              <Button
                                type="button"
                                onClick={() => handleLifecycleAction('stop', container.containerName)}
                                disabled={isBusy || container.status !== 'RUNNING'}
                              >
                                중지
                              </Button>
                              <Button
                                type="button"
                                onClick={() => handleLifecycleAction('restart', container.containerName)}
                                disabled={isBusy || container.status === 'NOT_FOUND'}
                              >
                                재시작
                              </Button>
                              <Button
                                type="button"
                                className="danger-button"
                                onClick={() => handleDelete(container.containerName)}
                                disabled={isBusy}
                              >
                                삭제
                              </Button>
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
      </div>
    </section>
  );
}
