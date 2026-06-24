/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import type { RepositoryAnalysisCostEstimateResponse } from '../../types/api'
import { HeuristicDisclaimer } from '../HeuristicDisclaimer'

function makeEstimate(
  override: Partial<RepositoryAnalysisCostEstimateResponse>,
): RepositoryAnalysisCostEstimateResponse {
  return {
    provider: 'openai',
    model: 'gpt-4o',
    mode: 'raw',
    baseTokens: 100,
    estimatedInputTokens: 0,
    estimatedOutputTokens: 100,
    inputCost: 0,
    outputCost: 0.01,
    totalCost: 0.01,
    formula: 'raw formula',
    engineeringEffort: {
      seniorEngineerHours: 1,
      engineeringDays: 0.1,
      manualImplementationEffort: '1 hour',
      summary: '1h',
      formula: 'ef',
      assumptions: {
        tokensPerSeniorEngineerHour: 10000,
        hoursPerEngineeringDay: 8,
        modeComplexityMultiplier: 1,
      },
    },
    ...override,
  }
}

describe('HeuristicDisclaimer', () => {
  afterEach(() => {
    cleanup()
  })

  it('shows disclaimer when at least one estimate has HEURISTIC precision', () => {
    const estimates = [
      makeEstimate({ precision: 'EXACT_LOCAL', tokenizerId: 'openai/o200k_base' }),
      makeEstimate({
        provider: 'anthropic',
        model: 'claude-3-5-sonnet',
        precision: 'HEURISTIC',
        tokenizerId: 'anthropic/cl100k_heuristic',
      }),
    ]

    render(<HeuristicDisclaimer estimates={estimates} />)

    expect(screen.getByRole('note')).toBeInTheDocument()
    expect(screen.getByText(/heuristic/i)).toBeInTheDocument()
  })

  it('shows disclaimer for all-HEURISTIC estimates', () => {
    const estimates = [
      makeEstimate({
        provider: 'anthropic',
        model: 'claude-3-opus',
        precision: 'HEURISTIC',
        tokenizerId: 'anthropic/cl100k_heuristic',
      }),
      makeEstimate({
        provider: 'google',
        model: 'gemini-2.5-pro',
        precision: 'HEURISTIC',
        tokenizerId: 'google/gemini_heuristic',
      }),
    ]

    render(<HeuristicDisclaimer estimates={estimates} />)

    expect(screen.getByRole('note')).toBeInTheDocument()
  })

  it('hides disclaimer when all estimates are EXACT_LOCAL', () => {
    const estimates = [
      makeEstimate({ precision: 'EXACT_LOCAL', tokenizerId: 'openai/o200k_base' }),
      makeEstimate({
        model: 'gpt-4-turbo',
        precision: 'EXACT_LOCAL',
        tokenizerId: 'openai/cl100k_base',
      }),
    ]

    const { container } = render(<HeuristicDisclaimer estimates={estimates} />)

    expect(container.firstChild).toBeNull()
  })

  it('hides disclaimer when all precision fields are null (legacy data)', () => {
    const estimates = [
      makeEstimate({ precision: undefined, tokenizerId: undefined }),
      makeEstimate({ precision: undefined, tokenizerId: undefined }),
    ]

    const { container } = render(<HeuristicDisclaimer estimates={estimates} />)

    expect(container.firstChild).toBeNull()
  })

  it('hides disclaimer for empty estimates array', () => {
    const { container } = render(<HeuristicDisclaimer estimates={[]} />)

    expect(container.firstChild).toBeNull()
  })
})
