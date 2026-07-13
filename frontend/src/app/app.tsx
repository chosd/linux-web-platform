import { lazy, Suspense, useEffect, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';

import {
  containerDetailUrl,
  containersPath,
  dashboardPath,
  imagesPath
} from '/src/app/app-routes';
import { ContainerStatus, ContainerSummary, listContainers } from '/src/features/containers/lib/container-api-client';
import { useHostResourceStats } from '/src/features/dashboard/lib/use-host-resource-stats';
import { AppMenuKey, AppShell } from '/src/layouts/app-shell';
import type { ContainerDetailTab } from '/src/pages/container-detail-page';
import { EmptyState } from '/src/shared/components/feedback';

const ContainerDetailPage = lazy(() =>
  import('/src/pages/container-detail-page').then((module) => ({ default: module.ContainerDetailPage }))
);
const ContainerCrudPage = lazy(() =>
  import('/src/pages/container-crud-page').then((module) => ({ default: module.ContainerCrudPage }))
);
const DashboardPage = lazy(() =>
  import('/src/pages/dashboard-page').then((module) => ({ default: module.DashboardPage }))
);
const ImagesPage = lazy(() =>
  import('/src/pages/images-page').then((module) => ({ default: module.ImagesPage }))
);

type SelectedContainer = {
  containerName: string;
  displayName: string;
  status: ContainerStatus;
  createdAt: string;
  cpuCores?: number;
  memoryMb?: number;
  imageName?: string;
};

type DetailNavigationState = {
  container?: SelectedContainer;
};

export function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const hostResourceState = useHostResourceStats();
  const activeMenu: AppMenuKey = location.pathname.startsWith(containersPath)
    ? 'containers'
    : location.pathname.startsWith(imagesPath) ? 'images' : 'dashboard';

  const selectMenu = (menu: AppMenuKey) => {
    navigate(menu === 'dashboard' ? dashboardPath : menu === 'images' ? imagesPath : containersPath);
  };

  return (
    <AppShell activeMenu={activeMenu} hostResourceStats={hostResourceState.stats} onSelectMenu={selectMenu}>
      <Suspense fallback={<EmptyState>Loading screen...</EmptyState>}>
        <Routes>
          <Route
            path={dashboardPath}
            element={
              <DashboardPage
                hostResourceStats={hostResourceState.stats}
                isLoadingHostResources={hostResourceState.isLoading}
                hostResourceErrorMessage={hostResourceState.errorMessage}
                onRefreshHostResources={hostResourceState.reload}
                onOpenContainerDetail={(container) => navigateToDetail(navigate, container, 'overview')}
              />
            }
          />
          <Route path={containersPath} element={<ContainerCrudPage onConnectTerminal={(container) => navigateToDetail(navigate, container, 'terminal')} onOpenContainerDetail={(container) => navigateToDetail(navigate, container, 'overview')} />} />
          <Route path={imagesPath} element={<ImagesPage />} />
          <Route path="/containers/:containerName/:tab" element={<ContainerDetailRoute />} />
          <Route path="*" element={<Navigate to={dashboardPath} replace />} />
        </Routes>
      </Suspense>
    </AppShell>
  );
}

function ContainerDetailRoute() {
  const { containerName = '', tab = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const decodedContainerName = containerName;
  const navigationState = location.state as DetailNavigationState | null;
  const [selectedContainer, setSelectedContainer] = useState<SelectedContainer | null>(
    navigationState?.container ?? null
  );

  useEffect(() => {
    let active = true;
    const loadSelectedContainer = async () => {
      try {
        const container = (await listContainers()).find((item) => item.containerName === decodedContainerName);
        if (active) {
          setSelectedContainer(container ? toSelectedContainer(container) : null);
        }
      } catch {
        if (active) setSelectedContainer(null);
      }
    };
    void loadSelectedContainer();
    return () => {
      active = false;
    };
  }, [decodedContainerName]);

  if (!isContainerDetailTab(tab)) {
    return <Navigate to={containerDetailUrl(decodedContainerName, 'overview')} replace />;
  }
  if (!selectedContainer) {
    return <EmptyState>Loading container...</EmptyState>;
  }

  return (
    <ContainerDetailPage
      activeTab={tab}
      containerName={selectedContainer.containerName}
      displayName={selectedContainer.displayName}
      status={selectedContainer.status}
      createdAt={selectedContainer.createdAt}
      cpuCores={selectedContainer.cpuCores}
      memoryMb={selectedContainer.memoryMb}
      imageName={selectedContainer.imageName}
      onBack={() => navigate(containersPath)}
      onSelectTab={(nextTab) => navigate(containerDetailUrl(selectedContainer.containerName, nextTab))}
    />
  );
}

function navigateToDetail(
  navigate: ReturnType<typeof useNavigate>,
  container: ContainerSummary,
  tab: ContainerDetailTab
) {
  navigate(containerDetailUrl(container.containerName, tab), {
    state: { container: toSelectedContainer(container) } satisfies DetailNavigationState
  });
}

function toSelectedContainer(container: ContainerSummary): SelectedContainer {
  return {
    containerName: container.containerName,
    displayName: container.displayName,
    status: container.status,
    createdAt: container.createdAt,
    cpuCores: container.cpuCores,
    memoryMb: container.memoryMb,
    imageName: container.imageName
  };
}

function isContainerDetailTab(value: string): value is ContainerDetailTab {
  return value === 'overview' || value === 'files' || value === 'terminal' || value === 'stats' || value === 'network';
}
