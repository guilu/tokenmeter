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

describe('CostHero — cross-mode floor/ceiling range', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders floorEstimate totalCost in the Floor (RAW) card', () => {
    const floorEstimate = makeEstimate({ totalCost: 1.23, mode: 'raw', provider: 'openai', model: 'gpt-4o-mini' })
    const ceilingEstimate = makeEstimate({ totalCost: 99.50, mode: 'agentic', provider: 'anthropic', model: 'claude-opus' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        floorEstimate={floorEstimate}
        ceilingEstimate={ceilingEstimate}
        selectedModeEstimate={floorEstimate}
        selectedMode="raw"
        topLanguage={{ language: 'TypeScript', files: 8, lines: 400, bytes: 16000, tokens: 4000 }}
      />,
    )

    expect(screen.getByText('$1.23')).toBeInTheDocument()
  })

  it('renders ceilingEstimate totalCost in the Ceiling (AGENTIC) card', () => {
    const floorEstimate = makeEstimate({ totalCost: 1.23, mode: 'raw' })
    const ceilingEstimate = makeEstimate({ totalCost: 99.50, mode: 'agentic', provider: 'anthropic', model: 'claude-opus' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        floorEstimate={floorEstimate}
        ceilingEstimate={ceilingEstimate}
        selectedModeEstimate={floorEstimate}
        selectedMode="raw"
        topLanguage={undefined}
      />,
    )

    expect(screen.getByText('$99.50')).toBeInTheDocument()
  })

  it('renders badge labels "Floor (RAW)" and "Ceiling (AGENTIC)"', () => {
    const floorEstimate = makeEstimate({ totalCost: 1.23, mode: 'raw' })
    const ceilingEstimate = makeEstimate({ totalCost: 9.87, mode: 'agentic' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        floorEstimate={floorEstimate}
        ceilingEstimate={ceilingEstimate}
        selectedModeEstimate={floorEstimate}
        selectedMode="raw"
        topLanguage={undefined}
      />,
    )

    expect(screen.getByText('Floor (RAW)')).toBeInTheDocument()
    expect(screen.getByText('Ceiling (AGENTIC)')).toBeInTheDocument()
  })

  it('renders em dash when both floor and ceiling estimates are null', () => {
    render(
      <CostHero
        analysis={makeAnalysis()}
        floorEstimate={null}
        ceilingEstimate={null}
        selectedModeEstimate={null}
        selectedMode="assisted"
        topLanguage={undefined}
      />,
    )

    const dashes = screen.getAllByText('—')
    expect(dashes).toHaveLength(2)
  })

  it('renders ceiling fallback value when no AGENTIC rows are available (fallback = max across all modes)', () => {
    // In this case ceilingEstimate is derived from assisted (fallback), not agentic
    const floorEstimate = makeEstimate({ totalCost: 0.50, mode: 'raw' })
    const fallbackCeiling = makeEstimate({ totalCost: 5.00, mode: 'assisted', provider: 'openai', model: 'gpt-4o' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        floorEstimate={floorEstimate}
        ceilingEstimate={fallbackCeiling}
        selectedModeEstimate={fallbackCeiling}
        selectedMode="assisted"
        topLanguage={undefined}
      />,
    )

    expect(screen.getByText('$5.00')).toBeInTheDocument()
    // Still renders Ceiling (AGENTIC) badge label regardless of fallback
    expect(screen.getByText('Ceiling (AGENTIC)')).toBeInTheDocument()
  })

  it('renders selected-mode indicator text when selectedModeEstimate is provided', () => {
    const floorEstimate = makeEstimate({ totalCost: 1.23, mode: 'raw' })
    const ceilingEstimate = makeEstimate({ totalCost: 9.87, mode: 'agentic' })
    const selectedModeEstimate = makeEstimate({ totalCost: 3.50, mode: 'assisted', provider: 'openai', model: 'gpt-4o' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        floorEstimate={floorEstimate}
        ceilingEstimate={ceilingEstimate}
        selectedModeEstimate={selectedModeEstimate}
        selectedMode="assisted"
        topLanguage={undefined}
      />,
    )

    // In-band indicator showing current mode
    expect(screen.getByText(/Viewing:/i)).toBeInTheDocument()
    expect(screen.getByText(/assisted/i)).toBeInTheDocument()
  })

  it('single-mode analysis: floor and ceiling both show raw values', () => {
    const cheapRaw = makeEstimate({ totalCost: 0.10, mode: 'raw', model: 'gpt-4o-mini' })
    const priceyRaw = makeEstimate({ totalCost: 0.80, mode: 'raw', model: 'gpt-4o' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        floorEstimate={cheapRaw}
        ceilingEstimate={priceyRaw}
        selectedModeEstimate={cheapRaw}
        selectedMode="raw"
        topLanguage={undefined}
      />,
    )

    expect(screen.getByText('$0.10')).toBeInTheDocument()
    expect(screen.getByText('$0.80')).toBeInTheDocument()
    // Selected mode indicator visible
    expect(screen.getByText(/Viewing:/i)).toBeInTheDocument()
  })

  it('renders topLanguage label in HeroMeta', () => {
    render(
      <CostHero
        analysis={makeAnalysis()}
        floorEstimate={null}
        ceilingEstimate={null}
        selectedModeEstimate={null}
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
        floorEstimate={null}
        ceilingEstimate={null}
        selectedModeEstimate={null}
        selectedMode="raw"
        topLanguage={undefined}
      />,
    )

    expect(screen.getByText('Unknown')).toBeInTheDocument()
  })
})
