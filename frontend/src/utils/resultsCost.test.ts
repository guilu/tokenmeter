/** @vitest-environment jsdom */

import { describe, expect, it } from 'vitest'

import type { ModelComparisonRow } from './resultsCost'
import {
  average,
  capitalize,
  cheapest,
  formatDecimal,
  highest,
  languageBreakdown,
  modelComparisonNote,
  modelTier,
  percentOf,
  uniqueProviders,
} from './resultsCost'
import type { RepositoryAnalysisCostEstimateResponse, RepositoryAnalysisResponse } from '../types/api'

function makeEstimate(overrides: Partial<RepositoryAnalysisCostEstimateResponse> = {}): RepositoryAnalysisCostEstimateResponse {
  return {
    provider: 'openai',
    model: 'gpt-4o',
    mode: 'raw',
    baseTokens: 1000,
    estimatedInputTokens: 0,
    estimatedOutputTokens: 1000,
    inputCost: 0,
    outputCost: 0.01,
    totalCost: 0.01,
    formula: 'raw formula',
    engineeringEffort: {
      seniorEngineerHours: 1,
      engineeringDays: 0.125,
      manualImplementationEffort: '1 h',
      summary: '1 h',
      formula: 'effort formula',
      assumptions: {
        tokensPerSeniorEngineerHour: 1000,
        hoursPerEngineeringDay: 8,
        modeComplexityMultiplier: 1,
      },
    },
    ...overrides,
  }
}

function makeAnalysis(overrides: Partial<RepositoryAnalysisResponse> = {}): RepositoryAnalysisResponse {
  return {
    id: 'test-id',
    createdAt: '2026-01-01T00:00:00Z',
    repositoryUrl: 'https://github.com/owner/repo',
    status: 'SUCCESS',
    metrics: {
      totalFiles: 10,
      totalLines: 100,
      totalBytes: 1000,
      tokenEncoding: 'o200k_base',
      totalTokens: 5000,
      languages: {
        TypeScript: { language: 'TypeScript', files: 5, lines: 50, bytes: 500, tokens: 3000 },
        Java: { language: 'Java', files: 5, lines: 50, bytes: 500, tokens: 2000 },
      },
    },
    costEstimates: [],
    ...overrides,
  }
}

describe('cheapest', () => {
  it('returns null for an empty array', () => {
    expect(cheapest([])).toBeNull()
  })

  it('returns the single estimate when array has one element', () => {
    const e = makeEstimate({ totalCost: 0.05 })
    expect(cheapest([e])).toBe(e)
  })

  it('returns the estimate with the lowest totalCost', () => {
    const low = makeEstimate({ totalCost: 0.01 })
    const high = makeEstimate({ totalCost: 0.10 })
    expect(cheapest([high, low])).toBe(low)
  })
})

describe('highest', () => {
  it('returns null for an empty array', () => {
    expect(highest([])).toBeNull()
  })

  it('returns the estimate with the highest totalCost', () => {
    const low = makeEstimate({ totalCost: 0.01 })
    const high = makeEstimate({ totalCost: 0.10 })
    expect(highest([low, high])).toBe(high)
  })
})

describe('average', () => {
  it('returns 0 for an empty array', () => {
    expect(average([])).toBe(0)
  })

  it('returns the single value when array has one element', () => {
    expect(average([5])).toBe(5)
  })

  it('returns the mean of values', () => {
    expect(average([1, 2, 3])).toBe(2)
  })
})

describe('percentOf', () => {
  it('returns 0 when total is 0', () => {
    expect(percentOf(10, 0)).toBe(0)
  })

  it('returns 50 when value is half of total', () => {
    expect(percentOf(5, 10)).toBe(50)
  })

  it('returns 100 when value equals total', () => {
    expect(percentOf(10, 10)).toBe(100)
  })
})

describe('capitalize', () => {
  it('capitalizes the first letter', () => {
    expect(capitalize('hello')).toBe('Hello')
  })

  it('does not change already-capitalized strings', () => {
    expect(capitalize('Raw')).toBe('Raw')
  })
})

describe('uniqueProviders', () => {
  it('returns empty array for no estimates', () => {
    expect(uniqueProviders([])).toEqual([])
  })

  it('returns unique providers sorted alphabetically', () => {
    const estimates = [
      makeEstimate({ provider: 'openai' }),
      makeEstimate({ provider: 'anthropic' }),
      makeEstimate({ provider: 'openai' }),
    ]
    expect(uniqueProviders(estimates)).toEqual(['anthropic', 'openai'])
  })
})

describe('modelTier', () => {
  it('returns Cheapest when relativeCost <= 1.05', () => {
    const estimate = makeEstimate({ totalCost: 0.01 })
    expect(modelTier(estimate, 0.01, 0.10)).toBe('Cheapest')
  })

  it('returns High reasoning for reasoning models', () => {
    const estimate = makeEstimate({ model: 'o1-preview', totalCost: 0.10 })
    expect(modelTier(estimate, 0.01, 0.10)).toBe('High reasoning')
  })

  it('returns Experimental for preview/experimental/xai models', () => {
    const estimate = makeEstimate({ model: 'gpt-5-preview', totalCost: 0.05 })
    expect(modelTier(estimate, 0.01, 0.10)).toBe('Experimental')
  })

  it('returns Premium for high-cost models', () => {
    const estimate = makeEstimate({ totalCost: 0.09 })
    expect(modelTier(estimate, 0.01, 0.10)).toBe('Premium')
  })

  it('returns Balanced for middle-range models', () => {
    const estimate = makeEstimate({ totalCost: 0.05 })
    expect(modelTier(estimate, 0.01, 0.10)).toBe('Balanced')
  })
})

describe('modelComparisonNote', () => {
  it('returns cheapest note for Cheapest tier', () => {
    const estimate = makeEstimate({ totalCost: 0.01 })
    const note = modelComparisonNote(estimate, 0.01, 0.10)
    expect(note).toContain('Lowest simulated cost')
  })
})

describe('formatDecimal', () => {
  it('formats whole number without decimals', () => {
    expect(formatDecimal(5, 2)).toBe('5')
  })

  it('formats decimal number with up to maximumFractionDigits', () => {
    expect(formatDecimal(1.5, 2)).toBe('1.5')
  })
})

describe('languageBreakdown', () => {
  it('returns languages sorted by tokens descending', () => {
    const analysis = makeAnalysis()
    const result = languageBreakdown(analysis)
    expect(result[0].language).toBe('TypeScript')
    expect(result[1].language).toBe('Java')
  })
})

describe('ModelComparisonRow type', () => {
  it('type is importable and usable', () => {
    const row: ModelComparisonRow = {
      estimate: makeEstimate(),
      relativeCost: 1,
      costPercent: 100,
      efficiencyScore: 0,
      tier: 'Cheapest',
      note: 'Lowest simulated cost for this workflow.',
    }
    expect(row.tier).toBe('Cheapest')
  })
})
