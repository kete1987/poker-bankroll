import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from './client';
import type { GameTemplate, GameTemplateRequest } from './types';

/**
 * Every template, by label (or room name without one); those that cannot start games now (their
 * room or variant is inactive) come too, with `usable: false`.
 */
export function useTemplates() {
  return useQuery({
    queryKey: ['templates'],
    queryFn: () => apiFetch<GameTemplate[]>('/game-templates'),
  });
}

/** Mutations on templates: only the list of templates depends on them. */
function useTemplateMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TInput>({
    mutationFn,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['templates'] });
    },
  });
}

export function useCreateTemplate() {
  return useTemplateMutation((template: GameTemplateRequest) =>
    apiFetch<GameTemplate>('/game-templates', { method: 'POST', body: JSON.stringify(template) }),
  );
}

export function useUpdateTemplate() {
  return useTemplateMutation(({ id, template }: { id: number; template: GameTemplateRequest }) =>
    apiFetch<GameTemplate>(`/game-templates/${id}`, {
      method: 'PUT',
      body: JSON.stringify(template),
    }),
  );
}

export function useDeleteTemplate() {
  return useTemplateMutation((id: number) =>
    apiFetch<void>(`/game-templates/${id}`, { method: 'DELETE' }),
  );
}
