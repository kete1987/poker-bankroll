import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from './client';
import type { Tag, TagRequest } from './types';

/** Tags of one game at most, as the backend takes them. */
export const MAX_TAGS = 10;
/** Characters of a tag at most. */
export const MAX_TAG_LENGTH = 40;

/** Every tag with its number of games, by name. */
export function useTags() {
  return useQuery({
    queryKey: ['tags'],
    queryFn: () => apiFetch<Tag[]>('/tags'),
  });
}

/**
 * Mutations on tags. A tag is shown on games and groups statistics, so everything loaded is
 * refreshed afterwards, not only the list of tags.
 */
function useTagMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TInput>({
    mutationFn,
    onSuccess: async () => {
      await queryClient.invalidateQueries();
    },
  });
}

/** Renames a tag; when another one has the name, it is merged into that one (which comes back). */
export function useRenameTag() {
  return useTagMutation(({ id, tag }: { id: number; tag: TagRequest }) =>
    apiFetch<Tag>(`/tags/${id}`, { method: 'PUT', body: JSON.stringify(tag) }),
  );
}

/** Deletes a tag: its games lose it. */
export function useDeleteTag() {
  return useTagMutation((id: number) => apiFetch<void>(`/tags/${id}`, { method: 'DELETE' }));
}
