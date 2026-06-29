import { containerApiBaseUrl, userId } from '/src/features/containers/config/container-api';

export type ContainerStatus = 'RUNNING' | 'EXITED' | 'NOT_FOUND' | string;

export type ContainerSummary = {
  userId?: string;
  containerName: string;
  displayName: string;
  status: ContainerStatus;
  createdAt: string;
  cpuCores?: number;
  memoryMb?: number;
  action?: string;
};

export type ResourceLimitsPayload = {
  cpuCores: number;
  memoryMb: number;
};

export type ContainerStatsSample = {
  timestamp: string;
  cpuPercent: number;
  memoryUsageMb: number;
  memoryLimitMb: number;
  networkRxMb: number;
  networkTxMb: number;
  blockReadMb: number;
  blockWriteMb: number;
};

const jsonHeaders = {
  'Content-Type': 'application/json',
  'X-User-Id': userId
};

const userHeaders = {
  'X-User-Id': userId
};

async function parseContainerResponse(response: Response, fallbackMessage: string) {
  if (!response.ok) {
    throw new Error(`${fallbackMessage}: ${response.status}`);
  }
  return (await response.json()) as ContainerSummary;
}

export async function listContainers() {
  const response = await fetch(`${containerApiBaseUrl}/api/containers`, {
    headers: userHeaders
  });
  if (!response.ok) {
    throw new Error(`Container list request failed: ${response.status}`);
  }
  return (await response.json()) as ContainerSummary[];
}

export async function createContainer(displayName: string, rootPassword: string, resourceLimits: ResourceLimitsPayload) {
  const response = await fetch(`${containerApiBaseUrl}/api/containers`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ displayName, rootPassword, resourceLimits })
  });
  return parseContainerResponse(response, 'Container create request failed');
}

export async function updateContainer(
  containerName: string,
  displayName: string,
  resourceLimits: ResourceLimitsPayload
) {
  const response = await fetch(`${containerApiBaseUrl}/api/containers/${encodeURIComponent(containerName)}`, {
    method: 'PATCH',
    headers: jsonHeaders,
    body: JSON.stringify({ displayName, resourceLimits })
  });
  return parseContainerResponse(response, 'Container update request failed');
}

export async function deleteContainer(containerName: string) {
  const response = await fetch(`${containerApiBaseUrl}/api/containers/${encodeURIComponent(containerName)}`, {
    method: 'DELETE',
    headers: userHeaders
  });
  if (!response.ok) {
    throw new Error(`Container delete request failed: ${response.status}`);
  }
}

export async function runContainerAction(action: 'start' | 'stop' | 'restart', containerName: string) {
  const response = await fetch(`${containerApiBaseUrl}/api/containers/${action}`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ userId, containerName })
  });
  return parseContainerResponse(response, `Container ${action} request failed`);
}
