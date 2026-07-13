import { FormEvent, useEffect, useState } from 'react';

import { ContainerSummary, createContainer } from '/src/features/containers/lib/container-api-client';
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
import { ErrorBanner, SuccessBanner } from '/src/shared/components/feedback';
import { FormField } from '/src/shared/components/form-field';
import { DockerImage, listImages } from '/src/features/images/lib/image-api-client';

import styles from './container-components.module.css';

type ContainerCrudToolProps = {
  onSuccess?: (container: ContainerSummary) => void;
  onCancel?: () => void;
};

export function ContainerCrudTool({ onSuccess, onCancel }: ContainerCrudToolProps) {
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [rootPassword, setRootPassword] = useState('');
  const [imageName, setImageName] = useState('linux-terminal-playground:ubuntu');
  const [images, setImages] = useState<DockerImage[]>([]);
  const [cpuCores, setCpuCores] = useState('0.5');
  const [memoryMb, setMemoryMb] = useState('256');
  const [portBindings, setPortBindings] = useState<DraftPortBinding[]>([]);
  const [volumeMounts, setVolumeMounts] = useState<DraftVolumeMount[]>([]);
  const [networkName, setNetworkName] = useState('bridge');
  const [networkAlias, setNetworkAlias] = useState('');
  const [composeText, setComposeText] = useState('');
  const [isComposeDirty, setIsComposeDirty] = useState(false);
  const [composeError, setComposeError] = useState('');
  const [isCreating, setIsCreating] = useState(false);

  useEffect(() => {
    void listImages().then((items) => {
      setImages(items.filter((item) => item.tags.length > 0));
      if (items.length > 0 && !items.some((item) => item.tags.includes(imageName))) setImageName(items[0].primaryTag);
    }).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!isComposeDirty) {
      setComposeText(buildComposeText({
        displayName,
        imageName,
        rootPassword,
        cpuCores,
        memoryMb,
        portBindings,
        volumeMounts,
        networkName,
        networkAlias
      }));
    }
  }, [cpuCores, displayName, imageName, isComposeDirty, memoryMb, networkAlias, networkName, portBindings, rootPassword, volumeMounts]);

  const applyComposeText = () => {
    const parsed = parseComposeText(composeText);
    if (!parsed) {
      setComposeError('YAML 옵션에서 container_name, resources, ports, volumes 형식을 확인하세요.');
      return;
    }
    setDisplayName(parsed.displayName);
    setImageName(parsed.imageName);
    setRootPassword(parsed.rootPassword);
    setCpuCores(parsed.cpuCores);
    setMemoryMb(parsed.memoryMb);
    setPortBindings(parsed.portBindings);
    setVolumeMounts(parsed.volumeMounts);
    setNetworkName(parsed.networkName);
    setNetworkAlias(parsed.networkAlias);
    setComposeError('');
    setIsComposeDirty(false);
  };

  const resetComposeText = () => {
    setComposeText(buildComposeText({
      displayName,
      imageName,
      rootPassword,
      cpuCores,
      memoryMb,
      portBindings,
      volumeMounts,
      networkName,
      networkAlias
    }));
    setComposeError('');
    setIsComposeDirty(false);
  };

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedDisplayName = displayName.trim();
    const parsedCpuCores = Number(cpuCores);
    const parsedMemoryMb = Number(memoryMb);
    if (
      !trimmedDisplayName ||
      !isValidResourceLimits(parsedCpuCores, parsedMemoryMb) ||
      rootPassword.length < 8 ||
      !isValidNetworkName(networkName) ||
      (networkAlias !== '' && !isValidNetworkAlias(networkAlias)) ||
      !areValidPortBindings(portBindings) ||
      !areValidVolumeMounts(volumeMounts)
    ) {
      return;
    }

    setIsCreating(true);
    setErrorMessage('');
    setSuccessMessage('');
    try {
      const container = await createContainer(
        trimmedDisplayName,
        rootPassword,
        {
          cpuCores: parsedCpuCores,
          memoryMb: parsedMemoryMb
        },
        toPortBindingPayload(portBindings),
        toVolumeMountPayload(volumeMounts),
        imageName,
        networkName,
        networkAlias
      );
      setDisplayName('');
      setRootPassword('');
      setCpuCores('0.5');
      setMemoryMb('256');
      setPortBindings([]);
      setVolumeMounts([]);
      setNetworkName('bridge');
      setNetworkAlias('');
      setIsComposeDirty(false);
      setComposeError('');
      setSuccessMessage('컨테이너가 생성되었습니다. 대시보드에서 실행 상태를 확인하세요.');
      if (onSuccess) {
        onSuccess(container);
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to create container.');
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <section className={styles.crudTool} aria-busy={isCreating}>
      <div className={styles.createWorkspace}>
        <form className={styles.createPanel} onSubmit={handleCreate}>
          <div>
            <h2>새 컨테이너</h2>
            <p>컨테이너 이름, root 비밀번호, 리소스, 포트, 호스트 bind mount를 설정합니다.</p>
          </div>

          {errorMessage && <ErrorBanner>{errorMessage}</ErrorBanner>}
          {successMessage && <SuccessBanner>{successMessage}</SuccessBanner>}

          <div className={styles.createFormGrid}>
            <FormField htmlFor="container-display-name" label="컨테이너 이름">
              <input
                id="container-display-name"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                placeholder="Input Container name"
              />
            </FormField>
            <FormField htmlFor="container-root-password" label="root 비밀번호">
              <input
                id="container-root-password"
                type="password"
                value={rootPassword}
                onChange={(event) => setRootPassword(event.target.value)}
                placeholder="8자 이상"
              />
            </FormField>
            <FormField htmlFor="container-image" label="Docker 이미지">
              <select id="container-image" value={imageName} onChange={(event) => setImageName(event.target.value)}>
                {!images.some((image) => image.tags.includes(imageName)) && <option value={imageName}>{imageName}</option>}
                {images.flatMap((image) => image.tags.map((tag) => <option key={`${image.id}-${tag}`} value={tag}>{tag}</option>))}
              </select>
            </FormField>
            <FormField htmlFor="container-cpu-cores" label="CPU 제한">
              <input
                id="container-cpu-cores"
                type="number"
                min="0.1"
                max="4"
                step="0.1"
                value={cpuCores}
                onChange={(event) => setCpuCores(event.target.value)}
              />
            </FormField>
            <FormField htmlFor="container-memory-mb" label="메모리 제한(MB)">
              <input
                id="container-memory-mb"
                type="number"
                min="128"
                max="4096"
                step="64"
                value={memoryMb}
                onChange={(event) => setMemoryMb(event.target.value)}
              />
            </FormField>
            <FormField htmlFor="container-network-name" label="Docker 네트워크">
              <input
                id="container-network-name"
                value={networkName}
                onChange={(event) => setNetworkName(event.target.value)}
                placeholder="bridge"
                aria-invalid={networkName !== '' && !isValidNetworkName(networkName)}
              />
            </FormField>
            <FormField htmlFor="container-network-alias" label="네트워크 별칭">
              <input
                id="container-network-alias"
                value={networkAlias}
                onChange={(event) => setNetworkAlias(event.target.value)}
                placeholder="optional"
                aria-invalid={networkAlias !== '' && !isValidNetworkAlias(networkAlias)}
              />
            </FormField>
          </div>

          <PortBindingsEditor value={portBindings} onChange={setPortBindings} />
          <VolumeMountsEditor value={volumeMounts} onChange={setVolumeMounts} />

          <div className={styles.createFormActions}>
            <Button
              type="submit"
              variant="primary"
              disabled={
                isCreating ||
                !displayName.trim() ||
                rootPassword.length < 8 ||
                !isValidResourceLimits(Number(cpuCores), Number(memoryMb)) ||
                !isValidNetworkName(networkName) ||
                (networkAlias !== '' && !isValidNetworkAlias(networkAlias)) ||
                !areValidPortBindings(portBindings) ||
                !areValidVolumeMounts(volumeMounts)
              }
            >
              생성
            </Button>
            {onCancel && (
              <Button type="button" onClick={onCancel}>
                목록으로 돌아가기
              </Button>
            )}
          </div>
        </form>
        <aside className={styles.composePanel} aria-label="Compose-like container options">
          <div className={styles.composeHeader}>
            <div>
              <h3>옵션 YAML</h3>
              <p>오른쪽에서 직접 수정한 뒤 YAML 적용을 누르면 왼쪽 설정에 반영됩니다.</p>
            </div>
          </div>
          {composeError && <ErrorBanner>{composeError}</ErrorBanner>}
          <textarea
            className={styles.composeTextarea}
            spellCheck={false}
            value={composeText}
            onChange={(event) => {
              setComposeText(event.target.value);
              setIsComposeDirty(true);
              setComposeError('');
            }}
          />
          <div className={styles.composeActions}>
            <Button type="button" variant="primary" onClick={applyComposeText}>
              YAML 적용
            </Button>
          </div>
        </aside>
      </div>
    </section>
  );
}

function isValidResourceLimits(cpuCores: number, memoryMb: number) {
  return cpuCores >= 0.1 && cpuCores <= 4 && memoryMb >= 128 && memoryMb <= 4096;
}

function isValidNetworkName(value: string) {
  return value.trim() === '' || /^[a-zA-Z0-9_.-]{1,63}$/.test(value.trim());
}

function isValidNetworkAlias(value: string) {
  return /^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,62}$/.test(value.trim());
}

type ComposeState = {
  displayName: string;
  imageName: string;
  rootPassword: string;
  cpuCores: string;
  memoryMb: string;
  portBindings: DraftPortBinding[];
  volumeMounts: DraftVolumeMount[];
  networkName: string;
  networkAlias: string;
};

function buildComposeText({
  displayName,
  imageName,
  rootPassword,
  cpuCores,
  memoryMb,
  portBindings,
  volumeMounts,
  networkName,
  networkAlias
}: ComposeState) {
  const serviceName = serviceKey(displayName);
  const effectiveDisplayName = displayName.trim() || 'new_container';
  const effectiveNetworkName = networkName.trim() || 'bridge';
  const portLines = portBindings.length
    ? portBindings.map((binding) => `      - "${binding.hostPort || '8080'}:${binding.containerPort || '80'}/${binding.protocol.toLowerCase()}"`)
    : ['      - "8080:80/tcp"'];
  const volumeLines = volumeMounts.length
    ? volumeMounts.map((mount) => `      - "${mount.hostPath || '/host/path'}:${mount.containerPath || '/workspace/app'}:${mount.accessMode === 'READ_ONLY' ? 'ro' : 'rw'}"`)
    : ['      - "/host/path:/workspace/app:rw"'];

  return [
    'services:',
    `  ${serviceName}:`,
    `    image: ${imageName}`,
    `    container_name: ${quoteYaml(effectiveDisplayName)}`,
    `    network_alias: ${quoteYaml(networkAlias.trim())}`,
    '    environment:',
    `      ROOT_PASSWORD: ${quoteYaml(rootPassword)}`,
    '    resources:',
    `      cpus: ${quoteYaml(cpuCores || '0.5')}`,
    `      memory: ${quoteYaml(`${memoryMb || '256'}M`)}`,
    '    ports:',
    ...portLines,
    '    volumes:',
    ...volumeLines,
    '    networks:',
    `      - ${effectiveNetworkName}`,
    '',
    'networks:',
    `  ${effectiveNetworkName}:`,
    '    driver: bridge'
  ].join('\n');
}

function parseComposeText(value: string): ComposeState | null {
  try {
    const displayName = unquoteYaml(matchFirst(value, /^\s*container_name:\s*(.+)$/m) || 'new_container');
    const imageName = unquoteYaml(matchFirst(value, /^\s*image:\s*(.+)$/m) || 'linux-terminal-playground:ubuntu');
    const rootPassword = unquoteYaml(matchFirst(value, /^\s*ROOT_PASSWORD:\s*(.*)$/m) || '');
    const cpuCores = unquoteYaml(matchFirst(value, /^\s*cpus:\s*(.+)$/m) || '0.5');
    const memoryRaw = unquoteYaml(matchFirst(value, /^\s*memory:\s*(.+)$/m) || '256M');
    const memoryMb = parseMemoryMb(memoryRaw);
    const networkAlias = unquoteYaml(matchFirst(value, /^\s*network_alias:\s*(.*)$/m) || '');
    const portBindings = parseListBlock(value, 'ports')
      .map(parsePortLine)
      .filter((binding): binding is DraftPortBinding => binding !== null);
    const volumeMounts = parseListBlock(value, 'volumes')
      .map(parseVolumeLine)
      .filter((mount): mount is DraftVolumeMount => mount !== null);
    const networkName = parseListBlock(value, 'networks')[0]?.trim() || 'bridge';

    if (!displayName.trim() || !isValidResourceLimits(Number(cpuCores), Number(memoryMb))) {
      return null;
    }
    return {
      displayName,
      imageName,
      rootPassword,
      cpuCores,
      memoryMb,
      portBindings,
      volumeMounts,
      networkName,
      networkAlias
    };
  } catch {
    return null;
  }
}

function parseListBlock(value: string, key: string) {
  const lines = value.split('\n');
  const startIndex = lines.findIndex((line) => new RegExp(`^\\s{4}${key}:\\s*$`).test(line));
  if (startIndex < 0) {
    return [];
  }
  const items: string[] = [];
  for (let index = startIndex + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (/^\s{4}[a-zA-Z_][\w-]*:\s*$/.test(line) || /^\S/.test(line)) {
      break;
    }
    const item = line.match(/^\s*-\s*(.+)$/)?.[1];
    if (item) {
      items.push(unquoteYaml(item));
    }
  }
  return items;
}

function parsePortLine(value: string): DraftPortBinding | null {
  const match = value.match(/^(\d+):(\d+)(?:\/(tcp|udp))?$/i);
  if (!match) {
    return null;
  }
  return {
    id: crypto.randomUUID(),
    hostPort: match[1],
    containerPort: match[2],
    protocol: (match[3]?.toUpperCase() || 'TCP') as DraftPortBinding['protocol']
  };
}

function parseVolumeLine(value: string): DraftVolumeMount | null {
  const parts = value.split(':');
  if (parts.length < 2) {
    return null;
  }
  const mode = parts[2]?.toLowerCase() === 'ro' ? 'READ_ONLY' : 'READ_WRITE';
  return {
    id: crypto.randomUUID(),
    hostPath: parts[0],
    containerPath: parts[1],
    accessMode: mode
  };
}

function parseMemoryMb(value: string) {
  const normalized = value.trim().toLowerCase();
  if (normalized.endsWith('g')) {
    return String(Number(normalized.slice(0, -1)) * 1024);
  }
  if (normalized.endsWith('gb')) {
    return String(Number(normalized.slice(0, -2)) * 1024);
  }
  return normalized.replace(/m|mb/g, '') || '256';
}

function matchFirst(value: string, pattern: RegExp) {
  return value.match(pattern)?.[1]?.trim();
}

function serviceKey(displayName: string) {
  const normalized = displayName.trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '-').replace(/^-+|-+$/g, '');
  return normalized || 'app';
}

function quoteYaml(value: string) {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function unquoteYaml(value: string) {
  const trimmed = value.trim();
  if ((trimmed.startsWith('"') && trimmed.endsWith('"')) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
    return trimmed.slice(1, -1).replace(/\\"/g, '"').replace(/\\\\/g, '\\');
  }
  return trimmed;
}
