import { ButtonHTMLAttributes } from 'react';

type ButtonVariant = 'primary' | 'secondary';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
};

export function Button({ className = '', variant = 'secondary', ...props }: ButtonProps) {
  const variantClassName = variant === 'primary' ? 'button button-primary' : 'button';
  return <button className={`${variantClassName} ${className}`.trim()} {...props} />;
}
