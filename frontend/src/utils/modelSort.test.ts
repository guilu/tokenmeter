import { describe, expect, it } from 'vitest'

import type { PricingModelResponse } from '../types/api'
import { sortModels } from './modelSort'

function model(
  modelName: string,
  input: number,
  output: number,
): PricingModelResponse {
  return {
    provider: 'openai',
    model: modelName,
    inputTokenPricePerMillion: input,
    outputTokenPricePerMillion: output,
    source: 'REMOTE',
    fetchedAt: '2026-06-19T00:00:00Z',
  }
}

const models = [model('mid', 2, 30), model('cheap', 1, 10), model('pricey', 3, 20)]

describe('sortModels', () => {
  it('sorts by input price ascending', () => {
    expect(sortModels(models, 'input', 'asc').map((m) => m.model)).toEqual([
      'cheap',
      'mid',
      'pricey',
    ])
  })

  it('sorts by input price descending', () => {
    expect(sortModels(models, 'input', 'desc').map((m) => m.model)).toEqual([
      'pricey',
      'mid',
      'cheap',
    ])
  })

  it('sorts by output price ascending', () => {
    expect(sortModels(models, 'output', 'asc').map((m) => m.model)).toEqual([
      'cheap',
      'pricey',
      'mid',
    ])
  })

  it('sorts by output price descending', () => {
    expect(sortModels(models, 'output', 'desc').map((m) => m.model)).toEqual([
      'mid',
      'pricey',
      'cheap',
    ])
  })

  it('does not mutate the input array', () => {
    const original = [...models]
    sortModels(models, 'input', 'desc')
    expect(models).toEqual(original)
  })
})
