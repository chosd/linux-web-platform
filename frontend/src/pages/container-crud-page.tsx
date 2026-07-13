import { useState } from 'react';
import { ContainerCrudTool } from '/src/features/containers/components/container-crud-tool';
import { ContainerListPanel } from '/src/features/containers/components/container-list-panel';
import { ContainerSummary } from '/src/features/containers/lib/container-api-client';

import styles from './page-layout.module.css';

type Props = { onConnectTerminal: (container: ContainerSummary) => void; onOpenContainerDetail: (container: ContainerSummary) => void };

export function ContainerCrudPage({ onConnectTerminal, onOpenContainerDetail }: Props) {
  const [activeView, setActiveView] = useState<'list' | 'create'>('list');

  return (
    <main className={styles.contentPage}>
      <header className={styles.contentHeader}>
        <div className={styles.titleGroup}>
          <h1>컨테이너 관리</h1>
          <p>
            {activeView === 'list'
              ? '실행 중인 터미널 컨테이너 목록을 조회하고 수명 주기를 제어합니다.'
              : 'Linux 터미널 컨테이너 생성을 위한 기본 설정과 포트 포워딩을 입력합니다.'}
          </p>
        </div>
      </header>
      <div className={styles.containerManagement}>
        {activeView === 'list' ? (
          <ContainerListPanel
            onConnectTerminal={onConnectTerminal}
            onOpenContainerDetail={onOpenContainerDetail}
            onCreateClick={() => setActiveView('create')}
          />
        ) : (
          <ContainerCrudTool
            onSuccess={() => setActiveView('list')}
            onCancel={() => setActiveView('list')}
          />
        )}
      </div>
    </main>
  );
}
