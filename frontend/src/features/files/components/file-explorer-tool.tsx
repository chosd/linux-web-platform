import { ChangeEvent, DragEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { ContainerSummary, listContainers } from '/src/features/containers/lib/container-api-client';
import {
  buildDownloadUrl,
  ContainerFileEntry,
  listContainerFiles,
  uploadContainerFile
} from '/src/features/files/lib/container-file-api-client';
import { Button } from '/src/shared/components/button';
import { StatusBadge } from '/src/shared/components/status-badge';

type TreeNode = {
  path: string;
  label: string;
};

export function FileExplorerTool() {
  const [containers, setContainers] = useState<ContainerSummary[]>([]);
  const [selectedContainerName, setSelectedContainerName] = useState('');
  const [currentPath, setCurrentPath] = useState('/');
  const [files, setFiles] = useState<ContainerFileEntry[]>([]);
  const [treeNodes, setTreeNodes] = useState<TreeNode[]>([{ path: '/', label: '/' }]);
  const [isLoading, setIsLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [isDragOver, setIsDragOver] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const selectedContainer = useMemo(
    () => containers.find((container) => container.containerName === selectedContainerName),
    [containers, selectedContainerName]
  );

  const loadContainers = useCallback(async () => {
    try {
      const loadedContainers = await listContainers();
      setContainers(loadedContainers);
      setSelectedContainerName((current) => current || loadedContainers[0]?.containerName || '');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load containers.');
    }
  }, []);

  const loadFiles = useCallback(async (containerName: string, path: string) => {
    if (!containerName) {
      setFiles([]);
      return;
    }
    setIsLoading(true);
    setErrorMessage('');
    try {
      const loadedFiles = await listContainerFiles(containerName, path);
      setFiles(loadedFiles);
      setCurrentPath(path);
      setTreeNodes((current) => mergeTreeNodes(current, path, loadedFiles));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load container files.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadContainers();
  }, [loadContainers]);

  useEffect(() => {
    if (selectedContainerName) {
      setCurrentPath('/');
      setTreeNodes([{ path: '/', label: '/' }]);
      void loadFiles(selectedContainerName, '/');
    }
  }, [loadFiles, selectedContainerName]);

  const enterDirectory = (directoryName: string) => {
    void loadFiles(selectedContainerName, joinPath(currentPath, directoryName));
  };

  const goToParent = () => {
    void loadFiles(selectedContainerName, parentPathOf(currentPath));
  };

  const downloadFile = (fileName: string) => {
    window.location.href = buildDownloadUrl(selectedContainerName, joinPath(currentPath, fileName));
  };

  const uploadFiles = async (selectedFiles: FileList | File[]) => {
    if (!selectedContainerName || selectedFiles.length === 0) {
      return;
    }
    setIsUploading(true);
    setErrorMessage('');
    try {
      for (const file of Array.from(selectedFiles)) {
        await uploadContainerFile(selectedContainerName, currentPath, file);
      }
      await loadFiles(selectedContainerName, currentPath);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to upload file.');
    } finally {
      setIsUploading(false);
    }
  };

  const handleFileInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (event.target.files) {
      void uploadFiles(event.target.files);
      event.target.value = '';
    }
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragOver(false);
    void uploadFiles(event.dataTransfer.files);
  };

  return (
    <section className="file-explorer-tool">
      <div className="file-toolbar">
        <div className="form-field">
          <label htmlFor="file-container-select">컨테이너</label>
          <select
            id="file-container-select"
            value={selectedContainerName}
            onChange={(event) => setSelectedContainerName(event.target.value)}
          >
            {containers.map((container) => (
              <option key={container.containerName} value={container.containerName}>
                {container.displayName}
              </option>
            ))}
          </select>
        </div>
        {selectedContainer && <StatusBadge label={selectedContainer.status} />}
        <Button type="button" onClick={() => loadFiles(selectedContainerName, currentPath)} disabled={isLoading}>
          새로고침
        </Button>
        <label className="file-upload-button">
          파일 선택
          <input type="file" multiple onChange={handleFileInputChange} disabled={isUploading} />
        </label>
      </div>

      {errorMessage && <div className="error-banner">{errorMessage}</div>}

      <div
        className={`file-explorer-grid ${isDragOver ? 'file-drop-active' : ''}`}
        onDragOver={(event) => {
          event.preventDefault();
          setIsDragOver(true);
        }}
        onDragLeave={() => setIsDragOver(false)}
        onDrop={handleDrop}
      >
        <aside className="file-tree-panel" aria-label="Directory tree">
          <header>
            <h2>디렉토리</h2>
            <span>{selectedContainer?.displayName || 'No container'}</span>
          </header>
          <nav>
            {treeNodes.map((node) => (
              <button
                key={node.path}
                type="button"
                className={node.path === currentPath ? 'file-tree-node file-tree-node-active' : 'file-tree-node'}
                onClick={() => loadFiles(selectedContainerName, node.path)}
              >
                {node.label}
              </button>
            ))}
          </nav>
        </aside>

        <section className="file-list-panel">
          <header className="file-path-header">
            <div>
              <h2>{currentPath}</h2>
              <p>폴더는 더블 클릭으로 진입하고, 파일은 클릭하면 다운로드됩니다.</p>
            </div>
            <Button type="button" onClick={goToParent} disabled={currentPath === '/' || isLoading}>
              상위
            </Button>
          </header>

          {isLoading ? (
            <div className="empty-state">Loading files...</div>
          ) : (
            <div className="file-table-scroll">
              <table className="file-table">
                <thead>
                  <tr>
                    <th>이름</th>
                    <th>유형</th>
                    <th>크기</th>
                    <th>수정일</th>
                  </tr>
                </thead>
                <tbody>
                  {files.map((file) => (
                    <tr
                      key={`${file.type}-${file.name}`}
                      onDoubleClick={() => file.type === 'DIRECTORY' && enterDirectory(file.name)}
                      onClick={() => file.type === 'FILE' && downloadFile(file.name)}
                    >
                      <td>
                        <span className="file-name-cell">
                          <small>{file.type === 'DIRECTORY' ? 'DIR' : 'FILE'}</small>
                          {file.name}
                        </span>
                      </td>
                      <td>{file.type}</td>
                      <td>{file.type === 'DIRECTORY' ? '-' : formatBytes(file.size)}</td>
                      <td>{new Date(file.lastModified).toLocaleString()}</td>
                    </tr>
                  ))}
                  {files.length === 0 && (
                    <tr>
                      <td colSpan={4}>
                        <div className="empty-state">No files.</div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
          {isUploading && <div className="upload-state">Uploading...</div>}
        </section>
      </div>
    </section>
  );
}

function mergeTreeNodes(current: TreeNode[], currentPath: string, files: ContainerFileEntry[]) {
  const nodeMap = new Map(current.map((node) => [node.path, node]));
  nodeMap.set(currentPath, { path: currentPath, label: currentPath });
  files
    .filter((file) => file.type === 'DIRECTORY')
    .forEach((directory) => {
      const path = joinPath(currentPath, directory.name);
      nodeMap.set(path, { path, label: path });
    });
  return [...nodeMap.values()].sort((first, second) => first.path.localeCompare(second.path));
}

function joinPath(basePath: string, name: string) {
  if (basePath === '/') {
    return `/${name}`;
  }
  return `${basePath}/${name}`;
}

function parentPathOf(path: string) {
  if (path === '/') {
    return '/';
  }
  const parentPath = path.slice(0, path.lastIndexOf('/'));
  return parentPath || '/';
}

function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
