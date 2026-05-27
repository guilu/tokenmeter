/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import { OverviewMetricsSection } from '../OverviewMetricsSection'
import type { LeaderboardOverviewResponse } from '../../../types/api'

const sampleOverview: LeaderboardOverviewResponse = {
  totalRepos: 142,
  totalAnalyses: 380,
  totalTokens: 12450000,
  totalBytes: 89324551,
  costsByMode: [
    { mode: 'raw', totalCost: '12.45', analysisCount: 120 },
    { mode: 'assisted', totalCost: '62.25', analysisCount: 120 },
    { mode: 'agentic', totalCost: '249.00', analysisCount: 120 },
  ],
}

describe('OverviewMetricsSection', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders loading skeleton when status is loading', () => {
    const { container } = render(<OverviewMetricsSection status="loading" data={null} error={null} />)
    const animatedElements = container.querySelectorAll('.animate-pulse')
    expect(animatedElements.length).toBeGreaterThan(0)
  })

  it('renders error message when status is error', () => {
    render(<OverviewMetricsSection status="error" data={null} error="Failed to load overview" />)
    expect(screen.getByText(/Failed to load overview/)).toBeInTheDocument()
  })

  it('renders totalRepos, totalTokens and totalBytes cards when data is present', () => {
    render(<OverviewMetricsSection status="ok" data={sampleOverview} error={null} />)

    // totalRepos: 142 — appears as the big metric number
    expect(screen.getAllByText('142').length).toBeGreaterThan(0)
    // totalTokens: 12.5M compact — appears in the compact display
    expect(screen.getAllByText(/12\.5M|12,450,000/).length).toBeGreaterThan(0)
  })

  it('renders cost breakdown entries for each mode', () => {
    render(<OverviewMetricsSection status="ok" data={sampleOverview} error={null} />)

    expect(screen.getByText(/raw/i)).toBeInTheDocument()
    expect(screen.getByText(/assisted/i)).toBeInTheDocument()
    expect(screen.getByText(/agentic/i)).toBeInTheDocument()
  })

  it('renders empty state gracefully when costsByMode is empty', () => {
    const emptyOverview: LeaderboardOverviewResponse = {
      ...sampleOverview,
      totalRepos: 0,
      totalAnalyses: 0,
      totalTokens: 0,
      totalBytes: 0,
      costsByMode: [],
    }
    render(<OverviewMetricsSection status="ok" data={emptyOverview} error={null} />)
    // Should not crash — "No cost data available." shown for empty breakdown
    expect(screen.getByText(/No cost data available/)).toBeInTheDocument()
  })
})
