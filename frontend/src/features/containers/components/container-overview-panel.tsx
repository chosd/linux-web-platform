import { useCallback, useEffect, useMemo, useState } from 'react';

import { ContainerSummary, listContainers } from '/src/features/containers/lib/container-api-client';
import { Button } from '/src/shared/components/button';
import { EmptyState, ErrorBanner } from '/src/shared/components/feedback';
import { PanelHeader } from '/src/shared/components/panel-header';
import { StatusBadge } from '/src/shared/components/status-badge';

import styles from './container-overview-panel.module.css';

type Props = { onOpenContainerDetail: (container: ContainerSummary) => void };

export function ContainerOverviewPanel({ onOpenContainerDetail }: Props) {
  const [containers, setContainers] = useState<ContainerSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const load = useCallback(async () => { setIsLoading(true); setErrorMessage(''); try { setContainers(await listContainers()); } catch (error) { setErrorMessage(error instanceof Error ? error.message : 'Failed to load containers.'); } finally { setIsLoading(false); } }, []);
  useEffect(() => { void load(); }, [load]);
  const recent = useMemo(() => [...containers].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)).slice(0, 5), [containers]);
  const running = containers.filter((item) => item.status === 'RUNNING').length;
  return <section className={styles.panel}><PanelHeader title="Container overview" description="상태 요약과 최근 생성된 컨테이너입니다." actions={<Button type="button" onClick={load} disabled={isLoading}>새로고침</Button>} />
    {errorMessage && <ErrorBanner>{errorMessage}</ErrorBanner>}
    <div className={styles.counts}><div><span>Total</span><strong>{containers.length}</strong></div><div><span>Running</span><strong>{running}</strong></div><div><span>Stopped</span><strong>{containers.length - running}</strong></div></div>
    {isLoading ? <EmptyState>Loading containers...</EmptyState> : recent.length === 0 ? <EmptyState>No containers yet.</EmptyState> : <div className={styles.list}>{recent.map((container) => <button type="button" key={container.containerName} onClick={() => onOpenContainerDetail(container)}><span><strong>{container.displayName}</strong><small>{container.imageName || 'Default image'}</small></span><StatusBadge label={container.status} /></button>)}</div>}
  </section>;
}
