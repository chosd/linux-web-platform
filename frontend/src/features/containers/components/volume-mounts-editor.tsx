import { VolumeMountPayload } from '/src/features/containers/lib/container-api-client';
import { Button } from '/src/shared/components/button';
import { FormField } from '/src/shared/components/form-field';

import styles from './container-components.module.css';

type DraftVolumeMount = {
  id: string;
  hostPath: string;
  containerPath: string;
  accessMode: 'READ_WRITE' | 'READ_ONLY';
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
        containerPath: '',
        accessMode: 'READ_WRITE'
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
    <div className={styles.settingsEditor}>
      <div className={styles.editorHeader}>
        <div>
          <h3>볼륨 설정</h3>
          <p>이미 준비된 호스트 절대 경로를 컨테이너 경로에 bind mount 합니다.</p>
        </div>
        <Button type="button" onClick={addMount}>
          + 추가
        </Button>
      </div>

      {value.length === 0 ? (
        <div className={styles.emptyHint}>
          설정된 볼륨 마운트가 없습니다. 호스트에 존재하는 디렉토리 경로를 추가하세요.
        </div>
      ) : (
        <div className={styles.volumeMountList}>
          {value.map((mount) => {
            const hostPathError = mount.hostPath !== '' && !isValidHostPath(mount.hostPath);
            const containerPathError = mount.containerPath !== '' && !isValidContainerPath(mount.containerPath);
            return (
              <div className={styles.volumeMountRow} key={mount.id}>
                <FormField htmlFor={`${mount.id}-volume-name`} label="호스트 경로">
                  <input
                    id={`${mount.id}-volume-name`}
                    value={mount.hostPath}
                    onChange={(event) => updateMount(mount.id, {
                      hostPath: event.target.value,
                      containerPath: mount.containerPath || containerPathFromHostPath(event.target.value)
                    })}
                    placeholder="/data/project-a"
                    aria-invalid={hostPathError}
                  />
                </FormField>
                <FormField htmlFor={`${mount.id}-container-path`} label="컨테이너 경로">
                  <input
                    id={`${mount.id}-container-path`}
                    value={mount.containerPath}
                    onChange={(event) => updateMount(mount.id, { containerPath: event.target.value })}
                    placeholder="/workspace/app"
                    aria-invalid={containerPathError}
                  />
                </FormField>
                <FormField htmlFor={`${mount.id}-access-mode`} label="권한">
                  <select
                    id={`${mount.id}-access-mode`}
                    value={mount.accessMode}
                    onChange={(event) => updateMount(mount.id, {
                      accessMode: event.target.value as DraftVolumeMount['accessMode']
                    })}
                  >
                    <option value="READ_WRITE">읽기/쓰기</option>
                    <option value="READ_ONLY">읽기 전용</option>
                  </select>
                </FormField>
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
    containerPath: mount.containerPath.trim(),
    accessMode: mount.accessMode
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
  if (value.includes('\0')) {
    return false;
  }
  return /^(?:[a-zA-Z]:[\\\/]|\/)/.test(value);
}

function isValidContainerPath(value: string) {
  return value.startsWith('/') && value !== '/' && !value.includes(':') && !value.includes('\0');
}

function containerPathFromHostPath(hostPath: string) {
  const segment = hostPath.trim().split(/[\\\/]/).filter(Boolean).pop();
  if (!segment) {
    return '';
  }
  return `/workspace/${segment.replace(/[^a-zA-Z0-9_.-]/g, '-')}`;
}
