import { useQuery } from '@tanstack/react-query';

import { apiFetch } from './client';
import type { Variant } from './types';

/** Every variant, ordered by game type: built-in ones first, then user-defined ones by name. */
export function useVariants() {
  return useQuery({
    queryKey: ['variants'],
    queryFn: () => apiFetch<Variant[]>('/variants'),
  });
}
