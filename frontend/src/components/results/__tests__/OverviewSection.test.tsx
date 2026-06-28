/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type {
  RepositoryAnalysisCostEstimateResponse,
  RepositoryAnalysisResponse,
} from '../../../types/api'
import { OverviewSection } from '../OverviewSection'

function makeEstimate(
  overrides: Partial<RepositoryAnalysisCostEstimateResponse> = {},
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
    ...overrides,
  }
}

function makeAnalysis(
  overrides: Partial<RepositoryAnalysisResponse> = {},
): RepositoryAnalysisResponse {
  return {
    id: 'test-overview-id',
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
    ...overrides,
  }
}

const defaultProps = {
  analysis: makeAnalysis(),
  selectedMode: 'raw' as const,
  onSelectMode: vi.fn(),
  lowestEstimate: makeEstimate({ totalCost: 0.01 }),
  highestEstimate: makeEstimate({ totalCost: 0.10 }),
  languageCount: 2,
  modelCount: 1,
  averageCost: 0.01,
  // Action row props
  onNewAnalysis: vi.fn(),
  copyState: 'idle' as const,
  onCopyPublicUrl: vi.fn(),
  selectedOpenGraphImageUrl: 'https://example.com/og.png',
  // Badge copy props (TKM-72)
  badgeCopyState: 'idle' as const,
  onCopyBadgeMarkdown: vi.fn(),
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('OverviewSection', () => {
  it('renders repo name as a link with repository URL text', () => {
    render(<OverviewSection {...defaultProps} />)
    const link = screen.getByRole('link', { name: /guilu\/tokenmeter/i })
    expect(link).toBeInTheDocument()
    expect(link).toHaveAttribute('href', 'https://github.com/guilu/tokenmeter')
  })

  it('renders Analysis id text', () => {
    render(<OverviewSection {...defaultProps} />)
    expect(screen.getByText(/Analysis id:/i)).toBeInTheDocument()
  })

  it('renders ModeSwitch with raw, assisted, agentic buttons', () => {
    render(<OverviewSection {...defaultProps} />)
    expect(screen.getByRole('button', { name: /raw/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /assisted/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /agentic/i })).toBeInTheDocument()
  })

  it('clicking a mode button calls onSelectMode with the correct key', () => {
    const onSelectMode = vi.fn()
    render(<OverviewSection {...defaultProps} onSelectMode={onSelectMode} />)
    fireEvent.click(screen.getByRole('button', { name: /assisted/i }))
    expect(onSelectMode).toHaveBeenCalledWith('assisted')
  })

  it('renders CostHero Min and Max labels', () => {
    render(<OverviewSection {...defaultProps} />)
    expect(screen.getByText('Min')).toBeInTheDocument()
    expect(screen.getByText('Max')).toBeInTheDocument()
  })

  it('renders all 4 MetricCard labels: Tokens, Files, Languages, Avg. cost', () => {
    render(<OverviewSection {...defaultProps} />)
    expect(screen.getByText('Tokens')).toBeInTheDocument()
    expect(screen.getByText('Files')).toBeInTheDocument()
    expect(screen.getByText('Languages')).toBeInTheDocument()
    expect(screen.getByText('Avg. cost')).toBeInTheDocument()
  })

  it('action row container carries print:hidden class', () => {
    render(<OverviewSection {...defaultProps} />)
    // The print:hidden div wraps the share buttons (Badge, Mini badge, etc.)
    // We find it via a button inside it
    const pdfButton = screen.getByRole('button', { name: /Export PDF/i })
    expect(pdfButton.closest('div')).toHaveClass('print:hidden')
  })

  // TKM-72 — Copy badge Markdown button
  it('renders "Copy badge Markdown" button when badgeCopyState is idle', () => {
    render(<OverviewSection {...defaultProps} badgeCopyState="idle" />)
    expect(screen.getByRole('button', { name: /Copy badge Markdown/i })).toBeInTheDocument()
  })

  it('clicking Copy badge Markdown button calls onCopyBadgeMarkdown', () => {
    const onCopyBadgeMarkdown = vi.fn()
    render(
      <OverviewSection
        {...defaultProps}
        badgeCopyState="idle"
        onCopyBadgeMarkdown={onCopyBadgeMarkdown}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /Copy badge Markdown/i }))
    expect(onCopyBadgeMarkdown).toHaveBeenCalledOnce()
  })

  it('shows "Copied!" when badgeCopyState is copied', () => {
    render(<OverviewSection {...defaultProps} badgeCopyState="copied" />)
    expect(screen.getByRole('button', { name: /Copied!/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Copy badge Markdown/i })).not.toBeInTheDocument()
  })

  it('shows "Copy failed" when badgeCopyState is failed', () => {
    render(<OverviewSection {...defaultProps} badgeCopyState="failed" />)
    expect(screen.getByRole('button', { name: /Copy failed/i })).toBeInTheDocument()
  })
})
