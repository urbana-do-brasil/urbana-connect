import type { ReactNode } from 'react';
import type { CanonicalMessage } from '../api/contracts';

interface MessageBubbleProps {
  message: CanonicalMessage;
}

export function MessageBubble({ message }: MessageBubbleProps) {
  const received = message.direction === 'OUTBOUND';
  return (
    <article className={`message-bubble ${received ? 'message-bubble--received' : 'message-bubble--sent'}`}>
      <div className="message-bubble__content">{renderSafeText(message.text ?? '')}</div>
      <time dateTime={message.createdAt} className="message-bubble__time">
        {formatTime(message.createdAt)}
      </time>
    </article>
  );
}

function renderSafeText(text: string): ReactNode {
  return text.split('\n').map((line, lineIndex) => (
    <span key={`line-${lineIndex}`}>
      {lineIndex > 0 ? <br /> : null}
      {renderLine(line, lineIndex)}
    </span>
  ));
}

function renderLine(line: string, lineIndex: number): ReactNode[] {
  const urlPattern = /https?:\/\/[^\s<>"']+/gi;
  const parts: ReactNode[] = [];
  let cursor = 0;
  let match = urlPattern.exec(line);
  let partIndex = 0;
  while (match !== null) {
    const rawUrl = match[0];
    const safeUrl = safeHttpUrl(rawUrl);
    if (match.index > cursor) {
      parts.push(line.slice(cursor, match.index));
    }
    if (safeUrl === null) {
      parts.push(rawUrl);
    } else {
      parts.push(
        <a
          key={`link-${lineIndex}-${partIndex}`}
          href={safeUrl}
          target="_blank"
          rel="noreferrer noopener"
        >
          {rawUrl}
        </a>,
      );
    }
    cursor = match.index + rawUrl.length;
    partIndex += 1;
    match = urlPattern.exec(line);
  }
  if (cursor < line.length) {
    parts.push(line.slice(cursor));
  }
  return parts;
}

function safeHttpUrl(value: string): string | null {
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.toString() : null;
  } catch {
    return null;
  }
}

function formatTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? ''
    : new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' }).format(date);
}
