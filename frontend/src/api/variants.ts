import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from './client';
import type { Variant, VariantCreateRequest, VariantUpdateRequest } from './types';

/** Every variant, ordered by game type: built-in ones first, then user-defined ones by name. */
export function useVariants() {
  return useQuery({
    queryKey: ['variants'],
    queryFn: () => apiFetch<Variant[]>('/variants'),
  });
}

/**
 * Mutations on variants. A variant is named in games and statistics, so everything loaded is
 * refreshed afterwards, not only the list of variants.
 */
function useVariantMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TInput>({
    mutationFn,
    onSuccess: async () => {
      await queryClient.invalidateQueries();
    },
  });
}

export function useCreateVariant() {
  return useVariantMutation((variant: VariantCreateRequest) =>
    apiFetch<Variant>('/variants', { method: 'POST', body: JSON.stringify(variant) }),
  );
}

export function useUpdateVariant() {
  return useVariantMutation(({ id, variant }: { id: number; variant: VariantUpdateRequest }) =>
    apiFetch<Variant>(`/variants/${id}`, { method: 'PUT', body: JSON.stringify(variant) }),
  );
}

export function useDeleteVariant() {
  return useVariantMutation((id: number) =>
    apiFetch<void>(`/variants/${id}`, { method: 'DELETE' }),
  );
}
