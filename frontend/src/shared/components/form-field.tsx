import { ReactNode } from 'react';

import styles from './form-field.module.css';

type FormFieldProps = {
  children: ReactNode;
  className?: string;
  label: string;
  htmlFor: string;
};

export function FormField({ children, className = '', htmlFor, label }: FormFieldProps) {
  return (
    <div className={`${styles.field} ${className}`.trim()}>
      <label htmlFor={htmlFor}>{label}</label>
      {children}
    </div>
  );
}
