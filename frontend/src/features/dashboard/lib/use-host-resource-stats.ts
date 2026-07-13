import { useCallback, useEffect, useState } from 'react';

import {
  getHostResourceStats,
  HostResourceStatsSample
} from '/src/features/dashboard/lib/dashboard-api-client';

export function useHostResourceStats(refreshIntervalMs = 3000) {
  const [stats, setStats] = useState<HostResourceStatsSample | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  const loadStats = useCallback(async (signal?: AbortSignal) => {
    setErrorMessage('');
    try {
      setStats(await getHostResourceStats(signal));
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load host resources.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let timerId: number | null = null;
    let controller: AbortController | null = null;
    let active = true;
    const poll = async () => {
      controller = new AbortController();
      await loadStats(controller.signal);
      if (active && !document.hidden) {
        timerId = window.setTimeout(poll, refreshIntervalMs);
      }
    };
    const handleVisibilityChange = () => {
      if (!document.hidden && timerId === null) void poll();
      if (document.hidden && timerId !== null) {
        window.clearTimeout(timerId);
        timerId = null;
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    void poll();

    return () => {
      active = false;
      controller?.abort();
      if (timerId !== null) window.clearTimeout(timerId);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [loadStats, refreshIntervalMs]);

  return { stats, isLoading, errorMessage, reload: loadStats };
}
