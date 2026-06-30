import { useCallback, useEffect, useState } from 'react';

import {
  getHostResourceStats,
  HostResourceStatsSample
} from '/src/features/dashboard/lib/dashboard-api-client';

export function useHostResourceStats(refreshIntervalMs = 3000) {
  const [stats, setStats] = useState<HostResourceStatsSample | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  const loadStats = useCallback(async () => {
    setErrorMessage('');
    try {
      setStats(await getHostResourceStats());
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load host resources.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStats();
    const intervalId = window.setInterval(() => {
      void loadStats();
    }, refreshIntervalMs);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [loadStats, refreshIntervalMs]);

  return { stats, isLoading, errorMessage, reload: loadStats };
}
