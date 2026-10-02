import { ActionIcon, SimpleGrid, Table, Text } from '@mantine/core';
import { IconChevronDown, IconChevronUp } from '@tabler/icons-react';
import { Fragment, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

/** One row of a {@link CompactTable}: what is always in sight and what unfolds under it. */
export interface CompactRow {
  key: string;
  /** First cell: what the row is. */
  label: ReactNode;
  /** The same in plain words, to name the button that unfolds the row. */
  name: string;
  /** The figures that are always shown, one per column after the first. */
  cells: ReactNode[];
  /** The other figures, shown under the row when it is unfolded. */
  details: { label: string; value: ReactNode }[];
}

interface CompactTableProps {
  /** The heading cells (`Table.Th`), as many as a row has cells plus the first one. */
  head: ReactNode;
  rows: CompactRow[];
  /** The totals, shown in bold under the rows. */
  foot?: CompactRow;
}

/**
 * A table of figures for a phone: only the columns that matter most, so it fits without
 * scrolling sideways, and the rest of each row unfolds under it on demand.
 */
export function CompactTable({ head, rows, foot }: CompactTableProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState<ReadonlySet<string>>(new Set());

  function toggle(key: string) {
    setOpen((now) => {
      const next = new Set(now);
      if (!next.delete(key)) {
        next.add(key);
      }
      return next;
    });
  }

  function render(row: CompactRow, total: boolean) {
    const unfolded = open.has(row.key);
    return (
      <Fragment key={row.key}>
        <Table.Tr fw={total ? 700 : undefined}>
          <Table.Th scope="row" fw={total ? 700 : 500}>
            {row.label}
          </Table.Th>
          {row.cells.map((cell, index) => (
            // The columns of a table do not move.
            // oxlint-disable-next-line react/no-array-index-key
            <Table.Td key={index} ta="right" style={{ whiteSpace: 'nowrap' }}>
              {cell}
            </Table.Td>
          ))}
          <Table.Td w={28} p={0}>
            {row.details.length > 0 && (
              <ActionIcon
                variant="subtle"
                color="gray"
                size="sm"
                aria-expanded={unfolded}
                aria-label={t('compactTable.details', { row: row.name })}
                onClick={() => toggle(row.key)}
              >
                {unfolded ? <IconChevronUp size={16} /> : <IconChevronDown size={16} />}
              </ActionIcon>
            )}
          </Table.Td>
        </Table.Tr>
        {unfolded && (
          <Table.Tr>
            <Table.Td colSpan={row.cells.length + 2} bg="var(--mantine-color-default-hover)">
              <SimpleGrid cols={2} spacing="xs" verticalSpacing={4} component="dl" m={0}>
                {row.details.map((detail) => (
                  <div key={detail.label}>
                    <Text size="xs" c="dimmed" component="dt">
                      {detail.label}
                    </Text>
                    <Text size="sm" component="dd" m={0}>
                      {detail.value}
                    </Text>
                  </div>
                ))}
              </SimpleGrid>
            </Table.Td>
          </Table.Tr>
        )}
      </Fragment>
    );
  }

  return (
    // Names written by the user may be one long word: they break instead of widening the page
    // (the figures, which never wrap, are not affected).
    <Table verticalSpacing="xs" horizontalSpacing={4} style={{ overflowWrap: 'anywhere' }}>
      <Table.Thead>
        <Table.Tr>
          {head}
          <Table.Th w={28} p={0} />
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>{rows.map((row) => render(row, false))}</Table.Tbody>
      {foot && <Table.Tfoot>{render(foot, true)}</Table.Tfoot>}
    </Table>
  );
}
