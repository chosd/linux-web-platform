import { HostResourceDashboard } from '/src/features/dashboard/components/host-resource-dashboard';
import { HostResourceStatsSample } from '/src/features/dashboard/lib/dashboard-api-client';
import { ContainerListPanel } from '/src/features/containers/components/container-list-panel';

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
    <main className="dashboard-page">
      <header className="content-header">
        <div className="page-title-group">
          <h1>대시보드</h1>
          <p>호스트 Linux 기준으로 컨테이너 플랫폼의 전체 리소스 사용량을 확인합니다.</p>
        </div>
      </header>
      <div className="dashboard-content">
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
