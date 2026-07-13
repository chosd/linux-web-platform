import { containerApiBaseUrl, userId } from '/src/features/containers/config/container-api';

export type ContainerFileType = 'DIRECTORY' | 'FILE';

export type ContainerFileEntry = {
  name: string;
  type: ContainerFileType;
  size: number;
  lastModified: string;
};

const userHeaders = {
  'X-User-Id': userId
};

export async function listContainerFiles(containerName: string, path: string) {
  const response = await fetch(
    `${containerApiBaseUrl}/api/containers/${encodeURIComponent(containerName)}/files?path=${encodeURIComponent(path)}`,
    {
      headers: userHeaders
    }
  );
  if (!response.ok) {
    throw new Error(`Container file list request failed: ${response.status}`);
  }
  return (await response.json()) as ContainerFileEntry[];
}

export function buildDownloadUrl(containerName: string, path: string) {
  return `${containerApiBaseUrl}/api/containers/${encodeURIComponent(
    containerName
  )}/files/download?path=${encodeURIComponent(path)}&userId=${encodeURIComponent(userId)}`;
}

export function uploadContainerFile(
  containerName: string,
  path: string,
  file: File,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal
) {
  const formData = new FormData();
  formData.append('path', path);
  formData.append('file', file);
  return new Promise<void>((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open(
      'POST',
      `${containerApiBaseUrl}/api/containers/${encodeURIComponent(containerName)}/files/upload`
    );
    request.setRequestHeader('X-User-Id', userId);
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress?.(Math.round((event.loaded / event.total) * 100));
      }
    };
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        resolve();
      } else {
        reject(new Error(`Container file upload request failed: ${request.status}`));
      }
    };
    request.onerror = () => reject(new Error('Container file upload request failed.'));
    request.onabort = () => reject(new DOMException('Upload cancelled.', 'AbortError'));
    signal?.addEventListener('abort', () => request.abort(), { once: true });
    request.send(formData);
  });
}

async function mutatePath(url: string, method: 'POST' | 'PATCH' | 'DELETE', body?: object) {
  const response = await fetch(url, {
    method,
    headers: body ? { ...userHeaders, 'Content-Type': 'application/json' } : userHeaders,
    body: body ? JSON.stringify(body) : undefined
  });
  if (!response.ok) {
    throw new Error(`Container file operation failed: ${response.status}`);
  }
}

export function createContainerDirectory(containerName: string, path: string, name: string) {
  return mutatePath(
    `${containerApiBaseUrl}/api/containers/${encodeURIComponent(containerName)}/directories`,
    'POST',
    { path, name }
  );
}

export function renameContainerPath(containerName: string, path: string, name: string) {
  return mutatePath(
    `${containerApiBaseUrl}/api/containers/${encodeURIComponent(containerName)}/files`,
    'PATCH',
    { path, name }
  );
}

export function deleteContainerPath(containerName: string, path: string) {
  return mutatePath(
    `${containerApiBaseUrl}/api/containers/${encodeURIComponent(containerName)}/files?path=${encodeURIComponent(path)}`,
    'DELETE'
  );
}
