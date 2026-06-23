import { FormEvent, useCallback, useEffect, useState } from 'react';

export type ContainerStatus = 'RUNNING' | 'EXITED' | 'NOT_FOUND' | string;

export type ContainerSummary = {
  containerName: string;
  displayName: string;
  status: ContainerStatus;
  createdAt: string;
};

type ContainerDashboardProps = {
  onConnectTerminal: (containerName: string, displayName: string) => void;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || `http://${window.location.hostname}:8080`;
const userId = import.meta.env.VITE_USER_ID || 'anonymous';

export function ContainerDashboard({ onConnectTerminal }: ContainerDashboardProps) {
  const [containers, setContainers] = useState<ContainerSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [displayName, setDisplayName] = useState('');
  const [isCreating, setIsCreating] = useState(false);

  const loadContainers = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      const response = await fetch(`${apiBaseUrl}/api/containers/list`, {
        headers: {
          'X-User-Id': userId
        }
      });
      if (!response.ok) {
        throw new Error(`Container list request failed: ${response.status}`);
      }
      const data = (await response.json()) as ContainerSummary[];
      setContainers(data);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load containers.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadContainers();
  }, [loadContainers]);

  const createContainer = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedDisplayName = displayName.trim();
    if (!trimmedDisplayName) {
      return;
    }

    setIsCreating(true);
    setErrorMessage('');
    try {
      const response = await fetch(`${apiBaseUrl}/api/containers/create`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-User-Id': userId
        },
        body: JSON.stringify({ displayName: trimmedDisplayName })
      });
      if (!response.ok) {
        throw new Error(`Container create request failed: ${response.status}`);
      }
      const createdContainer = (await response.json()) as ContainerSummary;
      setContainers((current) => [createdContainer, ...current]);
      setDisplayName('');
      setIsModalOpen(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to create container.');
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <main className="dashboard-page">
      <header className="dashboard-header">
        <div>
          <h1>Linux Containers</h1>
          <p>Choose a saved learning container or create a new terminal workspace.</p>
        </div>
        <button type="button" className="primary-button" onClick={() => setIsModalOpen(true)}>
          + 새 컨테이너 만들기
        </button>
      </header>

      {errorMessage && <div className="error-banner">{errorMessage}</div>}

      <section className="container-grid" aria-busy={isLoading}>
        {isLoading ? (
          <div className="empty-state">Loading containers...</div>
        ) : containers.length === 0 ? (
          <div className="empty-state">No containers yet.</div>
        ) : (
          containers.map((container) => (
            <article className="container-card" key={container.containerName}>
              <div className="container-card-header">
                <h2>{container.displayName}</h2>
                <span className={`container-status status-${container.status.toLowerCase()}`}>
                  {container.status}
                </span>
              </div>
              <dl>
                <div>
                  <dt>Container</dt>
                  <dd>{container.containerName}</dd>
                </div>
                <div>
                  <dt>Created</dt>
                  <dd>{new Date(container.createdAt).toLocaleString()}</dd>
                </div>
              </dl>
              <button
                type="button"
                onClick={() => onConnectTerminal(container.containerName, container.displayName)}
              >
                터미널 접속
              </button>
            </article>
          ))
        )}
      </section>

      {isModalOpen && (
        <div className="modal-backdrop" role="presentation">
          <form className="modal-panel" onSubmit={createContainer}>
            <header>
              <h2>새 컨테이너 만들기</h2>
              <button type="button" aria-label="Close" onClick={() => setIsModalOpen(false)}>
                x
              </button>
            </header>
            <label htmlFor="container-display-name">터미널 이름</label>
            <input
              id="container-display-name"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="예: 리눅스 기초 실습"
              autoFocus
            />
            <footer>
              <button type="button" onClick={() => setIsModalOpen(false)}>
                취소
              </button>
              <button type="submit" className="primary-button" disabled={isCreating || !displayName.trim()}>
                생성
              </button>
            </footer>
          </form>
        </div>
      )}
    </main>
  );
}
