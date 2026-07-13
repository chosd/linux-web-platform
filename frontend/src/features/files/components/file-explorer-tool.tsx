import { ChangeEvent, DragEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { ContainerStatus } from '/src/features/containers/lib/container-api-client';
import {
  buildDownloadUrl,
  ContainerFileEntry,
  createContainerDirectory,
  deleteContainerPath,
  listContainerFiles,
  renameContainerPath,
  uploadContainerFile
} from '/src/features/files/lib/container-file-api-client';
import { Button } from '/src/shared/components/button';
import { ConfirmDialog, TextInputDialog } from '/src/shared/components/dialog';
import { EmptyState, ErrorBanner } from '/src/shared/components/feedback';
import { StatusBadge } from '/src/shared/components/status-badge';

import styles from './file-explorer-tool.module.css';

type TreeNode = {
  depth: number;
  path: string;
  label: string;
};

type TransferStatus = 'idle' | 'uploading' | 'completed' | 'cancelled' | 'failed';

type FileDialog =
  | { kind: 'create-directory' }
  | { kind: 'rename'; entry: ContainerFileEntry }
  | { kind: 'delete'; entry: ContainerFileEntry }
  | null;

type FileExplorerToolProps = {
  containerName: string;
  displayName: string;
  status: ContainerStatus;
};

export function FileExplorerTool({ containerName, displayName, status }: FileExplorerToolProps) {
  const [currentPath, setCurrentPath] = useState('/');
  const [files, setFiles] = useState<ContainerFileEntry[]>([]);
  const [treeNodes, setTreeNodes] = useState<TreeNode[]>([toTreeNode('/')]);
  const [isLoading, setIsLoading] = useState(false);
  const [isDragOver, setIsDragOver] = useState(false);
  const [selectedEntry, setSelectedEntry] = useState<ContainerFileEntry | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [uploadController, setUploadController] = useState<AbortController | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [currentUploadName, setCurrentUploadName] = useState('');
  const [completedUploadCount, setCompletedUploadCount] = useState(0);
  const [totalUploadCount, setTotalUploadCount] = useState(0);
  const [transferStatus, setTransferStatus] = useState<TransferStatus>('idle');
  const [fileDialog, setFileDialog] = useState<FileDialog>(null);
  const [isMutating, setIsMutating] = useState(false);

  const isUploading = transferStatus === 'uploading';
  const breadcrumbs = useMemo(() => pathBreadcrumbs(currentPath), [currentPath]);

  const loadFiles = useCallback(async (activeContainerName: string, path: string) => {
    if (!activeContainerName) {
      setFiles([]);
      return;
    }
    setIsLoading(true);
    setErrorMessage('');
    try {
      const loadedFiles = await listContainerFiles(activeContainerName, path);
      setFiles(loadedFiles);
      setSelectedEntry(null);
      setCurrentPath(path);
      setTreeNodes((current) => mergeTreeNodes(current, path, loadedFiles));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load container files.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    setCurrentPath('/');
    setTreeNodes([toTreeNode('/')]);
    void loadFiles(containerName, '/');
  }, [containerName, loadFiles]);

  const enterDirectory = (directoryName: string) => {
    void loadFiles(containerName, joinPath(currentPath, directoryName));
  };

  const goToParent = () => {
    void loadFiles(containerName, parentPathOf(currentPath));
  };

  const downloadFile = (fileName: string) => {
    window.location.href = buildDownloadUrl(containerName, joinPath(currentPath, fileName));
  };

  const uploadFiles = async (selectedFiles: FileList | File[]) => {
    const queuedFiles = Array.from(selectedFiles);
    if (!containerName || queuedFiles.length === 0 || isUploading) return;

    const controller = new AbortController();
    setUploadController(controller);
    setTotalUploadCount(queuedFiles.length);
    setCompletedUploadCount(0);
    setUploadProgress(0);
    setTransferStatus('uploading');
    setErrorMessage('');
    try {
      for (let index = 0; index < queuedFiles.length; index += 1) {
        const file = queuedFiles[index];
        setCurrentUploadName(file.name);
        setUploadProgress(0);
        await uploadContainerFile(containerName, currentPath, file, setUploadProgress, controller.signal);
        setCompletedUploadCount(index + 1);
      }
      setTransferStatus('completed');
      await loadFiles(containerName, currentPath);
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        setTransferStatus('cancelled');
      } else {
        setTransferStatus('failed');
        setErrorMessage(error instanceof Error ? error.message : 'Failed to upload file.');
      }
    } finally {
      setUploadController(null);
    }
  };

  const createDirectory = () => {
    setFileDialog({ kind: 'create-directory' });
  };

  const renameSelected = () => {
    if (!selectedEntry) return;
    setFileDialog({ kind: 'rename', entry: selectedEntry });
  };

  const deleteSelected = () => {
    if (!selectedEntry) return;
    setFileDialog({ kind: 'delete', entry: selectedEntry });
  };

  const runFileMutation = async (mutation: () => Promise<void>) => {
    setIsMutating(true);
    setErrorMessage('');
    try {
      await mutation();
      await loadFiles(containerName, currentPath);
      setFileDialog(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Container file operation failed.');
    } finally {
      setIsMutating(false);
    }
  };

  const handleFileInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (event.target.files) void uploadFiles(event.target.files);
    event.target.value = '';
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragOver(false);
    void uploadFiles(event.dataTransfer.files);
  };

  return (
    <section className={styles.tool} aria-label="Container file manager">
      <header className={styles.connectionBar}>
        <div className={styles.serverIdentity}>
          <span className={styles.serverIcon} aria-hidden="true">S</span>
          <div>
            <strong>{displayName}</strong>
            <span title={containerName}>{containerName}</span>
          </div>
        </div>
        <StatusBadge label={status} />
        <div className={styles.remotePath}>
          <span>Remote path</span>
          <code title={currentPath}>{currentPath}</code>
        </div>
      </header>

      <div className={styles.actionBar}>
        <div className={styles.primaryActions}>
          <Button type="button" onClick={goToParent} disabled={currentPath === '/' || isLoading}>상위</Button>
          <Button type="button" onClick={() => loadFiles(containerName, currentPath)} disabled={isLoading}>새로고침</Button>
          <Button type="button" onClick={createDirectory}>새 폴더</Button>
          <Button type="button" onClick={renameSelected} disabled={!selectedEntry}>이름 변경</Button>
          <Button type="button" onClick={deleteSelected} disabled={!selectedEntry}>삭제</Button>
          <Button
            type="button"
            onClick={() => selectedEntry?.type === 'FILE' && downloadFile(selectedEntry.name)}
            disabled={selectedEntry?.type !== 'FILE'}
          >
            다운로드
          </Button>
        </div>
        <label className={styles.uploadButton} aria-disabled={isUploading}>
          파일 업로드
          <input type="file" multiple onChange={handleFileInputChange} disabled={isUploading} />
        </label>
      </div>

      {errorMessage && <ErrorBanner>{errorMessage}</ErrorBanner>}

      <nav className={styles.breadcrumbBar} aria-label="Current directory">
        {breadcrumbs.map((item, index) => (
          <span key={item.path}>
            {index > 0 && <i aria-hidden="true">/</i>}
            <button type="button" onClick={() => loadFiles(containerName, item.path)}>{item.label}</button>
          </span>
        ))}
      </nav>

      <div
        className={`${styles.explorerGrid} ${isDragOver ? styles.dropActive : ''}`}
        onDragOver={(event) => {
          event.preventDefault();
          setIsDragOver(true);
        }}
        onDragLeave={() => setIsDragOver(false)}
        onDrop={handleDrop}
      >
        {isDragOver && <div className={styles.dropOverlay}>현재 폴더에 파일 업로드</div>}
        <aside className={styles.treePanel} aria-label="Remote directory tree">
          <header className={styles.panelHeader}>
            <strong>Remote site</strong>
            <span>{treeNodes.length} folders</span>
          </header>
          <nav className={styles.treeNav}>
            {treeNodes.map((node) => (
              <button
                key={node.path}
                type="button"
                className={`${styles.treeNode} ${node.path === currentPath ? styles.treeNodeActive : ''}`}
                style={{ paddingLeft: `${12 + node.depth * 16}px` }}
                onClick={() => loadFiles(containerName, node.path)}
                title={node.path}
              >
                <span aria-hidden="true">▸</span>
                <span>{node.label}</span>
              </button>
            ))}
          </nav>
        </aside>

        <section className={styles.listPanel} aria-label="Remote file list">
          <header className={styles.panelHeader}>
            <strong>Filename</strong>
            <span>{files.length} items</span>
          </header>
          {isLoading ? (
            <EmptyState>Loading files...</EmptyState>
          ) : (
            <div className={styles.tableScroll}>
              <table className={styles.fileTable}>
                <thead>
                  <tr>
                    <th>파일명</th>
                    <th>크기</th>
                    <th>유형</th>
                    <th>마지막 수정</th>
                  </tr>
                </thead>
                <tbody>
                  {files.map((file) => (
                    <tr
                      key={`${file.type}-${file.name}`}
                      tabIndex={0}
                      aria-selected={selectedEntry?.name === file.name}
                      onDoubleClick={() => file.type === 'DIRECTORY'
                        ? enterDirectory(file.name)
                        : downloadFile(file.name)}
                      onClick={() => setSelectedEntry(file)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          file.type === 'DIRECTORY' ? enterDirectory(file.name) : downloadFile(file.name);
                        }
                      }}
                    >
                      <td>
                        <span className={styles.fileNameCell}>
                          <span className={styles.fileIcon} aria-hidden="true">
                            {file.type === 'DIRECTORY' ? 'D' : 'F'}
                          </span>
                          <span>{file.name}</span>
                        </span>
                      </td>
                      <td>{file.type === 'DIRECTORY' ? '-' : formatBytes(file.size)}</td>
                      <td>{file.type === 'DIRECTORY' ? 'Directory' : 'File'}</td>
                      <td>{new Date(file.lastModified).toLocaleString()}</td>
                    </tr>
                  ))}
                  {files.length === 0 && (
                    <tr><td colSpan={4}><EmptyState>이 디렉터리는 비어 있습니다.</EmptyState></td></tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>

      <footer className={styles.transferPanel} aria-live="polite">
        <div className={styles.transferSummary}>
          <strong>전송 대기열</strong>
          <span>{transferMessage(transferStatus, completedUploadCount, totalUploadCount)}</span>
        </div>
        <div className={styles.transferDetail}>
          <span className={styles.transferFile} title={currentUploadName}>{currentUploadName || '대기 중인 파일 없음'}</span>
          <div className={styles.progressTrack} aria-label={`Upload progress ${uploadProgress}%`}>
            <span style={{ width: `${uploadProgress}%` }} />
          </div>
          <strong>{isUploading ? `${uploadProgress}%` : transferStatus === 'completed' ? '완료' : '-'}</strong>
          <Button type="button" onClick={() => uploadController?.abort()} disabled={!isUploading}>취소</Button>
        </div>
      </footer>

      <TextInputDialog
        confirmLabel="생성"
        description="현재 Remote directory에 새 폴더를 만듭니다."
        isOpen={fileDialog?.kind === 'create-directory'}
        isSubmitting={isMutating}
        label="폴더 이름"
        onClose={() => setFileDialog(null)}
        onConfirm={(name) => void runFileMutation(() => createContainerDirectory(containerName, currentPath, name))}
        placeholder="예: project-assets"
        title="새 폴더"
      />
      <TextInputDialog
        confirmLabel="이름 변경"
        description="선택한 파일 또는 폴더의 이름을 변경합니다."
        initialValue={fileDialog?.kind === 'rename' ? fileDialog.entry.name : ''}
        isOpen={fileDialog?.kind === 'rename'}
        isSubmitting={isMutating}
        label="새 이름"
        onClose={() => setFileDialog(null)}
        onConfirm={(name) => {
          if (fileDialog?.kind !== 'rename') return;
          void runFileMutation(() => renameContainerPath(
            containerName,
            joinPath(currentPath, fileDialog.entry.name),
            name
          ));
        }}
        title="이름 변경"
      />
      <ConfirmDialog
        confirmLabel="삭제"
        description={fileDialog?.kind === 'delete'
          ? `${fileDialog.entry.name} 항목과 하위 내용을 삭제합니다. 이 작업은 되돌릴 수 없습니다.`
          : ''}
        isOpen={fileDialog?.kind === 'delete'}
        isSubmitting={isMutating}
        onClose={() => setFileDialog(null)}
        onConfirm={() => {
          if (fileDialog?.kind !== 'delete') return;
          void runFileMutation(() => deleteContainerPath(
            containerName,
            joinPath(currentPath, fileDialog.entry.name)
          ));
        }}
        title="파일 삭제"
        tone="danger"
      />
    </section>
  );
}

function mergeTreeNodes(current: TreeNode[], currentPath: string, files: ContainerFileEntry[]) {
  const nodeMap = new Map(current.map((node) => [node.path, node]));
  nodeMap.set(currentPath, toTreeNode(currentPath));
  files.filter((file) => file.type === 'DIRECTORY').forEach((directory) => {
    const path = joinPath(currentPath, directory.name);
    nodeMap.set(path, toTreeNode(path));
  });
  return [...nodeMap.values()].sort((first, second) => first.path.localeCompare(second.path));
}

function toTreeNode(path: string): TreeNode {
  if (path === '/') return { path, label: '/', depth: 0 };
  const segments = path.split('/').filter(Boolean);
  return { path, label: segments[segments.length - 1], depth: segments.length };
}

function pathBreadcrumbs(path: string) {
  const segments = path.split('/').filter(Boolean);
  return [
    { path: '/', label: 'root' },
    ...segments.map((segment, index) => ({ path: `/${segments.slice(0, index + 1).join('/')}`, label: segment }))
  ];
}

function joinPath(basePath: string, name: string) {
  return basePath === '/' ? `/${name}` : `${basePath}/${name}`;
}

function parentPathOf(path: string) {
  if (path === '/') return '/';
  return path.slice(0, path.lastIndexOf('/')) || '/';
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`;
}

function transferMessage(status: TransferStatus, completed: number, total: number) {
  if (status === 'uploading') return `${completed}/${total} 파일 전송됨`;
  if (status === 'completed') return `${total}개 파일 전송 완료`;
  if (status === 'cancelled') return '사용자가 전송을 취소했습니다.';
  if (status === 'failed') return '전송 중 오류가 발생했습니다.';
  return '파일을 선택하거나 이 화면에 끌어다 놓으세요. 최대 512MB';
}
