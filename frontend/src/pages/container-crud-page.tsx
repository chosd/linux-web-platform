import { ContainerCrudTool } from '/src/features/containers/components/container-crud-tool';

type ContainerCrudPageProps = {
  onConnectTerminal: (containerName: string, displayName: string) => void;
};

export function ContainerCrudPage({ onConnectTerminal }: ContainerCrudPageProps) {
  return (
    <main className="content-page">
      <header className="content-header">
        <div className="page-title-group">
          <h1>컨테이너 관리</h1>
          <p>Linux 터미널 컨테이너의 생성, 조회, 수정, 삭제를 관리합니다.</p>
        </div>
      </header>
      <ContainerCrudTool onConnectTerminal={onConnectTerminal} />
    </main>
  );
}
