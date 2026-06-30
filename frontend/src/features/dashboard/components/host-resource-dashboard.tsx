import { Button } from '/src/shared/components/button';
import { HostResourceStatsSample } from '/src/features/dashboard/lib/dashboard-api-client';

type HostResourceDashboardProps = {
  stats: HostResourceStatsSample | null;
  isLoading: boolean;
  errorMessage: string;
  onRefresh: () => void;
};

export function HostResourceDashboard({ stats, isLoading, errorMessage, onRefresh }: HostResourceDashboardProps) {
  return (
    <section className="host-resource-panel" aria-busy={isLoading}>
      <header className="panel-header">
        <div>
          <h2>호스트 리소스 현황</h2>
          <p>백엔드가 실행 중인 Linux 호스트 기준의 전체 CPU와 메모리 사용량입니다.</p>
        </div>
        <Button type="button" onClick={onRefresh} disabled={isLoading}>
          새로고침
        </Button>
      </header>

      {errorMessage ? (
        <div className="error-banner">{errorMessage}</div>
      ) : !stats ? (
        <div className="empty-state">Loading host resources...</div>
      ) : (
        <div className="resource-overview-grid">
          <ResourceMeter
            label="CPU"
            value={`${stats.cpuPercent.toFixed(1)}%`}
            detail="전체 CPU 사용률"
            percent={stats.cpuPercent}
            tone="primary"
          />
          <ResourceMeter
            label="Memory"
            value={`${formatMb(stats.memoryUsageMb)} / ${formatMb(stats.memoryTotalMb)}`}
            detail={`${stats.memoryPercent.toFixed(1)}% 사용 중`}
            percent={stats.memoryPercent}
            tone="accent"
          />
          <div className="resource-meta-panel">
            <span>기준</span>
            <strong>Linux Host</strong>
            <small>{new Date(stats.timestamp).toLocaleString()}</small>
          </div>
        </div>
      )}
    </section>
  );
}

type ResourceMeterProps = {
  label: string;
  value: string;
  detail: string;
  percent: number;
  tone: 'primary' | 'accent';
};

function ResourceMeter({ label, value, detail, percent, tone }: ResourceMeterProps) {
  return (
    <article className="resource-meter">
      <div className="resource-meter-heading">
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      <div className="resource-meter-track" aria-label={`${label} ${percent.toFixed(1)}%`}>
        <div
          className={`resource-meter-fill resource-meter-fill-${tone}`}
          style={{ width: `${clampPercent(percent)}%` }}
        />
      </div>
      <small>{detail}</small>
    </article>
  );
}

function clampPercent(value: number) {
  return Math.max(0, Math.min(100, value));
}

function formatMb(value: number) {
  if (value >= 1024) {
    return `${(value / 1024).toFixed(1)} GB`;
  }
  return `${value.toFixed(0)} MB`;
}
