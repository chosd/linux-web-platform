import { PortBindingPayload, PortProtocol } from '/src/features/containers/lib/container-api-client';
import { Button } from '/src/shared/components/button';
import { FormField } from '/src/shared/components/form-field';

import styles from './container-components.module.css';

type DraftPortBinding = {
  id: string;
  protocol: PortProtocol;
  hostPort: string;
  containerPort: string;
};

type PortBindingsEditorProps = {
  value: DraftPortBinding[];
  onChange: (value: DraftPortBinding[]) => void;
};

export type { DraftPortBinding };

export function PortBindingsEditor({ value, onChange }: PortBindingsEditorProps) {
  const addBinding = () => {
    onChange([
      ...value,
      {
        id: crypto.randomUUID(),
        protocol: 'TCP',
        hostPort: '',
        containerPort: ''
      }
    ]);
  };

  const updateBinding = (id: string, patch: Partial<DraftPortBinding>) => {
    onChange(value.map((binding) => (binding.id === id ? { ...binding, ...patch } : binding)));
  };

  const removeBinding = (id: string) => {
    onChange(value.filter((binding) => binding.id !== id));
  };

  return (
    <div className={styles.settingsEditor}>
      <div className={styles.editorHeader}>
        <div>
          <h3>포트 설정</h3>
          <p>외부에서 접속할 호스트 포트와 컨테이너 내부 포트를 연결합니다.</p>
        </div>
        <Button type="button" onClick={addBinding}>
          + 추가
        </Button>
      </div>

      {value.length === 0 ? (
        <div className={styles.emptyHint}>
          설정된 포트 포워딩이 없습니다. 오른쪽 추가 버튼을 눌러 포트를 연결하세요.
        </div>
      ) : (
        <div className={styles.portBindingList}>
          {value.map((binding) => {
            const hostPortError = binding.hostPort !== '' && !isValidHostPort(binding.hostPort);
            const containerPortError = binding.containerPort !== '' && !isValidContainerPort(binding.containerPort);
            return (
              <div className={styles.portBindingRow} key={binding.id}>
                <FormField htmlFor={`${binding.id}-protocol`} label="프로토콜">
                  <select
                    id={`${binding.id}-protocol`}
                    value={binding.protocol}
                    onChange={(event) => updateBinding(binding.id, { protocol: event.target.value as PortProtocol })}
                  >
                    <option value="TCP">TCP</option>
                    <option value="UDP">UDP</option>
                  </select>
                </FormField>
                <FormField htmlFor={`${binding.id}-host-port`} label="호스트 포트">
                  <input
                    id={`${binding.id}-host-port`}
                    inputMode="numeric"
                    value={binding.hostPort}
                    onChange={(event) => updateBinding(binding.id, { hostPort: event.target.value })}
                    placeholder="8080"
                    aria-invalid={hostPortError}
                  />
                </FormField>
                <FormField htmlFor={`${binding.id}-container-port`} label="컨테이너 포트">
                  <input
                    id={`${binding.id}-container-port`}
                    inputMode="numeric"
                    value={binding.containerPort}
                    onChange={(event) => updateBinding(binding.id, { containerPort: event.target.value })}
                    placeholder="80"
                    aria-invalid={containerPortError}
                  />
                </FormField>
                <Button type="button" onClick={() => removeBinding(binding.id)}>
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

export function toPortBindingPayload(bindings: DraftPortBinding[]): PortBindingPayload[] {
  return bindings.map((binding) => ({
    protocol: binding.protocol,
    hostPort: Number(binding.hostPort),
    containerPort: Number(binding.containerPort)
  }));
}

export function areValidPortBindings(bindings: DraftPortBinding[]) {
  const hostPorts = new Set<string>();
  const containerPorts = new Set<string>();
  return bindings.every((binding) => {
    if (!isValidHostPort(binding.hostPort) || !isValidContainerPort(binding.containerPort)) {
      return false;
    }
    const hostKey = `${binding.protocol}:${binding.hostPort}`;
    const containerKey = `${binding.protocol}:${binding.containerPort}`;
    if (hostPorts.has(hostKey) || containerPorts.has(containerKey)) {
      return false;
    }
    hostPorts.add(hostKey);
    containerPorts.add(containerKey);
    return true;
  });
}

function isValidHostPort(value: string) {
  return isIntegerString(value) && Number(value) >= 1024 && Number(value) <= 65535;
}

function isValidContainerPort(value: string) {
  return isIntegerString(value) && Number(value) >= 1 && Number(value) <= 65535;
}

function isIntegerString(value: string) {
  return /^[0-9]+$/.test(value);
}
