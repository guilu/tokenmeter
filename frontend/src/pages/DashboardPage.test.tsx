/** @vitest-environment jsdom */

import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { DashboardPage } from './DashboardPage'
import type { RepositoryAnalysisResponse } from '../types/api'

describe('DashboardPage trending integration', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    window.history.pushState(null, '', '/')
  })

  it('launches analysis for a trending repository when its Analyze button is clicked', async () => {
    const fetchMock = vi.fn<
      (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
    >(async (input) => {
      const url = typeof input === 'string' ? input : input.toString()
      if (url.includes('/api/repositories/trending')) {
        return jsonResponse({
          fetchedAt: '2026-05-27T12:00:00Z',
          since: 'weekly',
          language: null,
          items: [
            {
              fullName: 'acme/widget',
              repositoryUrl: 'https://github.com/acme/widget',
              description: 'A handy widget',
              language: 'Java',
              stars: 1234,
              forks: 56,
              sizeKb: 789,
              createdAt: '2026-05-20T00:00:00Z',
              updatedAt: '2026-05-26T00:00:00Z',
            },
          ],
        })
      }
      if (url.includes('/api/analyze/jobs/')) {
        return jsonResponse({
          jobId: 'job-1',
          status: 'RUNNING',
          phase: 'CLONING_REPOSITORY',
          progressPercent: 20,
        })
      }
      if (url.includes('/api/analyze')) {
        return jsonResponse({
          jobId: 'job-1',
          status: 'QUEUED',
          statusUrl: '/api/analyze/jobs/job-1',
          analysisId: null,
        })
      }
      return jsonResponse({})
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<DashboardPage />)

    // Trending section is collapsed by default — expand it first.
    fireEvent.click(screen.getByRole('button', { name: /Popular this week/ }))

    const analyzeButton = await screen.findByRole('button', {
      name: /Analyze acme\/widget/,
    })
    fireEvent.click(analyzeButton)

    await waitFor(() => {
      const analyzePost = fetchMock.mock.calls.find(
        (call) =>
          (typeof call[0] === 'string' ? call[0] : String(call[0])).includes(
            '/api/analyze',
          ) && call[1]?.method === 'POST',
      )
      expect(analyzePost).toBeDefined()
      expect(String(analyzePost?.[1]?.body)).toContain(
        'https://github.com/acme/widget',
      )
    })
  })
})

describe('DashboardPage pricing footer', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    window.history.pushState(null, '', '/')
  })

  it('renders the pricing footer when the analysis response includes pricing metadata', async () => {
    window.history.pushState(null, '', '/analysis/analysis-with-pricing')
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(sampleAnalysis({ withPricing: true }))),
    )

    render(<DashboardPage />)

    expect(
      await screen.findByText(/Pricing: REMOTE · captured/),
    ).toBeInTheDocument()
  })

  it('omits the pricing footer when the analysis response has no pricing metadata', async () => {
    window.history.pushState(null, '', '/analysis/analysis-without-pricing')
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(sampleAnalysis({ withPricing: false }))),
    )

    render(<DashboardPage />)

    await screen.findByText(/Analysis id:/)

    await waitFor(() =>
      expect(screen.queryByText(/Pricing:/)).not.toBeInTheDocument(),
    )
  })
})

describe('DashboardPage export controls', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    window.history.pushState(null, '', '/')
  })

  it('shows Markdown download link with correct href', async () => {
    const analysisId = 'analysis-with-pricing'
    window.history.pushState(null, '', `/analysis/${analysisId}`)
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(sampleAnalysis({ withPricing: true }))),
    )

    render(<DashboardPage />)

    await screen.findByText(/Analysis id:/)

    const link = screen.getByRole('link', { name: /Markdown/i })
    expect(link).toHaveAttribute('href', `/api/analyze/${analysisId}/export.md`)
    expect(link).toHaveAttribute('download')
  })

  it('Export PDF button calls window.print once', async () => {
    window.history.pushState(null, '', '/analysis/analysis-with-pricing')
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(sampleAnalysis({ withPricing: true }))),
    )
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {})

    render(<DashboardPage />)

    await screen.findByText(/Analysis id:/)

    const pdfButton = screen.getByRole('button', { name: /Export PDF/i })
    fireEvent.click(pdfButton)

    expect(printSpy).toHaveBeenCalledOnce()
  })

  it('action row buttons container carries print:hidden class', async () => {
    window.history.pushState(null, '', '/analysis/analysis-with-pricing')
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(sampleAnalysis({ withPricing: true }))),
    )

    render(<DashboardPage />)

    await screen.findByText(/Analysis id:/)

    const pdfButton = screen.getByRole('button', { name: /Export PDF/i })
    // The action-buttons wrapper must carry print:hidden
    expect(pdfButton.closest('div')).toHaveClass('print:hidden')
  })
})

describe('DashboardPage WhatIfPanel integration', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    window.history.pushState(null, '', '/')
  })

  function makeBreakdownResponse(analysisId: string) {
    return {
      analysisId,
      createdAt: '2026-05-24T18:00:00Z',
      repositoryUrl: 'https://github.com/guilu/tokenmeter',
      summary: { totalTokens: 1000, totalModels: 1, totalModes: 3 },
      models: [
        {
          provider: 'openai',
          model: 'gpt-4o',
          pricing: { inputTokenPricePerMillion: 2.5, outputTokenPricePerMillion: 10.0 },
          modes: [],
        },
      ],
    }
  }

  function makeFetchWithBreakdown(analysisId: string) {
    return vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString()
      if (url.includes('/cost-breakdown')) {
        return jsonResponse(makeBreakdownResponse(analysisId))
      }
      return jsonResponse(sampleAnalysis({ withPricing: true }))
    })
  }

  it('fetches cost-breakdown exactly once on results view load', async () => {
    const analysisId = 'analysis-with-pricing'
    window.history.pushState(null, '', `/analysis/${analysisId}`)
    const fetchMock = makeFetchWithBreakdown(analysisId)
    vi.stubGlobal('fetch', fetchMock)

    render(<DashboardPage />)
    await screen.findByText(/Analysis id:/)

    // Wait for WhatIfPanel to appear (breakdown resolved)
    await screen.findByRole('heading', { name: /What-If Multiplier Panel/i })

    const breakdownCalls = fetchMock.mock.calls.filter((call) => {
      const url = typeof call[0] === 'string' ? call[0] : String(call[0])
      return url.includes('/cost-breakdown')
    })
    expect(breakdownCalls).toHaveLength(1)
  })

  it('renders WhatIfPanel with a multiplier slider after results load', async () => {
    const analysisId = 'analysis-with-pricing'
    window.history.pushState(null, '', `/analysis/${analysisId}`)
    vi.stubGlobal('fetch', makeFetchWithBreakdown(analysisId))

    render(<DashboardPage />)
    await screen.findByText(/Analysis id:/)

    // Sliders appear once WhatIfPanel is rendered
    const sliders = await screen.findAllByRole('slider')
    expect(sliders.length).toBeGreaterThan(0)
  })

  it('WhatIfPanel root element carries print:hidden class', async () => {
    const analysisId = 'analysis-with-pricing'
    window.history.pushState(null, '', `/analysis/${analysisId}`)
    vi.stubGlobal('fetch', makeFetchWithBreakdown(analysisId))

    render(<DashboardPage />)
    await screen.findByText(/Analysis id:/)

    const panelHeading = await screen.findByRole('heading', { name: /What-If Multiplier Panel/i })
    // The panel's outermost section element carries print:hidden
    const panelRoot = panelHeading.closest('section')
    expect(panelRoot?.className).toContain('print:hidden')
  })

  it('changing a slider does NOT trigger additional cost-breakdown fetches', async () => {
    const analysisId = 'analysis-with-pricing'
    window.history.pushState(null, '', `/analysis/${analysisId}`)
    const fetchMock = makeFetchWithBreakdown(analysisId)
    vi.stubGlobal('fetch', fetchMock)

    render(<DashboardPage />)
    await screen.findByText(/Analysis id:/)

    const sliders = await screen.findAllByRole('slider')
    const outputSlider = sliders.find((el) => {
      const label = el.getAttribute('aria-label') ?? ''
      return /output/i.test(label)
    })
    expect(outputSlider).toBeDefined()

    // Count breakdown calls before slider change
    const callsBefore = fetchMock.mock.calls.filter((call) => {
      const url = typeof call[0] === 'string' ? call[0] : String(call[0])
      return url.includes('/cost-breakdown')
    }).length

    fireEvent.change(outputSlider!, { target: { value: '10' } })

    // Give React a tick to process
    await new Promise((resolve) => setTimeout(resolve, 50))

    const callsAfter = fetchMock.mock.calls.filter((call) => {
      const url = typeof call[0] === 'string' ? call[0] : String(call[0])
      return url.includes('/cost-breakdown')
    }).length

    expect(callsAfter).toBe(callsBefore)
  })

  it('markdown export link is still present and unchanged alongside WhatIfPanel', async () => {
    const analysisId = 'analysis-with-pricing'
    window.history.pushState(null, '', `/analysis/${analysisId}`)
    vi.stubGlobal('fetch', makeFetchWithBreakdown(analysisId))

    render(<DashboardPage />)
    await screen.findByText(/Analysis id:/)
    await screen.findByRole('heading', { name: /What-If Multiplier Panel/i })

    const link = screen.getByRole('link', { name: /Markdown/i })
    expect(link).toHaveAttribute('href', `/api/analyze/${analysisId}/export.md`)
    expect(link).toHaveAttribute('download')
  })
})

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function sampleAnalysis({
  withPricing,
}: {
  withPricing: boolean
}): RepositoryAnalysisResponse {
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
