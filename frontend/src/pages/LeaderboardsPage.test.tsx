/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { LeaderboardsPage } from './LeaderboardsPage'
import type {
  LeaderboardLanguagesResponse,
  LeaderboardOverviewResponse,
  LeaderboardPageResponse,
} from '../types/api'

// ---------------------------------------------------------------------------
// Sample data
// ---------------------------------------------------------------------------

const sampleOverview: LeaderboardOverviewResponse = {
  totalRepos: 50,
  totalAnalyses: 200,
  totalTokens: 5000000,
  totalBytes: 40000000,
  costsByMode: [{ mode: 'raw', totalCost: '10.00', analysisCount: 100 }],
}

const sampleLanguages: LeaderboardLanguagesResponse = {
  totalTokensAllLanguages: 5000000,
  languages: [
    { language: 'TypeScript', totalTokens: 3200000, repoCount: 30, sharePercent: '64.00' },
  ],
}

const sampleLeaderboard: LeaderboardPageResponse = {
  category: 'most-expensive',
  page: 0,
  size: 12,
  totalElements: 1,
  totalPages: 1,
  filters: {},
  entries: [
    {
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
      dominantLanguage: 'TypeScript',
    },
  ],
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function matchUrl(url: string | URL | Request, pattern: string): boolean {
  const str = url instanceof Request ? url.url : String(url)
  return str.includes(pattern)
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('LeaderboardsPage', () => {
  beforeEach(() => {
    window.history.pushState(null, '', '/leaderboards')
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    window.history.pushState(null, '', '/')
  })

  it('fires three independent fetch requests on initial render', async () => {
    const fetchMock = vi.fn((url: string | URL | Request) => {
      if (matchUrl(url, '/insights/overview')) return Promise.resolve(jsonResponse(sampleOverview))
      if (matchUrl(url, '/insights/languages')) return Promise.resolve(jsonResponse(sampleLanguages))
      return Promise.resolve(jsonResponse(sampleLeaderboard))
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<LeaderboardsPage />)

    await waitFor(() => {
      const calls = fetchMock.mock.calls.map((c) => String(c[0] instanceof Request ? c[0].url : c[0]))
      expect(calls.some((url) => url.includes('/insights/overview'))).toBe(true)
      expect(calls.some((url) => url.includes('/insights/languages'))).toBe(true)
      expect(calls.some((url) => url.includes('/api/leaderboards') && !url.includes('/insights'))).toBe(true)
    })
  })

  it('shows loading skeletons for all three sections before data arrives', async () => {
    // Never resolve — keeps loading
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))

    const { container } = render(<LeaderboardsPage />)

    await waitFor(() => {
      const animatedElements = container.querySelectorAll('.animate-pulse')
      expect(animatedElements.length).toBeGreaterThan(0)
    })
  })

  it('renders data from all three sections when fetches resolve', async () => {
    const fetchMock = vi.fn((url: string | URL | Request) => {
      if (matchUrl(url, '/insights/overview')) return Promise.resolve(jsonResponse(sampleOverview))
      if (matchUrl(url, '/insights/languages')) return Promise.resolve(jsonResponse(sampleLanguages))
      return Promise.resolve(jsonResponse(sampleLeaderboard))
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<LeaderboardsPage />)

    // Overview section data
    await screen.findByText('50')
    // Language section data (TypeScript appears in language table AND ranking dominantLanguage — check at least one)
    await waitFor(() => {
      expect(screen.getAllByText('TypeScript').length).toBeGreaterThanOrEqual(1)
    })
    // Rankings section data
    await screen.findByText('#1')
  })

  it('shows per-section error without breaking sibling sections when overview fails', async () => {
    const fetchMock = vi.fn((url: string | URL | Request) => {
      if (matchUrl(url, '/insights/overview'))
        return Promise.resolve(jsonResponse({ message: 'Server error' }, 500))
      if (matchUrl(url, '/insights/languages')) return Promise.resolve(jsonResponse(sampleLanguages))
      return Promise.resolve(jsonResponse(sampleLeaderboard))
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<LeaderboardsPage />)

    // Language section renders normally (TypeScript may appear in language table and rankings badge)
    await waitFor(() => {
      expect(screen.getAllByText('TypeScript').length).toBeGreaterThanOrEqual(1)
    })
    // Rankings section renders normally
    await screen.findByText('#1')
    // Overview section shows error (shows the message from the API or a fallback)
    await screen.findByText(/Server error|overview request failed|could not load/i)
  })

  it('re-fetches all three sections when mode filter changes', async () => {
    const fetchMock = vi.fn((url: string | URL | Request) => {
      if (matchUrl(url, '/insights/overview')) return Promise.resolve(jsonResponse(sampleOverview))
      if (matchUrl(url, '/insights/languages')) return Promise.resolve(jsonResponse(sampleLanguages))
      return Promise.resolve(jsonResponse(sampleLeaderboard))
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<LeaderboardsPage />)

    await screen.findByText('50')

    const initialCallCount = fetchMock.mock.calls.length
    expect(initialCallCount).toBeGreaterThanOrEqual(3)

    // Change mode from 'raw' to 'assisted' via the filter bar's first select
    const selects = screen.getAllByRole('combobox')
    fireEvent.change(selects[0], { target: { value: 'assisted' } })

    await waitFor(() => {
      expect(fetchMock.mock.calls.length).toBeGreaterThan(initialCallCount)
    })
  })
})
