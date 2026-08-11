import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { MAX_TEXT_LENGTH } from '../api/contracts';

interface MessageComposerProps {
  onSend: (text: string) => void;
  disabled: boolean;
}

export function MessageComposer({ onSend, disabled }: MessageComposerProps) {
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);

  function submit(event?: FormEvent<HTMLFormElement>): void {
    event?.preventDefault();
    const trimmed = text.trim();
    if (trimmed.length === 0) {
      setError('Escreva uma mensagem antes de enviar.');
      return;
    }
    if (text.length > MAX_TEXT_LENGTH) {
      setError(`A mensagem deve ter no máximo ${MAX_TEXT_LENGTH} caracteres.`);
      return;
    }
    setError(null);
    onSend(text);
    setText('');
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <form className="message-composer" onSubmit={submit}>
      <label htmlFor="message-input" className="sr-only">Mensagem</label>
      <textarea
        id="message-input"
        aria-label="Mensagem"
        value={text}
        onChange={(event) => {
          const nextText = event.target.value;
          setText(nextText);
          if (nextText.length > MAX_TEXT_LENGTH) {
            setError(`A mensagem deve ter no máximo ${MAX_TEXT_LENGTH} caracteres.`);
          } else if (error !== null) {
            setError(null);
          }
        }}
        onKeyDown={handleKeyDown}
        placeholder="Escreva uma mensagem"
        rows={2}
        disabled={disabled}
        aria-invalid={error !== null}
      />
      <div className="message-composer__footer">
        <span className="message-composer__hint">Enter envia · Shift+Enter quebra a linha</span>
        <button type="submit" disabled={disabled}>Enviar</button>
      </div>
      {error !== null ? <p role="alert" className="form-error">{error}</p> : null}
    </form>
  );
}
