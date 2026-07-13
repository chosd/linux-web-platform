import { ImageManager } from '/src/features/images/components/image-manager';
import styles from './page-layout.module.css';

export function ImagesPage() {
  return <main className={styles.contentPage}><header className={styles.contentHeader}><div className={styles.titleGroup}><h1>Images</h1><p>로컬 Docker 이미지를 조회하고 Registry에서 이미지를 가져옵니다.</p></div></header><ImageManager /></main>;
}
