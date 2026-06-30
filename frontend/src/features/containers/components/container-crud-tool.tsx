import { FormEvent, useState } from 'react';

import { createContainer } from '/src/features/containers/lib/container-api-client';
import {
  areValidPortBindings,
  DraftPortBinding,
  PortBindingsEditor,
  toPortBindingPayload
} from '/src/features/containers/components/port-bindings-editor';
import {
  areValidVolumeMounts,
  DraftVolumeMount,
  toVolumeMountPayload,
  VolumeMountsEditor
} from '/src/features/containers/components/volume-mounts-editor';
import { Button } from '/src/shared/components/button';

export function ContainerCrudTool() {
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [rootPassword, setRootPassword] = useState('');
  const [cpuCores, setCpuCores] = useState('0.5');
  const [memoryMb, setMemoryMb] = useState('256');
  const [portBindings, setPortBindings] = useState<DraftPortBinding[]>([]);
  const [volumeMounts, setVolumeMounts] = useState<DraftVolumeMount[]>([]);
  const [isCreating, setIsCreating] = useState(false);

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedDisplayName = displayName.trim();
    const parsedCpuCores = Number(cpuCores);
    const parsedMemoryMb = Number(memoryMb);
    if (
      !trimmedDisplayName ||
      !isValidResourceLimits(parsedCpuCores, parsedMemoryMb) ||
      rootPassword.length < 8 ||
      !areValidPortBindings(portBindings) ||
      !areValidVolumeMounts(volumeMounts)
    ) {
      return;
    }

    setIsCreating(true);
    setErrorMessage('');
    setSuccessMessage('');
    try {
      await createContainer(trimmedDisplayName, rootPassword, {
        cpuCores: parsedCpuCores,
        memoryMb: parsedMemoryMb
      }, toPortBindingPayload(portBindings), toVolumeMountPayload(volumeMounts));
      setDisplayName('');
      setRootPassword('');
      setCpuCores('0.5');
      setMemoryMb('256');
      setPortBindings([]);
      setVolumeMounts([]);
      setSuccessMessage('컨테이너가 생성되었습니다. 대시보드에서 실행 상태를 확인하세요.');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to create container.');
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <section className="crud-tool" aria-busy={isCreating}>
      <form className="create-panel create-form-card" onSubmit={handleCreate}>
        <div>
          <h2>새 컨테이너</h2>
          <p>컨테이너 이름, root 비밀번호, 리소스 제한과 포트 포워딩을 설정합니다.</p>
        </div>

        {errorMessage && <div className="error-banner">{errorMessage}</div>}
        {successMessage && <div className="success-banner">{successMessage}</div>}

        <div className="create-form-grid">
          <div className="form-field">
            <label htmlFor="container-display-name">컨테이너 이름</label>
            <input
              id="container-display-name"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="Input Container name"
            />
          </div>
          <div className="form-field">
            <label htmlFor="container-root-password">root 비밀번호</label>
            <input
              id="container-root-password"
              type="password"
              value={rootPassword}
              onChange={(event) => setRootPassword(event.target.value)}
              placeholder="8자 이상"
            />
          </div>
          <div className="form-field">
            <label htmlFor="container-cpu-cores">CPU 제한</label>
            <input
              id="container-cpu-cores"
              type="number"
              min="0.1"
              max="4"
              step="0.1"
              value={cpuCores}
              onChange={(event) => setCpuCores(event.target.value)}
            />
          </div>
          <div className="form-field">
            <label htmlFor="container-memory-mb">메모리 제한(MB)</label>
            <input
              id="container-memory-mb"
              type="number"
              min="128"
              max="4096"
              step="64"
              value={memoryMb}
              onChange={(event) => setMemoryMb(event.target.value)}
            />
          </div>
        </div>

        <PortBindingsEditor value={portBindings} onChange={setPortBindings} />
        <VolumeMountsEditor value={volumeMounts} onChange={setVolumeMounts} />

        <div className="create-form-actions">
          <Button
            type="submit"
            variant="primary"
            disabled={
              isCreating ||
              !displayName.trim() ||
              rootPassword.length < 8 ||
              !isValidResourceLimits(Number(cpuCores), Number(memoryMb)) ||
              !areValidPortBindings(portBindings) ||
              !areValidVolumeMounts(volumeMounts)
            }
          >
            생성
          </Button>
        </div>
      </form>
    </section>
  );
}

function isValidResourceLimits(cpuCores: number, memoryMb: number) {
  return cpuCores >= 0.1 && cpuCores <= 4 && memoryMb >= 128 && memoryMb <= 4096;
}
