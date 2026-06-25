/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import type { RepositoryAnalysisCostEstimateResponse, RepositoryAnalysisResponse } from '../../types/api'
import { CostHero } from '../CostHero'

function makeEstimate(
  override: Partial<RepositoryAnalysisCostEstimateResponse>,
): RepositoryAnalysisCostEstimateResponse {
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
    ...override,
  }
}

function makeAnalysis(
  override?: Partial<RepositoryAnalysisResponse>,
): RepositoryAnalysisResponse {
  return {
    id: 'test-analysis-1',
    createdAt: '2026-05-24T18:00:00Z',
    repositoryUrl: 'https://github.com/guilu/tokenmeter',
    status: 'SUCCESS',
    metrics: {
      totalFiles: 10,
      totalLines: 500,
      totalBytes: 20000,
      tokenEncoding: 'o200k_base',
      totalTokens: 5000,
      languages: {
        TypeScript: {
          language: 'TypeScript',
          files: 8,
          lines: 400,
          bytes: 16000,
          tokens: 4000,
        },
        CSS: {
          language: 'CSS',
          files: 2,
          lines: 100,
          bytes: 4000,
          tokens: 1000,
        },
      },
    },
    costEstimates: [],
    ...override,
  }
}

describe('CostHero', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders lowestEstimate totalCost formatted as currency in the Min card', () => {
    const lowestEstimate = makeEstimate({ totalCost: 1.23, provider: 'openai', model: 'gpt-4o-mini' })
    const highestEstimate = makeEstimate({ totalCost: 9.87, provider: 'anthropic', model: 'claude-opus' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={lowestEstimate}
        highestEstimate={highestEstimate}
        selectedMode="raw"
        topLanguage={{ language: 'TypeScript', files: 8, lines: 400, bytes: 16000, tokens: 4000 }}
      />,
    )

    // Min card should show $1.23
    expect(screen.getByText('$1.23')).toBeInTheDocument()
  })

  it('renders highestEstimate totalCost in the Max card', () => {
    const lowestEstimate = makeEstimate({ totalCost: 1.23 })
    const highestEstimate = makeEstimate({ totalCost: 9.87, provider: 'anthropic', model: 'claude-opus' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={lowestEstimate}
        highestEstimate={highestEstimate}
        selectedMode="raw"
        topLanguage={undefined}
      />,
    )

    expect(screen.getByText('$9.87')).toBeInTheDocument()
  })

  it('renders em dash when both estimates are null', () => {
    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={null}
        highestEstimate={null}
        selectedMode="assisted"
        topLanguage={undefined}
      />,
    )

    // Should render two em dashes (one for Min, one for Max)
    const dashes = screen.getAllByText('—')
    expect(dashes).toHaveLength(2)
  })

  it('renders topLanguage label in HeroMeta', () => {
    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={null}
        highestEstimate={null}
        selectedMode="raw"
        topLanguage={{ language: 'TypeScript', files: 8, lines: 400, bytes: 16000, tokens: 4000 }}
      />,
    )

    expect(screen.getByText('TypeScript')).toBeInTheDocument()
  })

  it('renders "Unknown" when topLanguage is undefined', () => {
    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={null}
        highestEstimate={null}
        selectedMode="raw"
        topLanguage={undefined}
      />,
    )

    expect(screen.getByText('Unknown')).toBeInTheDocument()
  })
})
