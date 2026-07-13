import styles from './status-badge.module.css';

type StatusBadgeProps = {
  label: string;
};

export function StatusBadge({ label }: StatusBadgeProps) {
  return <span className={`${styles.badge} ${styles[statusClassName(label)]}`}>{label}</span>;
}

function statusClassName(label: string) {
  return label.toLowerCase().replace(/_([a-z])/g, (_, character: string) => character.toUpperCase());
}
