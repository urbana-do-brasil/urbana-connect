import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MessageComposer } from './MessageComposer';

describe('MessageComposer', () => {
  it('sends with Enter and preserves a newline with Shift+Enter', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();
    render(<MessageComposer onSend={onSend} disabled={false} />);
    const input = screen.getByRole('textbox', { name: /mensagem/i });

    await user.type(input, 'primeira linha');
    await user.keyboard('{Shift>}{Enter}{/Shift}');
    await user.type(input, 'segunda linha');
    expect(onSend).not.toHaveBeenCalled();
    expect(input).toHaveValue('primeira linha\nsegunda linha');

    await user.click(input);
    await user.keyboard('{Enter}');
    expect(onSend).toHaveBeenCalledWith('primeira linha\nsegunda linha');
  });

  it('rejects empty input and text above the contract limit', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();
    render(<MessageComposer onSend={onSend} disabled={false} />);
    const input = screen.getByRole('textbox', { name: /mensagem/i });

    await user.click(input);
    await user.keyboard('{Enter}');
    expect(screen.getByRole('alert')).toHaveTextContent(/escreva uma mensagem/i);
    expect(onSend).not.toHaveBeenCalled();

    await user.click(input);
    await user.paste('x'.repeat(8001));
    expect(screen.getByRole('alert')).toHaveTextContent(/8000/i);
  });
});
