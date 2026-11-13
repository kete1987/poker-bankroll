import { useCallback, useEffect } from 'react';
import { useSearchParams } from 'react-router';

import { DEFAULT_PAGE_SIZE, parsePageSize } from './pagination';
import { parsePositiveInteger } from './urlParams';

/** Local paging for reference lists; their complete data remains available to other controls. */
export function useListPagination(totalItems: number) {
  const [params, setParams] = useSearchParams();
  const pageSize = parsePageSize(params.get('size'));
  const requestedPage = (parsePositiveInteger(params.get('page')) ?? 1) - 1;
  const totalPages = Math.ceil(totalItems / pageSize);
  const page = Math.min(requestedPage, Math.max(totalPages - 1, 0));

  const onPageChange = useCallback(
    (chosen: number) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          if (chosen === 0) next.delete('page');
          else next.set('page', String(chosen + 1));
          return next;
        },
        { replace: true },
      );
    },
    [setParams],
  );
  const onPageSizeChange = useCallback(
    (chosen: number) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          next.delete('page');
          if (chosen === DEFAULT_PAGE_SIZE) next.delete('size');
          else next.set('size', String(chosen));
          return next;
        },
        { replace: true },
      );
    },
    [setParams],
  );
  useEffect(() => {
    if (page !== requestedPage) onPageChange(page);
  }, [page, requestedPage, onPageChange]);

  return { page, pageSize, totalPages, onPageChange, onPageSizeChange };
}
