import { describe, expect, it } from 'vitest'

import type { CostBreakdownResponse, RepositoryAnalysisCostEstimateResponse } from '../types/api'
import {
  buildPricingMap,
  derivePricing,
  pricingKey,
  recalcCost,
} from './whatIfCost'

// ---------------------------------------------------------------------------
// pricingKey
// ---------------------------------------------------------------------------
describe('pricingKey', () => {
  it('formats provider and model separated by colon', () => {
    expect(pricingKey('openai', 'gpt-4o')).toBe('openai:gpt-4o')
  })

  it('preserves casing of provider and model', () => {
    expect(pricingKey('Anthropic', 'Claude-3-Opus')).toBe('Anthropic:Claude-3-Opus')
  })

  it('handles empty strings', () => {
    expect(pricingKey('', '')).toBe(':')
  })
})

// ---------------------------------------------------------------------------
// buildPricingMap
// ---------------------------------------------------------------------------

function makeBreakdown(
  models: Array<{
    provider: string
    model: string
    pricing: { inputTokenPricePerMillion: number; outputTokenPricePerMillion: number } | null
  }>,
): CostBreakdownResponse {
  return {
    analysisId: 'test-id',
    createdAt: '2026-01-01T00:00:00Z',
    repositoryUrl: 'https://github.com/test/repo',
    summary: { totalTokens: 1000, totalModels: models.length, totalModes: 3 },
    models: models.map((m) => ({
      provider: m.provider,
      model: m.model,
      pricing: m.pricing,
      modes: [],
    })),
  }
}

describe('buildPricingMap', () => {
  it('returns an empty map when breakdown is null', () => {
    const map = buildPricingMap(null)
    expect(map.size).toBe(0)
  })

  it('maps provider:model to pricing when pricing is not null', () => {
    const breakdown = makeBreakdown([
      { provider: 'openai', model: 'gpt-4o', pricing: { inputTokenPricePerMillion: 2.5, outputTokenPricePerMillion: 10.0 } },
    ])
    const map = buildPricingMap(breakdown)
    expect(map.size).toBe(1)
    expect(map.get('openai:gpt-4o')).toEqual({ inputPerMillion: 2.5, outputPerMillion: 10.0 })
  })

  it('skips models with pricing === null', () => {
    const breakdown = makeBreakdown([
      { provider: 'openai', model: 'gpt-4o', pricing: { inputTokenPricePerMillion: 2.5, outputTokenPricePerMillion: 10.0 } },
      { provider: 'anthropic', model: 'claude-3-haiku', pricing: null },
    ])
    const map = buildPricingMap(breakdown)
    expect(map.size).toBe(1)
    expect(map.has('anthropic:claude-3-haiku')).toBe(false)
  })

  it('maps multiple models with valid pricing', () => {
    const breakdown = makeBreakdown([
      { provider: 'openai', model: 'gpt-4o', pricing: { inputTokenPricePerMillion: 2.5, outputTokenPricePerMillion: 10.0 } },
      { provider: 'anthropic', model: 'claude-3-5-sonnet', pricing: { inputTokenPricePerMillion: 3.0, outputTokenPricePerMillion: 15.0 } },
    ])
    const map = buildPricingMap(breakdown)
    expect(map.size).toBe(2)
    expect(map.get('anthropic:claude-3-5-sonnet')).toEqual({ inputPerMillion: 3.0, outputPerMillion: 15.0 })
  })

  it('returns empty map when all models have null pricing', () => {
    const breakdown = makeBreakdown([
      { provider: 'openai', model: 'gpt-4o', pricing: null },
    ])
    const map = buildPricingMap(breakdown)
    expect(map.size).toBe(0)
  })
})

// ---------------------------------------------------------------------------
// recalcCost
// ---------------------------------------------------------------------------
describe('recalcCost', () => {
  it('calculates output-only cost for RAW mode (inputMult=0)', () => {
    const pricing = { outputPerMillion: 10.0, inputPerMillion: 0.5 }
    // baseTokens=1_000_000, outputMult=1, inputMult=0 → 1_000_000 * (1*10) / 1_000_000 = 10.0
    expect(recalcCost(1_000_000, 1, 0, pricing)).toBeCloseTo(10.0, 6)
  })

  it('calculates assisted-mode cost (outputMult=5, inputMult=1)', () => {
    const pricing = { outputPerMillion: 10.0, inputPerMillion: 2.5 }
    // baseTokens=1_000_000, outputMult=5, inputMult=1 → (5*10 + 1*2.5) = 52.5
    expect(recalcCost(1_000_000, 5, 1, pricing)).toBeCloseTo(52.5, 6)
  })

  it('calculates agentic-mode cost (outputMult=20, inputMult=4)', () => {
    const pricing = { outputPerMillion: 10.0, inputPerMillion: 2.5 }
    // baseTokens=1_000_000, outputMult=20, inputMult=4 → (20*10 + 4*2.5) = 210.0
    expect(recalcCost(1_000_000, 20, 4, pricing)).toBeCloseTo(210.0, 6)
  })

  it('returns 0 when baseTokens is 0', () => {
    const pricing = { outputPerMillion: 10.0, inputPerMillion: 2.5 }
    expect(recalcCost(0, 5, 1, pricing)).toBe(0)
  })

  it('uses the spec example: baseTokens=1_000_000, outputMult=3, inputMult=1, out=1.00, in=0.50 → 3.50', () => {
    // From spec scenario: (1_000_000 × (3×1.00 + 1×0.50)) / 1_000_000 = 3.50
    const pricing = { outputPerMillion: 1.0, inputPerMillion: 0.5 }
    expect(recalcCost(1_000_000, 3, 1, pricing)).toBeCloseTo(3.5, 6)
  })

  it('handles step=0.5 multipliers correctly', () => {
    const pricing = { outputPerMillion: 10.0, inputPerMillion: 5.0 }
    // outputMult=2.5, inputMult=0.5 → baseTokens=100_000 → (2.5*10 + 0.5*5)*100_000/1_000_000
    // = (25 + 2.5) * 0.1 = 2.75
    expect(recalcCost(100_000, 2.5, 0.5, pricing)).toBeCloseTo(2.75, 6)
  })

  it('handles small token counts with half-step multipliers', () => {
    // baseTokens=500_000, outputMult=1, inputMult=0 → 500_000 * 1 * outputPerMillion / 1_000_000
    const pricing = { outputPerMillion: 4.0, inputPerMillion: 1.0 }
    expect(recalcCost(500_000, 1, 0, pricing)).toBeCloseTo(2.0, 6)
  })

  it('handles fractional outputPerMillion values', () => {
    const pricing = { outputPerMillion: 0.15, inputPerMillion: 0.075 }
    // baseTokens=2_000_000, outputMult=5, inputMult=1 → (5*0.15 + 1*0.075) * 2 = 1.65
    expect(recalcCost(2_000_000, 5, 1, pricing)).toBeCloseTo(1.65, 6)
  })
})

// ---------------------------------------------------------------------------
// derivePricing
// ---------------------------------------------------------------------------

function makeEstimate(
  override: Partial<RepositoryAnalysisCostEstimateResponse>,
): RepositoryAnalysisCostEstimateResponse {
  return {
    provider: 'openai',
    model: 'gpt-4o',
    mode: 'raw',
    baseTokens: 1_000_000,
    estimatedInputTokens: 0,
    estimatedOutputTokens: 1_000_000,
    inputCost: 0,
    outputCost: 10.0,
    totalCost: 10.0,
    formula: 'raw formula',
    engineeringEffort: {
      seniorEngineerHours: 10,
      engineeringDays: 1.25,
      manualImplementationEffort: '1.25 days',
      summary: '1.25 days',
      formula: 'effort formula',
      assumptions: {
        tokensPerSeniorEngineerHour: 100_000,
        hoursPerEngineeringDay: 8,
        modeComplexityMultiplier: 1,
      },
    },
    ...override,
  }
}

describe('derivePricing', () => {
  it('returns null for empty array', () => {
    expect(derivePricing([])).toBeNull()
  })

  it('returns null for raw-only rows (no outputTokens > 0 or inputTokens > 0 ratio derivable)', () => {
    // RAW mode: estimatedInputTokens=0, so inputPerMillion is underivable
    // and outputCost/estimatedOutputTokens gives outputPerMillion but there's no input
    // Design says: raw-only set → null
    const rawOnly = [
      makeEstimate({ mode: 'raw', estimatedInputTokens: 0, estimatedOutputTokens: 1_000_000, outputCost: 10.0, inputCost: 0 }),
    ]
    expect(derivePricing(rawOnly)).toBeNull()
  })

  it('derives outputPerMillion from an assisted row', () => {
    // assisted: outputMult=5, baseTokens=1_000_000 → estimatedOutputTokens=5_000_000
    // outputCost = 5_000_000 * outputPerMillion / 1_000_000 = 5 * outputPerMillion
    // If outputCost=50 → outputPerMillion=10
    const assisted = makeEstimate({
      mode: 'assisted',
      baseTokens: 1_000_000,
      estimatedOutputTokens: 5_000_000,
      estimatedInputTokens: 1_000_000,
      outputCost: 50.0,    // → outputPerMillion = 50*1e6/5_000_000 = 10
      inputCost: 2.5,      // → inputPerMillion = 2.5*1e6/1_000_000 = 2.5
      totalCost: 52.5,
    })
    const result = derivePricing([assisted])
    expect(result).not.toBeNull()
    expect(result!.outputPerMillion).toBeCloseTo(10.0, 6)
    expect(result!.inputPerMillion).toBeCloseTo(2.5, 6)
  })

  it('derives pricing from an agentic row when no assisted row is present', () => {
    // agentic: outputMult=20, inputMult=4, baseTokens=500_000
    // estimatedOutputTokens=10_000_000, estimatedInputTokens=2_000_000
    // outputCost=100 → outputPerMillion = 100*1e6/10_000_000 = 10
    // inputCost=6 → inputPerMillion = 6*1e6/2_000_000 = 3
    const agentic = makeEstimate({
      mode: 'agentic',
      baseTokens: 500_000,
      estimatedOutputTokens: 10_000_000,
      estimatedInputTokens: 2_000_000,
      outputCost: 100.0,
      inputCost: 6.0,
      totalCost: 106.0,
    })
    const result = derivePricing([agentic])
    expect(result).not.toBeNull()
    expect(result!.outputPerMillion).toBeCloseTo(10.0, 5)
    expect(result!.inputPerMillion).toBeCloseTo(3.0, 5)
  })

  it('returns null when estimatedOutputTokens is 0 (cannot derive)', () => {
    const badRow = makeEstimate({
      mode: 'assisted',
      estimatedOutputTokens: 0,
      estimatedInputTokens: 1_000_000,
      outputCost: 0,
      inputCost: 2.5,
    })
    expect(derivePricing([badRow])).toBeNull()
  })

  it('prefers the first non-raw row that has both derivable values', () => {
    const assisted = makeEstimate({
      mode: 'assisted',
      baseTokens: 1_000_000,
      estimatedOutputTokens: 5_000_000,
      estimatedInputTokens: 1_000_000,
      outputCost: 50.0,
      inputCost: 2.5,
      totalCost: 52.5,
    })
    const agentic = makeEstimate({
      mode: 'agentic',
      baseTokens: 1_000_000,
      estimatedOutputTokens: 20_000_000,
      estimatedInputTokens: 4_000_000,
      outputCost: 200.0,
      inputCost: 10.0,
      totalCost: 210.0,
    })
    const result = derivePricing([assisted, agentic])
    // Should use the assisted row values
    expect(result!.outputPerMillion).toBeCloseTo(10.0, 6)
    expect(result!.inputPerMillion).toBeCloseTo(2.5, 6)
  })
})
