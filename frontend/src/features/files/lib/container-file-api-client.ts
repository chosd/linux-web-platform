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

export async function uploadContainerFile(containerName: string, path: string, file: File) {
  const formData = new FormData();
  formData.append('path', path);
  formData.append('file', file);
  const response = await fetch(
    `${containerApiBaseUrl}/api/containers/${encodeURIComponent(containerName)}/files/upload`,
    {
      method: 'POST',
      headers: userHeaders,
      body: formData
    }
  );
  if (!response.ok) {
    throw new Error(`Container file upload request failed: ${response.status}`);
  }
}
