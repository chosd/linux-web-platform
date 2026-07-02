import { HTMLAttributes, ReactNode } from 'react';

import styles from './feedback.module.css';

type FeedbackProps = HTMLAttributes<HTMLDivElement> & {
  children: ReactNode;
};

export function ErrorBanner({ className = '', ...props }: FeedbackProps) {
  return <div className={`${styles.errorBanner} ${className}`.trim()} {...props} />;
}

export function SuccessBanner({ className = '', ...props }: FeedbackProps) {
  return <div className={`${styles.successBanner} ${className}`.trim()} {...props} />;
}

export function EmptyState({ className = '', ...props }: FeedbackProps) {
  return <div className={`${styles.emptyState} ${className}`.trim()} {...props} />;
}
