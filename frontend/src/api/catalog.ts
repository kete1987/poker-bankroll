import { useQuery } from '@tanstack/react-query';

import { apiFetch } from './client';
import type { Catalog } from './types';

/** Reference data of the forms: currencies, game types and modalities. It hardly ever changes. */
export function useCatalog() {
  return useQuery({
    queryKey: ['catalog'],
    queryFn: () => apiFetch<Catalog>('/catalog'),
    staleTime: Infinity,
  });
}
