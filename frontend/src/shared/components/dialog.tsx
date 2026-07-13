import { FormEvent, ReactNode, useEffect, useRef, useState } from 'react';

type DialogProps = {
  children: ReactNode;
  description?: string;
  isOpen: boolean;
  onClose: () => void;
  title: string;
};

export function Dialog({ children, description, isOpen, onClose, title }: DialogProps) {
  const closeButtonRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    closeButtonRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-[clamp(1rem,2vw,2.5rem)]" role="presentation">
      <button
        aria-label="닫기"
        className="absolute inset-0 cursor-default bg-slate-950/60 backdrop-blur-[2px]"
        onClick={onClose}
        type="button"
      />
      <section
        aria-describedby={description ? 'dialog-description' : undefined}
        aria-modal="true"
        aria-labelledby="dialog-title"
        className="relative z-10 grid w-[clamp(26rem,32vw,36rem)] max-w-none gap-[clamp(1rem,1.4vw,1.5rem)] rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-[clamp(1rem,1.5vw,1.75rem)] shadow-2xl"
        role="dialog"
      >
        <header className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h2 className="text-base font-semibold text-[var(--color-text-strong)]" id="dialog-title">{title}</h2>
            {description && <p className="mt-1 text-sm text-[var(--color-text-muted)]" id="dialog-description">{description}</p>}
          </div>
          <button
            aria-label="닫기"
            className="grid h-8 w-8 shrink-0 place-items-center rounded-lg border border-[var(--color-border)] bg-[var(--color-surface-strong)] text-lg text-[var(--color-text-muted)] hover:text-[var(--color-text-strong)]"
            onClick={onClose}
            ref={closeButtonRef}
            type="button"
          >
            ×
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}

type TextInputDialogProps = {
  confirmLabel: string;
  description?: string;
  initialValue?: string;
  isOpen: boolean;
  isSubmitting?: boolean;
  label: string;
  onClose: () => void;
  onConfirm: (value: string) => void;
  placeholder?: string;
  title: string;
};

export function TextInputDialog({
  confirmLabel,
  description,
  initialValue = '',
  isOpen,
  isSubmitting = false,
  label,
  onClose,
  onConfirm,
  placeholder,
  title
}: TextInputDialogProps) {
  const [value, setValue] = useState(initialValue);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    setValue(initialValue);
    requestAnimationFrame(() => inputRef.current?.focus());
  }, [initialValue, isOpen]);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (value.trim()) onConfirm(value.trim());
  };

  return (
    <Dialog description={description} isOpen={isOpen} onClose={onClose} title={title}>
      <form className="grid gap-4" onSubmit={submit}>
        <label className="grid gap-2 text-sm font-medium text-[var(--color-text-strong)]">
          {label}
          <input
            className="min-h-10 rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-input-bg)] px-3 text-sm text-[var(--color-text-strong)] outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-border)]"
            onChange={(event) => setValue(event.target.value)}
            placeholder={placeholder}
            ref={inputRef}
            value={value}
          />
        </label>
        <DialogActions confirmLabel={confirmLabel} disabled={isSubmitting || !value.trim()} onClose={onClose} />
      </form>
    </Dialog>
  );
}

type ConfirmDialogProps = {
  confirmLabel: string;
  description: string;
  isOpen: boolean;
  isSubmitting?: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  tone?: 'danger' | 'primary';
};

export function ConfirmDialog({
  confirmLabel,
  description,
  isOpen,
  isSubmitting = false,
  onClose,
  onConfirm,
  title,
  tone = 'primary'
}: ConfirmDialogProps) {
  return (
    <Dialog description={description} isOpen={isOpen} onClose={onClose} title={title}>
      <DialogActions
        confirmLabel={confirmLabel}
        disabled={isSubmitting}
        onClose={onClose}
        onConfirm={onConfirm}
        tone={tone}
      />
    </Dialog>
  );
}

type DialogActionsProps = {
  confirmLabel: string;
  disabled?: boolean;
  onClose: () => void;
  onConfirm?: () => void;
  tone?: 'danger' | 'primary';
};

function DialogActions({ confirmLabel, disabled = false, onClose, onConfirm, tone = 'primary' }: DialogActionsProps) {
  const confirmClassName = tone === 'danger'
    ? 'bg-rose-600 hover:bg-rose-500'
    : 'bg-[var(--color-primary)] hover:bg-[var(--color-primary-hover)]';
  return (
    <div className="flex justify-end gap-2">
      <button
        className="min-h-9 rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-surface-strong)] px-3 text-sm font-medium text-[var(--color-text-strong)] hover:bg-[var(--color-surface-hover)]"
        onClick={onClose}
        type="button"
      >
        취소
      </button>
      <button
        className={`min-h-9 rounded-xl px-3 text-sm font-semibold text-white disabled:cursor-wait disabled:opacity-50 ${confirmClassName}`}
        disabled={disabled}
        onClick={onConfirm}
        type={onConfirm ? 'button' : 'submit'}
      >
        {confirmLabel}
      </button>
    </div>
  );
}
