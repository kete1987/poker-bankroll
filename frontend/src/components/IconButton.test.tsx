import { MantineProvider, Menu } from '@mantine/core';
import { IconDotsVertical, IconTrash } from '@tabler/icons-react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { describe, expect, it, onTestFinished, vi } from 'vitest';

import { IconButton } from './IconButton';

function renderInMantine(children: ReactNode) {
  return render(<MantineProvider>{children}</MantineProvider>);
}

describe('IconButton', () => {
  it('is named by its label, which shows in a tooltip on hover', async () => {
    const onClick = vi.fn();
    renderInMantine(
      <IconButton label="Delete Sunday Million" color="red" onClick={onClick}>
        <IconTrash />
      </IconButton>,
    );

    const button = screen.getByRole('button', { name: 'Delete Sunday Million' });
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();

    await userEvent.hover(button);
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Delete Sunday Million');
    // The tooltip describes the button: its name is still the label alone.
    expect(button).toHaveAccessibleName('Delete Sunday Million');

    await userEvent.unhover(button);
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument());

    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('shows its tooltip when reached with the keyboard', async () => {
    renderInMantine(
      <IconButton label="Switch between light and dark mode">
        <IconTrash />
      </IconButton>,
    );

    await userEvent.tab();
    expect(
      screen.getByRole('button', { name: 'Switch between light and dark mode' }),
    ).toHaveFocus();
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'Switch between light and dark mode',
    );
  });

  it('leaves no tooltip after a tap where the pointer cannot hover', async () => {
    const original = window.matchMedia;
    window.matchMedia = (query: string) => ({
      ...original(query),
      matches: query === '(hover: none)',
    });
    onTestFinished(() => {
      window.matchMedia = original;
    });
    const onClick = vi.fn();
    renderInMantine(
      <IconButton label="Details of Winamax" onClick={onClick}>
        <IconTrash />
      </IconButton>,
    );

    await userEvent.pointer({
      keys: '[TouchA]',
      target: screen.getByRole('button', { name: 'Details of Winamax' }),
    });
    expect(onClick).toHaveBeenCalledOnce();
    // Longer than the delay of a tooltip that would open.
    await new Promise((resolve) => setTimeout(resolve, 600));
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });

  it('can show something else in the tooltip, keeping the label as its name', async () => {
    renderInMantine(
      <IconButton
        label="Delete Winamax"
        tooltip="It has games: deactivate it instead."
        aria-disabled
      >
        <IconTrash />
      </IconButton>,
    );

    await userEvent.hover(screen.getByRole('button', { name: 'Delete Winamax' }));
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'It has games: deactivate it instead.',
    );
  });

  it('opens a menu as its target', async () => {
    renderInMantine(
      <Menu>
        <Menu.Target>
          <IconButton label="Actions for Sunday Million">
            <IconDotsVertical />
          </IconButton>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Item>Edit</Menu.Item>
        </Menu.Dropdown>
      </Menu>,
    );

    const button = screen.getByRole('button', { name: 'Actions for Sunday Million' });
    expect(button).toHaveAttribute('aria-haspopup', 'menu');
    await userEvent.click(button);
    expect(await screen.findByRole('menuitem', { name: 'Edit', hidden: true })).toBeInTheDocument();
    expect(button).toHaveAttribute('aria-expanded', 'true');
  });
});
