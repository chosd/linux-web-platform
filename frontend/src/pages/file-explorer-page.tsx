import { FileExplorerTool } from '/src/features/files/components/file-explorer-tool';

export function FileExplorerPage() {
  return (
    <main className="content-page">
      <header className="content-header">
        <div className="page-title-group">
          <h1>파일 탐색기</h1>
          <p>컨테이너 내부 파일 시스템을 조회하고 파일을 업로드/다운로드합니다.</p>
        </div>
      </header>
      <FileExplorerTool />
    </main>
  );
}
