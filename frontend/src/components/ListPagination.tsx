import { Group, Pagination, Select } from '@mantine/core';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { useFormat } from '../format/useFormat';
import { DEFAULT_PAGE_SIZE, pageSizeOptions } from './pagination';
import { useNarrowScreen } from './useNarrowScreen';

interface ListPaginationProps {
  /** Zero-based. */
  page: number;
  pageSize: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  defaultPageSize?: number;
  children?: ReactNode;
}

/** A size selector remains available even when all rows fit on one page. */
export function ListPagination({
  page,
  pageSize,
  totalPages,
  onPageChange,
  onPageSizeChange,
  defaultPageSize = DEFAULT_PAGE_SIZE,
  children,
}: ListPaginationProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const narrow = useNarrowScreen();
  return (
    <Group justify="space-between" align="flex-end" gap="sm">
      {children}
      <Group gap="sm" align="flex-end">
        <Select
          label={t('pagination.pageSize')}
          w={150}
          allowDeselect={false}
          data={pageSizeOptions(defaultPageSize).map((size) => ({
            value: String(size),
            label: format.number(size),
          }))}
          value={String(pageSize)}
          onChange={(value) => {
            if (value !== null) onPageSizeChange(Number(value));
          }}
        />
        {totalPages > 1 && (
          <Pagination
            total={totalPages}
            value={page + 1}
            onChange={(value) => onPageChange(value - 1)}
            siblings={narrow ? 0 : 1}
            getControlProps={(control) => ({ 'aria-label': t(`games.list.pages.${control}`) })}
            getItemProps={(value) => ({
              'aria-label': t('games.list.pages.page', { page: value }),
            })}
          />
        )}
      </Group>
    </Group>
  );
}
