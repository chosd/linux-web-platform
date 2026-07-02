import { FileExplorerTool } from '/src/features/files/components/file-explorer-tool';

import styles from './page-layout.module.css';

export function FileExplorerPage() {
  return (
    <main className={styles.contentPage}>
      <header className={styles.contentHeader}>
        <div className={styles.titleGroup}>
          <h1>파일 탐색기</h1>
          <p>컨테이너 내부 파일 시스템을 조회하고 파일을 업로드/다운로드합니다.</p>
        </div>
      </header>
      <FileExplorerTool />
    </main>
  );
}
