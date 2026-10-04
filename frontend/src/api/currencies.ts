import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from './client';
import type {
  CurrencySettings,
  CurrencySettingsRequest,
  ExchangeRateStatus,
  ManualRate,
  ManualRateRequest,
} from './types';

/** The base currency: the chosen one (or none, automatic) and the one in use. */
export function useCurrencySettings() {
  return useQuery({
    queryKey: ['currencies', 'settings'],
    queryFn: () => apiFetch<CurrencySettings>('/settings/currency'),
  });
}

/** The rates there are per currency, and how their download went. */
export function useExchangeRateStatus() {
  return useQuery({
    queryKey: ['currencies', 'status'],
    queryFn: () => apiFetch<ExchangeRateStatus>('/exchange-rates/status'),
  });
}

/** The rates typed by hand, newest first. */
export function useManualRates() {
  return useQuery({
    queryKey: ['currencies', 'manual'],
    queryFn: () => apiFetch<ManualRate[]>('/exchange-rates/manual'),
  });
}

/**
 * Mutations on the base currency and the rates. Every converted figure of the app depends on
 * them, so everything loaded is refreshed afterwards.
 */
function useCurrencyMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TInput>({
    mutationFn,
    onSuccess: async () => {
      await queryClient.invalidateQueries();
    },
  });
}

export function useUpdateCurrencySettings() {
  return useCurrencyMutation((settings: CurrencySettingsRequest) =>
    apiFetch<CurrencySettings>('/settings/currency', {
      method: 'PUT',
      body: JSON.stringify(settings),
    }),
  );
}

/** Downloads the missing rates now; the status that comes back says how it went. */
export function useRefreshExchangeRates() {
  return useCurrencyMutation(() =>
    apiFetch<ExchangeRateStatus>('/exchange-rates/refresh', { method: 'POST' }),
  );
}

/** Records the rate of a currency on a day, replacing the one typed before for that day. */
export function useSaveManualRate() {
  return useCurrencyMutation(
    ({ currencyCode, date, rate }: { currencyCode: string; date: string } & ManualRateRequest) =>
      apiFetch<ManualRate>(`/exchange-rates/manual/${currencyCode}/${date}`, {
        method: 'PUT',
        body: JSON.stringify({ rate }),
      }),
  );
}

export function useDeleteManualRate() {
  return useCurrencyMutation(({ currencyCode, date }: { currencyCode: string; date: string }) =>
    apiFetch<void>(`/exchange-rates/manual/${currencyCode}/${date}`, { method: 'DELETE' }),
  );
}
