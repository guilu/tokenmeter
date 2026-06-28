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

// ─── TKM-72 — Copy badge Markdown handler ────────────────────────────────────

describe('DashboardPage Copy badge Markdown', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    window.history.pushState(null, '', '/')
  })

  async function setupResults() {
    window.history.pushState(null, '', '/analysis/analysis-with-pricing')
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(sampleAnalysis({ withPricing: true }))),
    )
    render(<DashboardPage />)
    return screen.findByText(/Analysis id:/)
  }

  it('clicking Copy badge Markdown writes correct snippet to clipboard and shows Copied!', async () => {
    const writeText = vi.fn<(text: string) => Promise<void>>().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      writable: true,
      configurable: true,
    })
    Object.defineProperty(window, 'isSecureContext', {
      value: true,
      writable: true,
      configurable: true,
    })

    await setupResults()

    fireEvent.click(screen.getByRole('button', { name: /Copy badge Markdown/i }))

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /Copied!/i })).toBeInTheDocument(),
    )

    // analysis.repositoryUrl = 'https://github.com/guilu/tokenmeter' → owner='guilu', repo='tokenmeter'
    // origin and publicUrl are derived from window.location at runtime
    const origin = window.location.origin
    const expectedSnippet = `[![AI generation cost](${origin}/api/badge/guilu/tokenmeter.svg)](${origin}/analysis/analysis-with-pricing)`
    expect(writeText).toHaveBeenCalledWith(expectedSnippet)
  })

  it('clipboard writeText failure transitions badge button to Copy failed', async () => {
    const writeText = vi
      .fn<(text: string) => Promise<void>>()
      .mockRejectedValue(new Error('Permission denied'))
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      writable: true,
      configurable: true,
    })
    Object.defineProperty(window, 'isSecureContext', {
      value: true,
      writable: true,
      configurable: true,
    })
    // Prevent the execCommand fallback from succeeding — define it if not present in jsdom
    Object.defineProperty(document, 'execCommand', {
      value: vi.fn().mockReturnValue(false),
      writable: true,
      configurable: true,
    })

    await setupResults()

    fireEvent.click(screen.getByRole('button', { name: /Copy badge Markdown/i }))

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /Copy failed/i })).toBeInTheDocument(),
    )
  })

  it('badgeCopyState is independent of copyState — badge copy does not affect Copy URL button', async () => {
    const writeText = vi.fn<(text: string) => Promise<void>>().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      writable: true,
      configurable: true,
    })
    Object.defineProperty(window, 'isSecureContext', {
      value: true,
      writable: true,
      configurable: true,
    })

    await setupResults()

    // Click "Copy badge Markdown" — badge transitions to 'copied'
    fireEvent.click(screen.getByRole('button', { name: /Copy badge Markdown/i }))

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /Copied!/i })).toBeInTheDocument(),
    )

    // "Copy URL" must still show 'Copy URL' (its state was not touched)
    expect(screen.getByRole('button', { name: /Copy URL/i })).toBeInTheDocument()
  })
})

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function makeEffort(multiplier: number) {
  return {
    seniorEngineerHours: multiplier,
    engineeringDays: multiplier * 0.125,
    manualImplementationEffort: `${multiplier} h`,
    summary: `${multiplier} h`,
    formula: 'effort formula',
    assumptions: {
      tokensPerSeniorEngineerHour: 1000,
      hoursPerEngineeringDay: 8,
      modeComplexityMultiplier: multiplier,
    },
  }
}

function sampleAnalysis({
  withPricing,
  manyLanguages = false,
}: {
  withPricing: boolean
  manyLanguages?: boolean
}): RepositoryAnalysisResponse {
  const languages: Record<string, { language: string; files: number; lines: number; bytes: number; tokens: number }> = {
    TypeScript: { language: 'TypeScript', files: 1, lines: 10, bytes: 100, tokens: 1000 },
  }

  if (manyLanguages) {
    const extras = ['JavaScript', 'Python', 'Java', 'Rust', 'Go', 'CSS', 'HTML', 'Shell', 'YAML', 'Markdown']
    for (const lang of extras) {
      languages[lang] = { language: lang, files: 1, lines: 5, bytes: 50, tokens: 100 }
    }
  }

  return {
    id: withPricing ? 'analysis-with-pricing' : 'analysis-without-pricing',
    createdAt: '2026-05-24T18:00:00Z',
    repositoryUrl: 'https://github.com/guilu/tokenmeter',
    status: 'SUCCESS',
    metrics: {
      totalFiles: manyLanguages ? 11 : 1,
      totalLines: manyLanguages ? 60 : 10,
      totalBytes: manyLanguages ? 650 : 100,
      tokenEncoding: 'o200k_base',
      totalTokens: manyLanguages ? 2000 : 1000,
      languages,
    },
    costEstimates: [
      // openai/gpt-4o — raw (keep totalCost 0.01 so existing assertions hold)
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
        engineeringEffort: makeEffort(1),
      },
      // openai/gpt-4o — assisted
      {
        provider: 'openai',
        model: 'gpt-4o',
        mode: 'assisted',
        baseTokens: 1000,
        estimatedInputTokens: 1000,
        estimatedOutputTokens: 5000,
        inputCost: 0.0025,
        outputCost: 0.05,
        totalCost: 0.0525,
        formula: 'assisted formula',
        engineeringEffort: makeEffort(5),
      },
      // openai/gpt-4o — agentic
      {
        provider: 'openai',
        model: 'gpt-4o',
        mode: 'agentic',
        baseTokens: 1000,
        estimatedInputTokens: 4000,
        estimatedOutputTokens: 20000,
        inputCost: 0.01,
        outputCost: 0.2,
        totalCost: 0.21,
        formula: 'agentic formula',
        engineeringEffort: makeEffort(20),
      },
      // anthropic/claude-3-5-sonnet — raw
      {
        provider: 'anthropic',
        model: 'claude-3-5-sonnet',
        mode: 'raw',
        baseTokens: 1000,
        estimatedInputTokens: 0,
        estimatedOutputTokens: 1000,
        inputCost: 0,
        outputCost: 0.015,
        totalCost: 0.015,
        formula: 'raw formula',
        engineeringEffort: makeEffort(1),
      },
      // anthropic/claude-3-5-sonnet — assisted
      {
        provider: 'anthropic',
        model: 'claude-3-5-sonnet',
        mode: 'assisted',
        baseTokens: 1000,
        estimatedInputTokens: 1000,
        estimatedOutputTokens: 5000,
        inputCost: 0.003,
        outputCost: 0.075,
        totalCost: 0.078,
        formula: 'assisted formula',
        engineeringEffort: makeEffort(5),
      },
      // anthropic/claude-3-5-sonnet — agentic
      {
        provider: 'anthropic',
        model: 'claude-3-5-sonnet',
        mode: 'agentic',
        baseTokens: 1000,
        estimatedInputTokens: 4000,
        estimatedOutputTokens: 20000,
        inputCost: 0.012,
        outputCost: 0.3,
        totalCost: 0.312,
        formula: 'agentic formula',
        engineeringEffort: makeEffort(20),
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

// ─── PR2 tests — tabs, dedup, output-tokens, show-all toggle ──────────────────

describe('DashboardPage ResultsView tabs', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    window.history.pushState(null, '', '/')
  })

  function setup(manyLanguages = false) {
    window.history.pushState(null, '', '/analysis/analysis-with-pricing')
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(sampleAnalysis({ withPricing: true, manyLanguages }))),
    )
    render(<DashboardPage />)
  }

  it('default active tab on load is Models (aria-selected=true)', async () => {
    setup()
    await screen.findByText(/Analysis id:/)

    const modelsTab = screen.getByRole('tab', { name: /Models/i })
    expect(modelsTab).toHaveAttribute('aria-selected', 'true')

    const otherTabs = screen.getAllByRole('tab').filter((t) => t !== modelsTab)
    for (const tab of otherTabs) {
      expect(tab).toHaveAttribute('aria-selected', 'false')
    }
  })

  it('clicking Languages tab makes it active and deactivates Models', async () => {
    setup()
    await screen.findByText(/Analysis id:/)

    fireEvent.click(screen.getByRole('tab', { name: /Languages/i }))

    expect(screen.getByRole('tab', { name: /Languages/i })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: /Models/i })).toHaveAttribute('aria-selected', 'false')
  })

  it('all 4 tab panels are in the DOM (getAllByRole with hidden:true returns 4)', async () => {
    setup()
    await screen.findByText(/Analysis id:/)

    const panels = screen.getAllByRole('tabpanel', { hidden: true })
    expect(panels).toHaveLength(4)
  })

  it('inactive panel has class hidden and print:block; active panel has class block and not hidden', async () => {
    setup()
    await screen.findByText(/Analysis id:/)

    // Models is active by default
    const modelsPanel = screen.getByTestId('tab-panel-models')
    expect(modelsPanel).toHaveClass('block')
    expect(modelsPanel).not.toHaveClass('hidden')

    const languagesPanel = screen.getByTestId('tab-panel-languages')
    expect(languagesPanel).toHaveClass('hidden')
    expect(languagesPanel).toHaveClass('print:block')
  })

  it('WhatIf panel wrapper has print:block; inner WhatIfPanel section retains print:hidden', async () => {
    const analysisId = 'analysis-with-pricing'
    window.history.pushState(null, '', `/analysis/${analysisId}`)
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString()
        if (url.includes('/cost-breakdown')) {
          return jsonResponse({
            analysisId,
            createdAt: '2026-05-24T18:00:00Z',
            repositoryUrl: 'https://github.com/guilu/tokenmeter',
            summary: { totalTokens: 1000, totalModels: 2, totalModes: 3 },
            models: [],
          })
        }
        return jsonResponse(sampleAnalysis({ withPricing: true }))
      }),
    )
    render(<DashboardPage />)
    await screen.findByText(/Analysis id:/)

    const whatifWrapper = screen.getByTestId('tab-panel-whatif')
    expect(whatifWrapper).toHaveClass('print:block')

    const panelHeading = await screen.findByRole('heading', { name: /What-If Multiplier Panel/i })
    const panelRoot = panelHeading.closest('section')
    expect(panelRoot?.className).toContain('print:hidden')
  })

  it('Output tokens column header is present in ModelComparison table', async () => {
    setup()
    await screen.findByText(/Analysis id:/)

    expect(screen.getByRole('columnheader', { name: /Output tokens/i })).toBeInTheDocument()
  })

  it('ModelComparison shows formatted output token count for a fixture row', async () => {
    setup()
    await screen.findByText(/Analysis id:/)

    // gpt-4o raw has estimatedOutputTokens: 1000 → formatted as "1,000"
    const panels = screen.getAllByTestId('tab-panel-models')
    expect(panels[0]).toHaveTextContent('1,000')
  })

  it('mode switch preserves active tab (switch to Workflow then change mode)', async () => {
    setup()
    await screen.findByText(/Analysis id:/)

    // Switch to Workflow tab
    fireEvent.click(screen.getByRole('tab', { name: /Workflow/i }))
    expect(screen.getByRole('tab', { name: /Workflow/i })).toHaveAttribute('aria-selected', 'true')

    // Switch mode via ModeSwitch buttons
    fireEvent.click(screen.getByRole('button', { name: /Assisted/i }))

    // Workflow tab should still be active
    expect(screen.getByRole('tab', { name: /Workflow/i })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: /Models/i })).toHaveAttribute('aria-selected', 'false')
  })

  it('dedup: "AI costs" eyebrow text is NOT rendered', async () => {
    setup()
    await screen.findByText(/Analysis id:/)

    expect(screen.queryByText(/AI costs/i)).not.toBeInTheDocument()
  })

  it('dedup: flat "AI generation estimates" Cost breakdown heading is NOT rendered', async () => {
    setup()
    await screen.findByText(/Analysis id:/)

    expect(screen.queryByText(/AI generation estimates/i)).not.toBeInTheDocument()
  })

  it('dedup: CostHero does NOT show "Total tokens" or "Files · languages" HeroMeta labels', async () => {
    setup()
    await screen.findByText(/Analysis id:/)

    expect(screen.queryByText(/Total tokens/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Files · languages/i)).not.toBeInTheDocument()
  })

  it('languages show-all toggle: top 8 shown by default when >8 languages exist', async () => {
    setup(true) // manyLanguages=true → 11 languages in fixture
    await screen.findByText(/Analysis id:/)

    // Switch to Languages tab
    fireEvent.click(screen.getByRole('tab', { name: /Languages/i }))

    // TypeScript + 10 extras = 11. Top 8 should be shown.
    // The toggle button should be present
    expect(screen.getByRole('button', { name: /Show all/i })).toBeInTheDocument()
  })

  it('languages show-all toggle: clicking reveal shows all languages', async () => {
    setup(true)
    await screen.findByText(/Analysis id:/)

    fireEvent.click(screen.getByRole('tab', { name: /Languages/i }))

    const showAllBtn = screen.getByRole('button', { name: /Show all/i })
    fireEvent.click(showAllBtn)

    // After click the toggle label should change
    expect(screen.getByRole('button', { name: /Show top 8/i })).toBeInTheDocument()
  })
})
