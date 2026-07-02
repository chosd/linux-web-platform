import { HostResourceDashboard } from '/src/features/dashboard/components/host-resource-dashboard';
import { HostResourceStatsSample } from '/src/features/dashboard/lib/dashboard-api-client';
import { ContainerListPanel } from '/src/features/containers/components/container-list-panel';

import styles from './page-layout.module.css';

type DashboardPageProps = {
  hostResourceStats: HostResourceStatsSample | null;
  isLoadingHostResources: boolean;
  hostResourceErrorMessage: string;
  onRefreshHostResources: () => void;
  onConnectTerminal: (containerName: string, displayName: string) => void;
};

export function DashboardPage({
  hostResourceStats,
  isLoadingHostResources,
  hostResourceErrorMessage,
  onRefreshHostResources,
  onConnectTerminal
}: DashboardPageProps) {
  return (
    <main className={styles.dashboardPage}>
      <header className={styles.contentHeader}>
        <div className={styles.titleGroup}>
          <h1>대시보드</h1>
          <p>호스트 Linux 기준으로 컨테이너 플랫폼의 전체 리소스 사용량을 확인합니다.</p>
        </div>
      </header>
      <div className={styles.dashboardContent}>
        <HostResourceDashboard
          stats={hostResourceStats}
          isLoading={isLoadingHostResources}
          errorMessage={hostResourceErrorMessage}
          onRefresh={onRefreshHostResources}
        />
        <ContainerListPanel onConnectTerminal={onConnectTerminal} />
      </div>
    </main>
  );
}
