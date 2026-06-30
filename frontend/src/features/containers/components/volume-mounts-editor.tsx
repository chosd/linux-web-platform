import { VolumeMountPayload } from '/src/features/containers/lib/container-api-client';
import { allowedVolumeHostPathBase } from '/src/features/containers/config/container-api';
import { Button } from '/src/shared/components/button';

type DraftVolumeMount = {
  id: string;
  hostPath: string;
  containerPath: string;
};

type VolumeMountsEditorProps = {
  value: DraftVolumeMount[];
  onChange: (value: DraftVolumeMount[]) => void;
};

export type { DraftVolumeMount };

export function VolumeMountsEditor({ value, onChange }: VolumeMountsEditorProps) {
  const addMount = () => {
    onChange([
      ...value,
      {
        id: crypto.randomUUID(),
        hostPath: '',
        containerPath: ''
      }
    ]);
  };

  const updateMount = (id: string, patch: Partial<DraftVolumeMount>) => {
    onChange(value.map((mount) => (mount.id === id ? { ...mount, ...patch } : mount)));
  };

  const removeMount = (id: string) => {
    onChange(value.filter((mount) => mount.id !== id));
  };

  return (
    <div className="volume-mounts-editor">
      <div className="settings-editor-header">
        <div>
          <h3>볼륨 설정</h3>
          <p>호스트 디렉토리를 컨테이너 내부 경로에 연결해 데이터를 보존합니다.</p>
        </div>
        <Button type="button" onClick={addMount}>
          + 추가
        </Button>
      </div>

      {value.length === 0 ? (
        <div className="settings-empty-hint">
          설정된 볼륨 마운트가 없습니다. 오른쪽 추가 버튼을 눌러 디렉토리를 연결하세요.
        </div>
      ) : (
        <div className="volume-mount-list">
          {value.map((mount) => {
            const hostPathError = mount.hostPath !== '' && !isValidHostPath(mount.hostPath);
            const containerPathError = mount.containerPath !== '' && !isValidContainerPath(mount.containerPath);
            return (
              <div className="volume-mount-row" key={mount.id}>
                <div className="form-field">
                  <label htmlFor={`${mount.id}-host-path`}>호스트 경로</label>
                  <input
                    id={`${mount.id}-host-path`}
                    value={mount.hostPath}
                    onChange={(event) => updateMount(mount.id, { hostPath: event.target.value })}
                    placeholder={`${normalizedAllowedVolumeHostPathBase()}/test2`}
                    aria-invalid={hostPathError}
                  />
                </div>
                <div className="form-field">
                  <label htmlFor={`${mount.id}-container-path`}>컨테이너 경로</label>
                  <input
                    id={`${mount.id}-container-path`}
                    value={mount.containerPath}
                    onChange={(event) => updateMount(mount.id, { containerPath: event.target.value })}
                    placeholder="/workspace"
                    aria-invalid={containerPathError}
                  />
                </div>
                <Button type="button" onClick={() => removeMount(mount.id)}>
                  삭제
                </Button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export function toVolumeMountPayload(mounts: DraftVolumeMount[]): VolumeMountPayload[] {
  return mounts.map((mount) => ({
    hostPath: mount.hostPath.trim(),
    containerPath: mount.containerPath.trim()
  }));
}

export function areValidVolumeMounts(mounts: DraftVolumeMount[]) {
  const hostPaths = new Set<string>();
  const containerPaths = new Set<string>();
  return mounts.every((mount) => {
    const hostPath = mount.hostPath.trim();
    const containerPath = mount.containerPath.trim();
    if (!isValidHostPath(hostPath) || !isValidContainerPath(containerPath)) {
      return false;
    }
    if (hostPaths.has(hostPath) || containerPaths.has(containerPath)) {
      return false;
    }
    hostPaths.add(hostPath);
    containerPaths.add(containerPath);
    return true;
  });
}

function isValidHostPath(value: string) {
  return value.startsWith(`${normalizedAllowedVolumeHostPathBase()}/`) && !value.includes(':');
}

function isValidContainerPath(value: string) {
  return value.startsWith('/') && value !== '/' && !value.includes(':');
}

function normalizedAllowedVolumeHostPathBase() {
  return allowedVolumeHostPathBase.replace(/\/+$/, '') || '/mnt/storage';
}
