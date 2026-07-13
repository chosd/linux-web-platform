import { useEffect, useState } from 'react';

import { containerApiBaseUrl, userId } from '/src/features/containers/config/container-api';
import { ContainerStatsSample } from '/src/features/containers/lib/container-api-client';

const maxSamples = 30;

export function useContainerStats(containerName: string | null) {
  const [samples, setSamples] = useState<ContainerStatsSample[]>([]);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    setSamples([]);
    setErrorMessage('');
    if (!containerName) {
      return;
    }

    const url = `${containerApiBaseUrl}/api/containers/${encodeURIComponent(
      containerName
    )}/stats/stream?userId=${encodeURIComponent(userId)}`;
    const eventSource = new EventSource(url);

    eventSource.addEventListener('container-stats', (event) => {
      try {
        const sample = JSON.parse((event as MessageEvent).data) as ContainerStatsSample;
        setSamples((current) => [...current.slice(-(maxSamples - 1)), sample]);
        setErrorMessage('');
      } catch {
        setErrorMessage('Invalid container stats response.');
      }
    });

    eventSource.onerror = () => {
      setErrorMessage('Failed to receive container stats.');
    };

    return () => {
      eventSource.close();
    };
  }, [containerName]);

  return { samples, errorMessage };
}
