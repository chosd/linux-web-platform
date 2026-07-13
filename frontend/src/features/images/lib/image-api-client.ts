import { containerApiBaseUrl } from '/src/features/containers/config/container-api';

export type DockerImage = {
  id: string;
  tags: string[];
  primaryTag: string;
  sizeBytes: number;
  createdAt: string;
};

async function errorMessage(response: Response, fallback: string) {
  try {
    const body = (await response.json()) as { message?: string };
    return body.message || `${fallback}: ${response.status}`;
  } catch {
    return `${fallback}: ${response.status}`;
  }
}

export async function listImages() {
  const response = await fetch(`${containerApiBaseUrl}/api/images`);
  if (!response.ok) throw new Error(await errorMessage(response, 'Image list request failed'));
  return (await response.json()) as DockerImage[];
}

export async function pullImage(imageName: string) {
  const response = await fetch(`${containerApiBaseUrl}/api/images/pull`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ imageName })
  });
  if (!response.ok) throw new Error(await errorMessage(response, 'Image pull request failed'));
  return (await response.json()) as DockerImage;
}
