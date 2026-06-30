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

export type PortProtocol = 'TCP' | 'UDP';

export type PortBindingPayload = {
  protocol: PortProtocol;
  hostPort: number;
  containerPort: number;
};

export type PortMapping = {
  hostPort?: number;
  containerPort?: number;
  protocol: PortProtocol;
  hostIp?: string;
  url?: string;
};

export type ContainerNetwork = {
  name: string;
  networkId: string;
  ipAddress: string;
  gateway: string;
  macAddress: string;
};

export type ContainerNetworkDashboard = {
  containerName: string;
  networks: ContainerNetwork[];
  ports: PortMapping[];
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
    throw new Error(await parseErrorMessage(response, fallbackMessage));
  }
  return (await response.json()) as ContainerSummary;
}

async function parseErrorMessage(response: Response, fallbackMessage: string) {
  try {
    const body = (await response.json()) as { message?: string };
    if (body.message) {
      return body.message;
    }
  } catch {
    // Ignore non-JSON error bodies.
  }
  return `${fallbackMessage}: ${response.status}`;
}

export async function listContainers() {
  const response = await fetch(`${containerApiBaseUrl}/api/containers`, {
    headers: userHeaders
  });
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, 'Container list request failed'));
  }
  return (await response.json()) as ContainerSummary[];
}

export async function createContainer(
  displayName: string,
  rootPassword: string,
  resourceLimits: ResourceLimitsPayload,
  portBindings: PortBindingPayload[]
) {
  const response = await fetch(`${containerApiBaseUrl}/api/containers`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ displayName, rootPassword, resourceLimits, portBindings })
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
    throw new Error(await parseErrorMessage(response, 'Container delete request failed'));
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

export async function getContainerNetworkDashboard(containerName: string) {
  const response = await fetch(
    `${containerApiBaseUrl}/api/containers/${encodeURIComponent(containerName)}/network`,
    {
      headers: userHeaders
    }
  );
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, 'Container network request failed'));
  }
  return (await response.json()) as ContainerNetworkDashboard;
}
