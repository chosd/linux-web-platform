import { ButtonHTMLAttributes } from 'react';

import styles from './button.module.css';

type ButtonVariant = 'primary' | 'secondary';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
};

export function Button({ className = '', variant = 'secondary', ...props }: ButtonProps) {
  const variantClassName = variant === 'primary' ? `${styles.button} ${styles.primary}` : styles.button;
  return <button className={`${variantClassName} ${className}`.trim()} {...props} />;
}
