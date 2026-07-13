import { lazy, Suspense, useState } from 'react';

import { ContainerStatus } from '/src/features/containers/lib/container-api-client';
import { Button } from '/src/shared/components/button';
import { EmptyState } from '/src/shared/components/feedback';
import { StatusBadge } from '/src/shared/components/status-badge';

import layoutStyles from './page-layout.module.css';
import styles from './container-detail-page.module.css';

const ContainerNetworkDrawer = lazy(() => import('/src/features/containers/components/container-network-dashboard').then((module) => ({ default: module.ContainerNetworkDrawer })));
const ContainerStatsChart = lazy(() => import('/src/features/containers/components/container-stats-chart').then((module) => ({ default: module.ContainerStatsChart })));
const FileExplorerTool = lazy(() => import('/src/features/files/components/file-explorer-tool').then((module) => ({ default: module.FileExplorerTool })));
const TerminalView = lazy(() => import('/src/features/terminal/components/terminal-view').then((module) => ({ default: module.TerminalView })));

export type ContainerDetailTab = 'overview' | 'files' | 'terminal' | 'stats' | 'network';

type ContainerDetailPageProps = {
  containerName: string;
  displayName: string;
  status: ContainerStatus;
  createdAt: string;
  cpuCores?: number;
  memoryMb?: number;
  imageName?: string;
  activeTab: ContainerDetailTab;
  onBack: () => void;
  onSelectTab: (tab: ContainerDetailTab) => void;
};

const tabs: Array<{ key: ContainerDetailTab; label: string }> = [
  { key: 'overview', label: '개요' },
  { key: 'files', label: '파일' },
  { key: 'terminal', label: '터미널' },
  { key: 'network', label: '네트워크' }
];

export function ContainerDetailPage({
  activeTab,
  containerName,
  createdAt,
  cpuCores,
  displayName,
  imageName,
  memoryMb,
  onBack,
  onSelectTab,
  status
}: ContainerDetailPageProps) {
  const [isNetworkDrawerOpen, setIsNetworkDrawerOpen] = useState(false);

  return (
    <main className={styles.page}>
      <header className={`${layoutStyles.contentHeader} ${styles.detailHeader}`}>
        <div className={layoutStyles.titleGroup}>
          <h1>{displayName}</h1>
          <p>{containerName}</p>
        </div>
        <div className={styles.headerActions}>
          <StatusBadge label={status} />
          <Button type="button" onClick={onBack}>
            목록
          </Button>
        </div>
      </header>

      <nav className={styles.tabs} aria-label="Container detail tabs" role="tablist">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.key}
            className={`${styles.tab} ${activeTab === tab.key ? styles.tabActive : ''}`}
            onClick={() => onSelectTab(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      <div className={styles.content}>
        <Suspense fallback={<EmptyState>Loading tool...</EmptyState>}>
          {activeTab === 'overview' && (
            <div className={styles.overviewContainer}>
              <section className={styles.overview}>
                <div className={styles.overviewGrid}>
                  <article><span>Status</span><StatusBadge label={status} /></article>
                  <article><span>Image</span><strong>{imageName || 'Default image'}</strong></article>
                  <article><span>CPU limit</span><strong>{cpuCores ?? 0.5} cores</strong></article>
                  <article><span>Memory limit</span><strong>{memoryMb ?? 256} MB</strong></article>
                  <article><span>Created</span><strong>{new Date(createdAt).toLocaleString()}</strong></article>
                  <article><span>Container ID</span><code>{containerName}</code></article>
                </div>
              </section>
              <section className={styles.overviewStats}>
                <ContainerStatsChart containerName={containerName} displayName={displayName} cpuCores={cpuCores} memoryMb={memoryMb} />
              </section>
            </div>
          )}
          {activeTab === 'files' && (
            <section className={styles.workSurface}>
              <FileExplorerTool containerName={containerName} displayName={displayName} status={status} />
            </section>
          )}

          {activeTab === 'terminal' && (
            <section className={styles.workSurface}>
              <TerminalView
                containerName={containerName}
                displayName={displayName}
                onBack={() => onSelectTab('files')}
                backLabel="파일"
              />
            </section>
          )}



          {activeTab === 'network' && (
            <section className={styles.networkPanel}>
              <div className={styles.networkIntro}>
                <div>
                  <span>Network workspace</span>
                  <strong>{displayName}</strong>
                </div>
                <Button type="button" onClick={() => setIsNetworkDrawerOpen(true)}>
                  네트워크 정보 열기
                </Button>
              </div>
              <div className={styles.networkEmpty}>연결된 네트워크, IP 주소와 포트 매핑은 네트워크 정보에서 관리합니다.</div>
            </section>
          )}
        </Suspense>
      </div>

      <Suspense fallback={null}>
        <ContainerNetworkDrawer
          containerName={containerName}
          displayName={displayName}
          isOpen={isNetworkDrawerOpen}
          onClose={() => setIsNetworkDrawerOpen(false)}
        />
      </Suspense>
    </main>
  );
}
