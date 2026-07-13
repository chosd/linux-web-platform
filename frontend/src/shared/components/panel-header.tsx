import { ReactNode } from 'react';

import styles from './panel-header.module.css';

type PanelHeaderProps = {
  actions?: ReactNode;
  className?: string;
  description?: ReactNode;
  title: ReactNode;
};

export function PanelHeader({ actions, className = '', description, title }: PanelHeaderProps) {
  return (
    <header className={`${styles.header} ${className}`.trim()}>
      <div>
        <h2 className={styles.title}>{title}</h2>
        {description && <p className={styles.description}>{description}</p>}
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </header>
  );
}
