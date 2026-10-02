import { Button, Menu, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconDownload, IconFileSpreadsheet, IconFileTypeCsv } from '@tabler/icons-react';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError, type ApiFile } from '../api/client';
import type { ExportFormat } from '../api/types';
import { saveFile } from './saveFile';

interface ExportMenuProps {
  /** What the button exports, for those who cannot see where it is. */
  label: string;
  /** What the file will hold, shown with the formats: what is left out must be said here. */
  note: string;
  /** Asks the backend for the file with the filters of the list. */
  onExport: (format: ExportFormat) => Promise<ApiFile>;
  /** What to say once the file is saved, given its name. */
  doneMessage: (file: string) => string;
  /** Name of the file when the backend gives none, without extension. */
  fallbackName: string;
}

const FORMATS: readonly ExportFormat[] = ['CSV', 'XLSX'];
const ICONS = { CSV: IconFileTypeCsv, XLSX: IconFileSpreadsheet };

/**
 * The "Export" button of a list: it offers the formats, downloads the file with what the list
 * shows (every page of it) and saves it. One export at a time; a failure is told in a notification.
 */
export function ExportMenu({ label, note, onExport, doneMessage, fallbackName }: ExportMenuProps) {
  const { t } = useTranslation();
  // A ref, not the state: a second click can arrive before the state of the first is rendered.
  const running = useRef(false);
  const [busy, setBusy] = useState(false);

  async function download(format: ExportFormat) {
    if (running.current) {
      return;
    }
    running.current = true;
    setBusy(true);
    try {
      const file = await onExport(format);
      const name = file.name ?? `${fallbackName}.${format.toLowerCase()}`;
      saveFile(file.blob, name);
      notifications.show({ color: 'teal', title: t('export.done'), message: doneMessage(name) });
    } catch (error) {
      notifications.show({
        color: 'red',
        title: t('export.failed'),
        message: error instanceof ApiError ? error.message : t('errors.unexpected'),
        autoClose: false,
      });
    } finally {
      running.current = false;
      setBusy(false);
    }
  }

  return (
    <Menu position="bottom-end" width={280} withinPortal>
      <Menu.Target>
        <Button
          variant="default"
          leftSection={<IconDownload size={16} />}
          loading={busy}
          aria-label={label}
        >
          {t('export.button')}
        </Button>
      </Menu.Target>
      <Menu.Dropdown>
        {FORMATS.map((format) => {
          const Icon = ICONS[format];
          return (
            <Menu.Item
              key={format}
              leftSection={<Icon size={16} />}
              onClick={() => void download(format)}
            >
              {t(format === 'CSV' ? 'export.csv' : 'export.xlsx')}
            </Menu.Item>
          );
        })}
        <Menu.Divider />
        <Text size="xs" c="dimmed" px="sm" py={4}>
          {note}
        </Text>
      </Menu.Dropdown>
    </Menu>
  );
}
