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
        TypeScript: { language: 'TypeScript', files: 8, lines: 400, bytes: 16000, tokens: 4000 },
        CSS: { language: 'CSS', files: 2, lines: 100, bytes: 4000, tokens: 1000 },
      },
    },
    costEstimates: [],
    ...override,
  }
}

const baseMetrics = { languageCount: 2, modelCount: 3, averageCost: 2.5 }

describe('CostHero — min/max range + embedded repo metrics', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders the lowest estimate cost in the Min card', () => {
    const lowest = makeEstimate({ totalCost: 1.23, model: 'gpt-4o-mini' })
    const highest = makeEstimate({ totalCost: 4.56, model: 'gpt-4o' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={lowest}
        highestEstimate={highest}
        selectedMode="assisted"
        {...baseMetrics}
      />,
    )

    expect(screen.getByText('$1.23')).toBeInTheDocument()
  })

  it('renders the highest estimate cost in the Max card', () => {
    const lowest = makeEstimate({ totalCost: 1.23, model: 'gpt-4o-mini' })
    const highest = makeEstimate({ totalCost: 4.56, provider: 'anthropic', model: 'claude-opus' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={lowest}
        highestEstimate={highest}
        selectedMode="assisted"
        {...baseMetrics}
      />,
    )

    expect(screen.getByText('$4.56')).toBeInTheDocument()
  })

  it('renders the "Min" and "Max" badge labels', () => {
    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={makeEstimate({ totalCost: 1.23 })}
        highestEstimate={makeEstimate({ totalCost: 4.56 })}
        selectedMode="assisted"
        {...baseMetrics}
      />,
    )

    expect(screen.getByText('Min')).toBeInTheDocument()
    expect(screen.getByText('Max')).toBeInTheDocument()
  })

  it('labels each card with the selected workflow mode (reacts to mode)', () => {
    const lowest = makeEstimate({ totalCost: 1.23, provider: 'openai', model: 'gpt-4o-mini' })
    const highest = makeEstimate({ totalCost: 4.56, provider: 'anthropic', model: 'claude-opus' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={lowest}
        highestEstimate={highest}
        selectedMode="agentic"
        {...baseMetrics}
      />,
    )

    expect(screen.getByText(/gpt-4o-mini · agentic workflow mode/i)).toBeInTheDocument()
    expect(screen.getByText(/claude-opus · agentic workflow mode/i)).toBeInTheDocument()
  })

  it('renders an em dash in each cost card when both estimates are null', () => {
    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={null}
        highestEstimate={null}
        selectedMode="assisted"
        {...baseMetrics}
      />,
    )

    expect(screen.getAllByText('—')).toHaveLength(2)
  })

  it('renders the 4 repo metric cards (Tokens, Files, Languages, Avg. cost) inside the hero', () => {
    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={makeEstimate({ totalCost: 1.23 })}
        highestEstimate={makeEstimate({ totalCost: 4.56 })}
        selectedMode="assisted"
        languageCount={2}
        modelCount={3}
        averageCost={2.5}
      />,
    )

    expect(screen.getByText('Tokens')).toBeInTheDocument()
    expect(screen.getByText('Files')).toBeInTheDocument()
    expect(screen.getByText('Languages')).toBeInTheDocument()
    expect(screen.getByText('Avg. cost')).toBeInTheDocument()
    // Files value + Avg cost value
    expect(screen.getByText('10')).toBeInTheDocument()
    expect(screen.getByText('$2.50')).toBeInTheDocument()
    // Avg cost hint shows mode + model count
    expect(screen.getByText(/assisted mode across 3 models/i)).toBeInTheDocument()
  })

  it('renders the language count and token encoding in the Languages card', () => {
    render(
      <CostHero
        analysis={makeAnalysis()}
        lowestEstimate={makeEstimate({ totalCost: 1.23 })}
        highestEstimate={makeEstimate({ totalCost: 4.56 })}
        selectedMode="raw"
        languageCount={14}
        modelCount={84}
        averageCost={1086.74}
      />,
    )

    expect(screen.getByText('14')).toBeInTheDocument()
    expect(screen.getByText(/o200k_base encoding/i)).toBeInTheDocument()
  })
})
