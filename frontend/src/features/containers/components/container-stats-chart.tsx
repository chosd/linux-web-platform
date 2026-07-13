import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';

import { useContainerStats } from '/src/features/containers/lib/use-container-stats';
import { EmptyState, ErrorBanner } from '/src/shared/components/feedback';
import { PanelHeader } from '/src/shared/components/panel-header';

import styles from './container-components.module.css';

type ContainerStatsChartProps = {
  containerName: string | null;
  displayName?: string;
  cpuCores?: number;
  memoryMb?: number;
};

export function ContainerStatsChart({ containerName, displayName, cpuCores, memoryMb }: ContainerStatsChartProps) {
  const { samples, errorMessage } = useContainerStats(containerName);

  const limitCpu = cpuCores && cpuCores > 0 ? cpuCores : 0.5;
  const limitMem = memoryMb && memoryMb > 0 ? memoryMb : 256;

  const chartData = samples.map((sample) => {
    const cpuUsagePercent = (sample.cpuPercent / limitCpu);
    const memoryPercent = (sample.memoryUsageMb / limitMem) * 100;
    return {
      ...sample,
      time: new Date(sample.timestamp).toLocaleTimeString(),
      cpuPercent: Math.min(100, Math.max(0, cpuUsagePercent)),
      memoryPercent: Math.min(100, Math.max(0, memoryPercent))
    };
  });

  const latest = samples.length > 0 ? samples[samples.length - 1] : undefined;

  return (
    <section className={styles.statsPanel}>
      <PanelHeader
        title="실시간 자원 사용량"
        description={displayName || '컨테이너를 선택하면 CPU와 메모리 추이가 표시됩니다.'}
        actions={
          latest && (
          <div className={styles.statsSummary}>
            <span>CPU {Math.min(100, latest.cpuPercent / limitCpu).toFixed(1)}%</span>
            <span>
              MEM {latest.memoryUsageMb.toFixed(1)} / {limitMem} MB
            </span>
          </div>
          )
        }
      />

      {!containerName ? (
        <EmptyState>모니터링할 컨테이너를 선택하세요.</EmptyState>
      ) : errorMessage ? (
        <ErrorBanner>{errorMessage}</ErrorBanner>
      ) : (
        <div className={styles.statsChart}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 12, right: 20, bottom: 0, left: 0 }}>
              <CartesianGrid stroke="rgba(255,255,255,0.08)" vertical={false} />
              <XAxis dataKey="time" stroke="#747b8d" tick={{ fontSize: 11 }} minTickGap={24} />
              <YAxis stroke="#747b8d" tick={{ fontSize: 11 }} domain={[0, 100]} unit="%" />
              <Tooltip
                contentStyle={{
                  background: '#16171d',
                  border: '1px solid rgba(255,255,255,0.12)',
                  borderRadius: 12,
                  color: '#f7f8fb'
                }}
              />
              <Line
                type="monotone"
                dataKey="cpuPercent"
                name="CPU %"
                stroke="#5b8cff"
                strokeWidth={2}
                dot={false}
                isAnimationActive
              />
              <Line
                type="monotone"
                dataKey="memoryPercent"
                name="Memory %"
                stroke="#a855f7"
                strokeWidth={2}
                dot={false}
                isAnimationActive
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  );
}
