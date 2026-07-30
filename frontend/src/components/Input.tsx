import type { InputHTMLAttributes, TextareaHTMLAttributes, SelectHTMLAttributes, ReactNode } from 'react';

interface FieldProps {
  label?: string;
  required?: boolean;
  hint?: string;
  error?: string;
  children: ReactNode;
}

export function Field({ label, required, hint, error, children }: FieldProps) {
  return (
    <div className="field">
      {label && (
        <label className="label">
          {label}
          {required && <span className="req">*</span>}
        </label>
      )}
      {children}
      {error ? (
        <span className="error-text">{error}</span>
      ) : hint ? (
        <span className="hint">{hint}</span>
      ) : null}
    </div>
  );
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
  lead?: ReactNode;
}

export function Input({ invalid, lead, className, ...rest }: InputProps) {
  if (lead) {
    return (
      <div className="input-icon">
        <span className="lead">{lead}</span>
        <input className={`input ${invalid ? 'err' : ''} ${className ?? ''}`} {...rest} />
      </div>
    );
  }
  return <input className={`input ${invalid ? 'err' : ''} ${className ?? ''}`} {...rest} />;
}

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
}

export function Textarea({ invalid, className, ...rest }: TextareaProps) {
  return <textarea className={`textarea ${invalid ? 'err' : ''} ${className ?? ''}`} {...rest} />;
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  invalid?: boolean;
}

export function Select({ invalid, className, children, ...rest }: SelectProps) {
  return (
    <select className={`select ${invalid ? 'err' : ''} ${className ?? ''}`} {...rest}>
      {children}
    </select>
  );
}

export default Input;
