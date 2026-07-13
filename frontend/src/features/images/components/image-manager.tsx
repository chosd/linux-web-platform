import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { DockerImage, listImages, pullImage } from '/src/features/images/lib/image-api-client';
import { Button } from '/src/shared/components/button';
import { EmptyState, ErrorBanner, SuccessBanner } from '/src/shared/components/feedback';
import { FormField } from '/src/shared/components/form-field';
import { PanelHeader } from '/src/shared/components/panel-header';

import styles from './image-manager.module.css';

export function ImageManager() {
  const [images, setImages] = useState<DockerImage[]>([]);
  const [query, setQuery] = useState('');
  const [pullName, setPullName] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isPulling, setIsPulling] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const load = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');
    try { setImages(await listImages()); }
    catch (error) { setErrorMessage(error instanceof Error ? error.message : 'Failed to load images.'); }
    finally { setIsLoading(false); }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const filteredImages = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return normalized ? images.filter((image) => image.tags.some((tag) => tag.toLowerCase().includes(normalized))) : images;
  }, [images, query]);

  const handlePull = async (event: FormEvent) => {
    event.preventDefault();
    if (!pullName.trim()) return;
    setIsPulling(true);
    setErrorMessage('');
    setSuccessMessage('');
    try {
      await pullImage(pullName.trim());
      setSuccessMessage(`${pullName.trim()} 이미지를 가져왔습니다.`);
      setPullName('');
      await load();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to pull image.');
    } finally { setIsPulling(false); }
  };

  return (
    <div className={styles.workspace}>
      <form className={styles.pullBar} onSubmit={handlePull}>
        <FormField htmlFor="image-pull-name" label="Registry 이미지">
          <input id="image-pull-name" value={pullName} onChange={(event) => setPullName(event.target.value)} placeholder="ubuntu:24.04" />
        </FormField>
        <Button type="submit" variant="primary" disabled={isPulling || !pullName.trim()}>{isPulling ? 'Pulling...' : 'Pull'}</Button>
      </form>
      {errorMessage && <ErrorBanner>{errorMessage}</ErrorBanner>}
      {successMessage && <SuccessBanner>{successMessage}</SuccessBanner>}
      <section className={styles.panel}>
        <PanelHeader title="로컬 이미지" description={`${images.length}개의 Docker 이미지`} actions={<><input className={styles.search} aria-label="Search images" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="이미지 검색" /><Button type="button" onClick={load} disabled={isLoading}>새로고침</Button></>} />
        {isLoading ? <EmptyState>Loading images...</EmptyState> : filteredImages.length === 0 ? <EmptyState>No images found.</EmptyState> : (
          <div className={styles.tableScroll}><table className={styles.table}><thead><tr><th>Repository / Tag</th><th>Image ID</th><th>크기</th><th>생성일</th></tr></thead><tbody>
            {filteredImages.map((image) => <tr key={image.id}><td><strong>{image.primaryTag}</strong>{image.tags.length > 1 && <small>{image.tags.slice(1).join(', ')}</small>}</td><td><code>{shortId(image.id)}</code></td><td>{formatBytes(image.sizeBytes)}</td><td>{new Date(image.createdAt).toLocaleString()}</td></tr>)}
          </tbody></table></div>
        )}
      </section>
    </div>
  );
}

function shortId(id: string) { return id.replace(/^sha256:/, '').slice(0, 12); }
function formatBytes(value: number) { const mb = value / 1024 / 1024; return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${mb.toFixed(1)} MB`; }
