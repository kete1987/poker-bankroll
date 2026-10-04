import {
  Alert,
  Anchor,
  Button,
  Checkbox,
  FileButton,
  Group,
  Loader,
  Stack,
  Table,
  Text,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconDatabaseExport, IconDatabaseImport } from '@tabler/icons-react';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { downloadBackup, useCheckBackup, useRestoreBackup } from '../api/backup';
import { ApiError } from '../api/client';
import type { BackupContents, BackupRestore } from '../api/types';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { saveFile } from '../components/saveFile';
import { NO_VALUE } from '../format/format';
import { useFormat } from '../format/useFormat';
import { formatVersion } from '../version';

/** When to use this and when the automatic backups of the stack. */
const DOCS_URL = 'https://github.com/kete1987/poker-bankroll/blob/main/docs/backups.md';

/** A file that was checked: what it holds, what would be lost and what is wrong with it. */
interface Checked {
  file: File;
  result: BackupRestore;
}

/**
 * The backup of everything: downloading it, and restoring one. Choosing a file only checks it:
 * the section shows what it holds and, when the installation has data, what restoring deletes.
 * The restore is offered once the file has no errors and must be confirmed; when data is deleted,
 * with a box to tick first. It is all or nothing; afterwards the section is left without file.
 */
export function BackupSection() {
  const { t } = useTranslation();
  const format = useFormat();
  const resetFile = useRef<() => void>(null);
  const check = useCheckBackup();
  const restore = useRestoreBackup();
  const [downloading, setDownloading] = useState(false);
  const [checked, setChecked] = useState<Checked | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [restored, setRestored] = useState<Checked | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [understood, setUnderstood] = useState(false);
  // A ref, not the state: a second click can arrive before the state of the first is rendered.
  const downloadRunning = useRef(false);
  // Only the answer about the file chosen last counts.
  const latest = useRef(0);

  async function download() {
    if (downloadRunning.current) {
      return;
    }
    downloadRunning.current = true;
    setDownloading(true);
    try {
      const file = await downloadBackup();
      const name = file.name ?? 'poker-bankroll-backup.json';
      saveFile(file.blob, name);
      notifications.show({
        color: 'teal',
        title: t('backup.downloaded'),
        message: t('backup.downloadedText', { file: name }),
      });
    } catch (error) {
      notifications.show({
        color: 'red',
        title: t('backup.downloadFailed'),
        message: error instanceof ApiError ? error.message : t('errors.unexpected'),
        autoClose: false,
      });
    } finally {
      downloadRunning.current = false;
      setDownloading(false);
    }
  }

  async function choose(file: File) {
    const mine = ++latest.current;
    setChecked(null);
    setFailure(null);
    setRestored(null);
    try {
      const result = await check.mutateAsync(file);
      if (mine === latest.current) {
        setChecked({ file, result });
      }
    } catch (error) {
      if (mine === latest.current) {
        setFailure(error instanceof ApiError ? error.message : t('errors.unexpected'));
      }
    }
  }

  async function confirm() {
    if (!checked) {
      return;
    }
    // Only what the user saw and accepted is deleted: an installation that was empty when the
    // file was checked and has data now is not replaced (the backend refuses).
    const result = await restore.mutateAsync({
      file: checked.file,
      replace: !checked.result.current.empty,
    });
    if (result.restored) {
      latest.current++;
      setChecked(null);
      setRestored({ file: checked.file, result });
    } else {
      // Something changed since the file was checked: its errors are shown instead.
      setChecked({ file: checked.file, result });
    }
  }

  function closeConfirmation() {
    setConfirming(false);
    setUnderstood(false);
  }

  const count = (value: number) => ({ count: value, formatted: format.number(value) });
  /** What a backup or the installation holds, in words: "4 rooms, 412 games…". */
  const inWords = (contents: BackupContents) =>
    [
      t('backup.counts.rooms', count(contents.rooms)),
      t('backup.counts.variants', count(contents.variants)),
      t('backup.counts.games', count(contents.games)),
      t('backup.counts.movements', count(contents.movements)),
    ].join(', ');

  const result = checked?.result;
  const replaces = result ? !result.current.empty : false;

  return (
    <Stack gap="md">
      <Stack gap="xs" maw={720}>
        <Text>{t('backup.intro')}</Text>
        <Group gap="lg">
          <Anchor href={DOCS_URL} target="_blank" rel="noreferrer" size="sm">
            {t('backup.docs')}
          </Anchor>
        </Group>
      </Stack>

      <Group>
        <Button
          variant="default"
          leftSection={<IconDatabaseExport size={16} />}
          loading={downloading}
          onClick={() => void download()}
        >
          {t('backup.download')}
        </Button>
      </Group>

      <Text maw={720}>{t('backup.restoreIntro')}</Text>

      {restored && (
        <Alert
          color="teal"
          title={t('backup.doneTitle')}
          withCloseButton
          closeButtonLabel={t('actions.close')}
          onClose={() => setRestored(null)}
        >
          <Group gap="md">
            <Text size="sm">
              {t('backup.done', {
                file: restored.file.name,
                contents: inWords(restored.result.file),
              })}
            </Text>
            <Anchor component={Link} to="/games" size="sm">
              {t('import.viewGames')}
            </Anchor>
          </Group>
        </Alert>
      )}

      <Group gap="md">
        <FileButton
          resetRef={resetFile}
          accept=".json,application/json"
          onChange={(file) => {
            if (file) {
              void choose(file);
            }
            // Otherwise choosing the same file again would do nothing.
            resetFile.current?.();
          }}
        >
          {(props) => (
            <Button
              variant="default"
              leftSection={<IconDatabaseImport size={16} />}
              disabled={restore.isPending}
              {...props}
            >
              {checked ? t('backup.chooseAnother') : t('backup.choose')}
            </Button>
          )}
        </FileButton>
        {checked && <Text fw={500}>{checked.file.name}</Text>}
        {check.isPending && (
          <Group gap="xs">
            <Loader size="sm" />
            <Text size="sm" c="dimmed">
              {t('backup.checking')}
            </Text>
          </Group>
        )}
      </Group>

      {failure && (
        <Alert color="red" title={t('backup.unreadable')}>
          {failure}
        </Alert>
      )}

      {checked && result && (
        <Stack gap="md">
          {result.exportedAt && result.appVersion && (
            <Text size="sm" c="dimmed">
              {t('backup.madeOn', {
                date: format.date(result.exportedAt.slice(0, 10)),
                version: formatVersion(result.appVersion),
              })}
            </Text>
          )}

          <Table.ScrollContainer minWidth={420} maw={720}>
            <Table verticalSpacing={4} aria-label={t('backup.contents.label')}>
              <Table.Thead>
                <Table.Tr>
                  <Table.Td />
                  <Table.Th ta="right">{t('backup.contents.file')}</Table.Th>
                  <Table.Th ta="right">{t('backup.contents.current')}</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(
                  ['rooms', 'variants', 'games', 'gamesInPlay', 'movements', 'templates'] as const
                ).map((what) => (
                  <Table.Tr key={what}>
                    <Table.Th scope="row" fw={400}>
                      {t(`backup.contents.${what}`)}
                    </Table.Th>
                    <Table.Td ta="right">{format.number(result.file[what])}</Table.Td>
                    <Table.Td ta="right">{format.number(result.current[what])}</Table.Td>
                  </Table.Tr>
                ))}
                <Table.Tr>
                  <Table.Th scope="row" fw={400}>
                    {t('backup.contents.dates')}
                  </Table.Th>
                  <Table.Td ta="right">{dates(result.file)}</Table.Td>
                  <Table.Td ta="right">{dates(result.current)}</Table.Td>
                </Table.Tr>
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>

          {result.errorCount > 0 && (
            <Alert color="red" title={t('backup.errors.title', count(result.errorCount))}>
              <Stack gap="sm">
                <Text size="sm">{t('backup.errors.help')}</Text>
                <Table.ScrollContainer minWidth={480}>
                  <Table verticalSpacing={4} aria-label={t('backup.errors.error')}>
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th w={220}>{t('backup.errors.where')}</Table.Th>
                        <Table.Th>{t('backup.errors.error')}</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {result.errors.map((error, index) => (
                        // Nothing identifies an error: a value can have several.
                        // oxlint-disable-next-line react/no-array-index-key
                        <Table.Tr key={index}>
                          <Table.Td>{error.path}</Table.Td>
                          <Table.Td>{error.message}</Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Table.ScrollContainer>
                {result.errorCount > result.errors.length && (
                  <Text size="sm">
                    {t('backup.errors.more', count(result.errorCount - result.errors.length))}
                  </Text>
                )}
              </Stack>
            </Alert>
          )}

          {replaces ? (
            <Alert
              color="red"
              variant="filled"
              icon={<IconAlertTriangle />}
              title={t('backup.replace.title')}
              maw={720}
            >
              {t('backup.replace.text', { contents: inWords(result.current) })}
            </Alert>
          ) : (
            <Text size="sm" c="dimmed" maw={720}>
              {t('backup.emptyInstallation')}
            </Text>
          )}

          <Group>
            <Button
              color={replaces ? 'red' : undefined}
              disabled={result.errorCount > 0}
              loading={restore.isPending}
              onClick={() => setConfirming(true)}
            >
              {t('backup.submit')}
            </Button>
          </Group>
        </Stack>
      )}

      {confirming && checked && result && (
        <ConfirmDialog
          title={t('backup.confirm.title')}
          confirmLabel={replaces ? t('backup.confirm.labelReplace') : t('backup.confirm.label')}
          destructive={replaces}
          confirmDisabled={replaces && !understood}
          onConfirm={confirm}
          onClose={closeConfirmation}
        >
          <Stack gap="sm">
            <Text size="sm">
              {t('backup.confirm.text', {
                file: checked.file.name,
                contents: inWords(result.file),
              })}
            </Text>
            {replaces ? (
              <>
                <Text size="sm" fw={600} c="red">
                  {t('backup.confirm.deleted', { contents: inWords(result.current) })}
                </Text>
                <Checkbox
                  color="red"
                  checked={understood}
                  onChange={(event) => setUnderstood(event.currentTarget.checked)}
                  label={t('backup.confirm.understand')}
                />
              </>
            ) : (
              <Text size="sm">{t('backup.emptyInstallation')}</Text>
            )}
          </Stack>
        </ConfirmDialog>
      )}
    </Stack>
  );

  function dates(contents: BackupContents) {
    return contents.from && contents.to
      ? t('import.dates', { from: format.date(contents.from), to: format.date(contents.to) })
      : NO_VALUE;
  }
}
