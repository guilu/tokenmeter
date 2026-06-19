import type { PricingModelResponse } from '../types/api'

export type PriceSortColumn = 'input' | 'output'
export type SortDirection = 'asc' | 'desc'

/**
 * Returns a new array of models sorted by the chosen per-million price column (TKM-67). Stable and
 * non-mutating — the original array is left untouched.
 */
export function sortModels(
  models: readonly PricingModelResponse[],
  column: PriceSortColumn,
  direction: SortDirection,
): PricingModelResponse[] {
  const field =
    column === 'input' ? 'inputTokenPricePerMillion' : 'outputTokenPricePerMillion'
  const factor = direction === 'asc' ? 1 : -1
  return [...models].sort((a, b) => (a[field] - b[field]) * factor)
}
