import { ContainerCrudTool } from '/src/features/containers/components/container-crud-tool';

import styles from './page-layout.module.css';

export function ContainerCrudPage() {
  return (
    <main className={styles.contentPage}>
      <header className={styles.contentHeader}>
        <div className={styles.titleGroup}>
          <h1>컨테이너 관리</h1>
          <p>Linux 터미널 컨테이너 생성을 위한 기본 설정과 포트 포워딩을 입력합니다.</p>
        </div>
      </header>
      <ContainerCrudTool />
    </main>
  );
}
