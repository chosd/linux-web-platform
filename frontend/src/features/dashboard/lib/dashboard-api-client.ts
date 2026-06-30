import { containerApiBaseUrl } from '/src/features/containers/config/container-api';

export type HostResourceStatsSample = {
  timestamp: string;
  cpuPercent: number;
  memoryUsageMb: number;
  memoryTotalMb: number;
  memoryPercent: number;
};

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

export async function getHostResourceStats() {
  const response = await fetch(`${containerApiBaseUrl}/api/dashboard/resources`);
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, 'Host resource request failed'));
  }
  return (await response.json()) as HostResourceStatsSample;
}
