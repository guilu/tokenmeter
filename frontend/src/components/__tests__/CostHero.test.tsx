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

describe('CostHero — selected-mode headline + floor/ceiling band marker', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders the selected-mode cost as the prominent headline (moves with the mode)', () => {
    const floorEstimate = makeEstimate({ totalCost: 1.23, mode: 'raw' })
    const ceilingEstimate = makeEstimate({ totalCost: 99.5, mode: 'agentic' })
    const selectedModeEstimate = makeEstimate({ totalCost: 3.5, mode: 'assisted', provider: 'openai', model: 'gpt-4o' })

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

    expect(screen.getByText('$3.50')).toBeInTheDocument()
    expect(screen.getByText(/cheapest in assisted mode/i)).toBeInTheDocument()
  })

  it('renders floor and ceiling values in the band', () => {
    const floorEstimate = makeEstimate({ totalCost: 1.23, mode: 'raw', model: 'gpt-4o-mini' })
    const ceilingEstimate = makeEstimate({ totalCost: 99.5, mode: 'agentic', provider: 'anthropic', model: 'claude-opus' })
    const selectedModeEstimate = makeEstimate({ totalCost: 50, mode: 'assisted', provider: 'openai', model: 'gpt-4o' })

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

    expect(screen.getByText('$1.23')).toBeInTheDocument()
    expect(screen.getByText('$99.50')).toBeInTheDocument()
  })

  it('renders the "Floor (RAW)" and "Ceiling (AGENTIC)" band labels', () => {
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

  it('renders a "Viewing:" indicator with the current mode', () => {
    const floorEstimate = makeEstimate({ totalCost: 1.23, mode: 'raw' })
    const ceilingEstimate = makeEstimate({ totalCost: 9.87, mode: 'agentic' })
    const selectedModeEstimate = makeEstimate({ totalCost: 3.5, mode: 'assisted' })

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

    expect(screen.getByText(/Viewing:/i)).toBeInTheDocument()
    expect(screen.getAllByText(/assisted/i).length).toBeGreaterThan(0)
  })

  it('positions the mode marker proportionally inside the floor→ceiling band', () => {
    // floor 10, ceiling 110, selected 60 → (60-10)/100 = 50%
    const floorEstimate = makeEstimate({ totalCost: 10, mode: 'raw' })
    const ceilingEstimate = makeEstimate({ totalCost: 110, mode: 'agentic' })
    const selectedModeEstimate = makeEstimate({ totalCost: 60, mode: 'assisted' })

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

    const marker = screen.getByTestId('mode-marker')
    expect(marker).toBeInTheDocument()
    expect(marker.style.left).toBe('50%')
  })

  it('omits the marker when the band has no spread (floor === ceiling)', () => {
    const onlyRaw = makeEstimate({ totalCost: 0.5, mode: 'raw' })

    render(
      <CostHero
        analysis={makeAnalysis()}
        floorEstimate={onlyRaw}
        ceilingEstimate={onlyRaw}
        selectedModeEstimate={onlyRaw}
        selectedMode="raw"
        topLanguage={undefined}
      />,
    )

    expect(screen.queryByTestId('mode-marker')).not.toBeInTheDocument()
  })

  it('renders em dashes and no marker when all estimates are null', () => {
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

    // headline + floor + ceiling each show an em dash
    expect(screen.getAllByText('—')).toHaveLength(3)
    expect(screen.queryByTestId('mode-marker')).not.toBeInTheDocument()
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
