/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { LeaderboardRankingsSection } from '../LeaderboardRankingsSection'
import type { LeaderboardEntryResponse, LeaderboardPageResponse } from '../../../types/api'

function makeEntry(overrides: Partial<LeaderboardEntryResponse> = {}): LeaderboardEntryResponse {
  return {
    rank: 1,
    analysisId: 'analysis-1',
    repositoryUrl: 'https://github.com/owner/repo',
    owner: 'owner',
    name: 'repo',
    analyzedAt: '2026-05-01T12:00:00Z',
    totalFiles: 100,
    totalLines: 5000,
    totalBytes: 512000,
    totalTokens: 80000,
    analysisCount: 3,
    provider: 'openai',
    model: 'gpt-4o',
    mode: 'raw',
    totalCost: 4.2,
    costPerMillionTokens: 52.5,
    ...overrides,
  }
}

const sampleLeaderboard: LeaderboardPageResponse = {
  category: 'most-expensive',
  page: 0,
  size: 12,
  totalElements: 2,
  totalPages: 1,
  filters: {},
  entries: [
    makeEntry({ rank: 1, analysisId: 'analysis-1', dominantLanguage: 'TypeScript' }),
    makeEntry({ rank: 2, analysisId: 'analysis-2', dominantLanguage: null }),
  ],
}

describe('LeaderboardRankingsSection', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('renders loading skeleton when status is loading', () => {
    const { container } = render(
      <LeaderboardRankingsSection
        status="loading"
        data={null}
        error={null}
        category="most-expensive"
        page={0}
        onCategoryChange={vi.fn()}
        onPageChange={vi.fn()}
      />,
    )
    const animatedElements = container.querySelectorAll('.animate-pulse')
    expect(animatedElements.length).toBeGreaterThan(0)
  })

  it('renders error message when status is error', () => {
    render(
      <LeaderboardRankingsSection
        status="error"
        data={null}
        error="Failed to load rankings"
        category="most-expensive"
        page={0}
        onCategoryChange={vi.fn()}
        onPageChange={vi.fn()}
      />,
    )
    expect(screen.getByText(/Failed to load rankings/)).toBeInTheDocument()
  })

  it('renders ranking entries when data is present', () => {
    render(
      <LeaderboardRankingsSection
        status="ok"
        data={sampleLeaderboard}
        error={null}
        category="most-expensive"
        page={0}
        onCategoryChange={vi.fn()}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('#1')).toBeInTheDocument()
    expect(screen.getByText('#2')).toBeInTheDocument()
    expect(screen.getAllByText('owner/repo').length).toBeGreaterThanOrEqual(1)
  })

  it('renders dominantLanguage when present, omits it when null', () => {
    render(
      <LeaderboardRankingsSection
        status="ok"
        data={sampleLeaderboard}
        error={null}
        category="most-expensive"
        page={0}
        onCategoryChange={vi.fn()}
        onPageChange={vi.fn()}
      />,
    )

    // First entry has dominantLanguage: 'TypeScript'
    expect(screen.getByText(/TypeScript/)).toBeInTheDocument()
    // Second entry has dominantLanguage: null — should not appear as a language badge
    const typescriptInstances = screen.getAllByText(/TypeScript/)
    expect(typescriptInstances).toHaveLength(1)
  })

  it('renders a Sort by select with category options', () => {
    render(
      <LeaderboardRankingsSection
        status="ok"
        data={sampleLeaderboard}
        error={null}
        category="most-expensive"
        page={0}
        onCategoryChange={vi.fn()}
        onPageChange={vi.fn()}
      />,
    )

    const sortSelect = screen.getByRole('combobox')
    expect(sortSelect).toBeInTheDocument()
  })

  it('calls onCategoryChange when sort dropdown changes', () => {
    const onCategoryChange = vi.fn()
    render(
      <LeaderboardRankingsSection
        status="ok"
        data={sampleLeaderboard}
        error={null}
        category="most-expensive"
        page={0}
        onCategoryChange={onCategoryChange}
        onPageChange={vi.fn()}
      />,
    )

    const sortSelect = screen.getByRole('combobox')
    fireEvent.change(sortSelect, { target: { value: 'highest-token-count' } })
    expect(onCategoryChange).toHaveBeenCalledWith('highest-token-count')
  })

  it('renders empty state when entries list is empty', () => {
    const emptyLeaderboard: LeaderboardPageResponse = {
      ...sampleLeaderboard,
      entries: [],
      totalElements: 0,
    }
    render(
      <LeaderboardRankingsSection
        status="ok"
        data={emptyLeaderboard}
        error={null}
        category="most-expensive"
        page={0}
        onCategoryChange={vi.fn()}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText(/No repositories match/)).toBeInTheDocument()
  })
})
