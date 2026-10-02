import {
  Alert,
  Anchor,
  Button,
  Divider,
  FileButton,
  Group,
  List,
  Loader,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { IconDownload, IconFileImport } from '@tabler/icons-react';
import { useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { ApiError } from '../api/client';
import { useCheckImport, useImportGames } from '../api/imports';
import type { GameImport } from '../api/types';
import { BackupSection } from '../backup/BackupSection';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Page } from '../components/Page';
import { StatCard } from '../components/StatCard';
import { useFormat } from '../format/useFormat';

/** The description of the format, for whoever prepares a file. */
const FORMAT_URL = 'https://github.com/kete1987/poker-bankroll/blob/main/docs/import.md';
/** Served by the web app itself (`public/`). */
const EXAMPLE_URL = `${import.meta.env.BASE_URL}import-example.csv`;

/** A file that was checked: what it would import and what is wrong with it. */
interface Checked {
  file: File;
  result: GameImport;
}

/**
 * The Import / Export section: games from a CSV file, and the backup of everything
 * ({@link BackupSection}).
 *
 * Importing games: choosing a file only checks it: the page shows what it holds and its errors,
 * and the import is offered once there are none. It is all or nothing; afterwards the page is
 * ready for another file.
 */
export function ImportPage() {
  const { t } = useTranslation();
  const format = useFormat();
  const resetFile = useRef<() => void>(null);
  const check = useCheckImport();
  const importGames = useImportGames();
  const [checked, setChecked] = useState<Checked | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [imported, setImported] = useState<GameImport | null>(null);
  const [confirming, setConfirming] = useState(false);
  // Only the answer about the file chosen last counts.
  const latest = useRef(0);

  async function choose(file: File) {
    const mine = ++latest.current;
    setChecked(null);
    setFailure(null);
    setImported(null);
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
    const result = await importGames.mutateAsync(checked.file);
    if (result.imported) {
      latest.current++;
      setChecked(null);
      setImported(result);
    } else {
      // Something changed since the file was checked: its errors are shown instead.
      setChecked({ file: checked.file, result });
    }
  }

  const result = checked?.result;
  const count = (value: number) => ({ count: value, formatted: format.number(value) });

  return (
    <Page title={t('nav.import')}>
      <Title order={3}>{t('import.title')}</Title>
      <Stack gap="xs" maw={720}>
        <Text>{t('import.intro')}</Text>
        <Group gap="lg">
          <Anchor href={FORMAT_URL} target="_blank" rel="noreferrer" size="sm">
            {t('import.format')}
          </Anchor>
          <Anchor href={EXAMPLE_URL} download="import-example.csv" size="sm">
            <Group gap={4} component="span" wrap="nowrap">
              <IconDownload size={14} />
              {t('import.example')}
            </Group>
          </Anchor>
        </Group>
      </Stack>

      {imported && (
        <Alert
          color="teal"
          title={t('import.doneTitle')}
          withCloseButton
          closeButtonLabel={t('actions.close')}
          onClose={() => setImported(null)}
        >
          <Group gap="md">
            <Text size="sm">{t('import.done', count(imported.games))}</Text>
            <Anchor component={Link} to="/games" size="sm">
              {t('import.viewGames')}
            </Anchor>
          </Group>
        </Alert>
      )}

      <Group gap="md">
        <FileButton
          resetRef={resetFile}
          accept=".csv,text/csv"
          onChange={(file) => {
            if (file) {
              void choose(file);
            }
            // Otherwise choosing the same file again, once corrected, would do nothing.
            resetFile.current?.();
          }}
        >
          {(props) => (
            <Button
              variant={checked ? 'default' : 'filled'}
              leftSection={<IconFileImport size={16} />}
              disabled={importGames.isPending}
              {...props}
            >
              {checked ? t('import.chooseAnother') : t('import.choose')}
            </Button>
          )}
        </FileButton>
        {checked && <Text fw={500}>{checked.file.name}</Text>}
        {check.isPending && (
          <Group gap="xs">
            <Loader size="sm" />
            <Text size="sm" c="dimmed">
              {t('import.checking')}
            </Text>
          </Group>
        )}
      </Group>

      {failure && (
        <Alert color="red" title={t('import.unreadable')}>
          {failure}
        </Alert>
      )}

      {checked && result && (
        <Stack gap="md">
          <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }}>
            <StatCard label={t('import.games')} value={format.number(result.games)}>
              {result.gamesByType.map((ofType) => (
                <div key={ofType.gameType}>
                  {t(`gameTypes.${ofType.gameType}`)}: {format.number(ofType.games)}
                </div>
              ))}
              {result.from && result.to && (
                <div>
                  {t('import.dates', {
                    from: format.date(result.from),
                    to: format.date(result.to),
                  })}
                </div>
              )}
              <div>{t('import.rows', count(result.rows))}</div>
            </StatCard>
            {result.totals.map((total) => (
              <StatCard
                key={total.currencyCode}
                label={t('import.net', { currency: total.currencyCode })}
                value={format.signedMoney(total.net, total.currencyCode)}
                tone={total.net > 0 ? 'positive' : total.net < 0 ? 'negative' : undefined}
              >
                {t('import.netGames', count(total.games))}
              </StatCard>
            ))}
          </SimpleGrid>

          {result.newRooms.length > 0 && (
            <Created title={t('import.newRooms')}>
              {result.newRooms.map((room) => (
                <List.Item key={room.name}>
                  {room.name} ({room.currencyCode})
                </List.Item>
              ))}
            </Created>
          )}
          {result.newVariants.length > 0 && (
            <Created title={t('import.newVariants')}>
              {result.newVariants.map((variant) => (
                <List.Item key={`${variant.gameType}/${variant.name}`}>
                  {variant.name} ({t(`gameTypes.${variant.gameType}`)})
                </List.Item>
              ))}
            </Created>
          )}

          {result.errorCount > 0 ? (
            <Alert color="red" title={t('import.errors.title', count(result.errorCount))}>
              <Stack gap="sm">
                <Text size="sm">{t('import.errors.help')}</Text>
                <Table.ScrollContainer minWidth={480}>
                  <Table verticalSpacing={4} aria-label={t('import.errors.error')}>
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th w={70}>{t('import.errors.row')}</Table.Th>
                        <Table.Th w={160}>{t('import.errors.column')}</Table.Th>
                        <Table.Th>{t('import.errors.error')}</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {result.errors.map((error, index) => (
                        // Nothing identifies an error: a row can have several, also in a column.
                        // oxlint-disable-next-line react/no-array-index-key
                        <Table.Tr key={index}>
                          <Table.Td>{error.row}</Table.Td>
                          <Table.Td>{error.field ?? '—'}</Table.Td>
                          <Table.Td>{error.message}</Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Table.ScrollContainer>
                {result.errorCount > result.errors.length && (
                  <Text size="sm">
                    {t('import.errors.more', count(result.errorCount - result.errors.length))}
                  </Text>
                )}
              </Stack>
            </Alert>
          ) : (
            <Text size="sm" c="dimmed" maw={720}>
              {t('import.duplicates')}
            </Text>
          )}

          <Group>
            <Button
              disabled={result.errorCount > 0 || result.games === 0}
              loading={importGames.isPending}
              onClick={() => setConfirming(true)}
            >
              {t('import.submit', count(result.games))}
            </Button>
          </Group>
        </Stack>
      )}

      {confirming && checked && result && (
        <ConfirmDialog
          title={t('import.confirm.title')}
          confirmLabel={t('import.confirm.label')}
          onConfirm={confirm}
          onClose={() => setConfirming(false)}
        >
          <Stack gap="xs">
            <Text size="sm">
              {t('import.confirm.text', { ...count(result.games), file: checked.file.name })}
            </Text>
            <Text size="sm">{t('import.duplicates')}</Text>
          </Stack>
        </ConfirmDialog>
      )}

      <Divider my="sm" />
      <Title order={3}>{t('backup.title')}</Title>
      <BackupSection />
    </Page>
  );
}

/** What the import creates besides games. */
function Created({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Stack gap={4}>
      <Text size="sm" fw={600}>
        {title}
      </Text>
      <List size="sm">{children}</List>
    </Stack>
  );
}
