/** @vitest-environment jsdom */

import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { DashboardPage } from './DashboardPage'
import type { RepositoryAnalysisResponse } from '../types/api'

describe('DashboardPage pricing footer', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    window.history.pushState(null, '', '/')
  })

  it('renders the pricing footer when the analysis response includes pricing metadata', async () => {
    window.history.pushState(null, '', '/analysis/analysis-with-pricing')
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(sampleAnalysis({ withPricing: true }))))

    render(<DashboardPage />)

    expect(await screen.findByText(/Pricing: REMOTE · captured/)).toBeInTheDocument()
  })

  it('omits the pricing footer when the analysis response has no pricing metadata', async () => {
    window.history.pushState(null, '', '/analysis/analysis-without-pricing')
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(sampleAnalysis({ withPricing: false }))))

    render(<DashboardPage />)

    await screen.findByText(/Analysis id:/)

    await waitFor(() => expect(screen.queryByText(/Pricing:/)).not.toBeInTheDocument())
  })
})

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function sampleAnalysis({ withPricing }: { withPricing: boolean }): RepositoryAnalysisResponse {
  return {
    id: withPricing ? 'analysis-with-pricing' : 'analysis-without-pricing',
    createdAt: '2026-05-24T18:00:00Z',
    repositoryUrl: 'https://github.com/guilu/tokenmeter',
    status: 'SUCCESS',
    metrics: {
      totalFiles: 1,
      totalLines: 10,
      totalBytes: 100,
      tokenEncoding: 'o200k_base',
      totalTokens: 1000,
      languages: {
        TypeScript: {
          language: 'TypeScript',
          files: 1,
          lines: 10,
          bytes: 100,
          tokens: 1000,
        },
      },
    },
    costEstimates: [
      {
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
      },
    ],
    ...(withPricing
      ? {
          pricing: {
            snapshotId: `v1:${'0'.repeat(64)}`,
            primarySource: 'REMOTE',
            capturedAt: '2026-05-24T18:42:11Z',
          },
        }
      : {}),
  }
}
