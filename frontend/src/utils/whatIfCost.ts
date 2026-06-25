import type {
  CostBreakdownResponse,
  RepositoryAnalysisCostEstimateResponse,
} from '../types/api'

export interface ModelPricing {
  inputPerMillion: number
  outputPerMillion: number
}

export type PricingMap = Map<string, ModelPricing>

/** Returns the canonical map key for a provider/model pair. */
export function pricingKey(provider: string, model: string): string {
  return `${provider}:${model}`
}

/**
 * Build a PricingMap from a CostBreakdownResponse.
 * Models whose server-side `pricing` is null are skipped.
 * Returns an empty map when `breakdown` is null.
 */
export function buildPricingMap(breakdown: CostBreakdownResponse | null): PricingMap {
  const map: PricingMap = new Map()
  if (!breakdown) return map

  if (!Array.isArray(breakdown.models)) return map

  for (const modelEntry of breakdown.models) {
    if (!modelEntry.pricing) continue
    map.set(pricingKey(modelEntry.provider, modelEntry.model), {
      inputPerMillion: modelEntry.pricing.inputTokenPricePerMillion,
      outputPerMillion: modelEntry.pricing.outputTokenPricePerMillion,
    })
  }

  return map
}

/**
 * Derive a ModelPricing from a model's stored estimate rows when the
 * cost-breakdown endpoint returned null pricing for that model.
 *
 * Strategy: find the first non-raw row where both estimatedOutputTokens > 0
 * and estimatedInputTokens > 0, then back-calculate:
 *   outputPerMillion = outputCost * 1e6 / estimatedOutputTokens
 *   inputPerMillion  = inputCost  * 1e6 / estimatedInputTokens
 *
 * Returns null when no suitable row is found (raw-only model, or zero
 * token denominators).
 */
export function derivePricing(
  estimatesForModel: RepositoryAnalysisCostEstimateResponse[],
): ModelPricing | null {
  for (const row of estimatesForModel) {
    if (row.mode === 'raw') continue
    if (row.estimatedOutputTokens <= 0) continue
    if (row.estimatedInputTokens <= 0) continue

    const outputPerMillion = (row.outputCost * 1_000_000) / row.estimatedOutputTokens
    const inputPerMillion = (row.inputCost * 1_000_000) / row.estimatedInputTokens

    return { outputPerMillion, inputPerMillion }
  }

  return null
}

/**
 * Pure recalculation function.
 * cost = baseTokens × (outputMult × outputPerMillion + inputMult × inputPerMillion) / 1_000_000
 */
export function recalcCost(
  baseTokens: number,
  outputMult: number,
  inputMult: number,
  pricing: ModelPricing,
): number {
  return (
    (baseTokens * (outputMult * pricing.outputPerMillion + inputMult * pricing.inputPerMillion)) /
    1_000_000
  )
}
