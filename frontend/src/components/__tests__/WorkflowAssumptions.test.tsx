/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import type { RepositoryAnalysisCostEstimateResponse } from '../../types/api'
import { WorkflowAssumptions } from '../WorkflowAssumptions'

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

describe('WorkflowAssumptions', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders title for raw mode', () => {
    render(
      <WorkflowAssumptions
        selectedMode="raw"
        estimate={null}
        rawBaselineEstimate={null}
      />,
    )

    expect(screen.getByText('Raw mode assumptions')).toBeInTheDocument()
  })

  it('renders title for assisted mode', () => {
    render(
      <WorkflowAssumptions
        selectedMode="assisted"
        estimate={null}
        rawBaselineEstimate={null}
      />,
    )

    expect(screen.getByText('Assisted mode assumptions')).toBeInTheDocument()
  })

  it('renders title for agentic mode', () => {
    render(
      <WorkflowAssumptions
        selectedMode="agentic"
        estimate={null}
        rawBaselineEstimate={null}
      />,
    )

    expect(screen.getByText('Agentic mode assumptions')).toBeInTheDocument()
  })

  it('renders multiplierLabel text when rawBaselineEstimate is null', () => {
    render(
      <WorkflowAssumptions
        selectedMode="raw"
        estimate={null}
        rawBaselineEstimate={null}
      />,
    )

    // raw mode multiplierLabel: 'Baseline output-only estimate'
    expect(screen.getByText('Baseline output-only estimate')).toBeInTheDocument()
  })

  it('renders computed multiplier ratio when both estimates are present', () => {
    const estimate = makeEstimate({ totalCost: 5.0, mode: 'assisted' })
    const rawBaselineEstimate = makeEstimate({ totalCost: 1.0, mode: 'raw' })

    render(
      <WorkflowAssumptions
        selectedMode="assisted"
        estimate={estimate}
        rawBaselineEstimate={rawBaselineEstimate}
      />,
    )

    // 5.0 / 1.0 = 5.0× vs raw floor
    expect(screen.getByText(/5\.0× vs raw floor/)).toBeInTheDocument()
  })
})
