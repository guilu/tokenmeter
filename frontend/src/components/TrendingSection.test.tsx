/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { TrendingSection } from './TrendingSection'
import type { TrendingRepositoriesResponse } from '../types/api'

describe('TrendingSection', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('shows a loading skeleton before data resolves', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))) // never resolves
    render(<TrendingSection onAnalyze={() => {}} />)
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('renders suggestion cards when the endpoint returns items', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(samplePayload())))
    render(<TrendingSection onAnalyze={() => {}} />)

    expect(await screen.findByText('acme/widget')).toBeInTheDocument()
    expect(screen.getByText('A handy widget')).toBeInTheDocument()
    expect(screen.getByText('Java')).toBeInTheDocument()
  })

  it('shows an empty state when no items are returned', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({ fetchedAt: '2026-05-27T12:00:00Z', since: 'weekly', language: null, items: [] }),
      ),
    )
    render(<TrendingSection onAnalyze={() => {}} />)

    expect(await screen.findByText(/No suggestions available right now/)).toBeInTheDocument()
  })

  it('shows a discreet rate-limited message on 503 GITHUB_RATE_LIMITED without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => errorResponse(503, 'GITHUB_RATE_LIMITED', 'rate limited')),
    )
    render(<TrendingSection onAnalyze={() => {}} />)

    expect(await screen.findByText(/rate-limited right now/)).toBeInTheDocument()
  })

  it('shows an unavailable message on 503 GITHUB_UNAVAILABLE', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => errorResponse(503, 'GITHUB_UNAVAILABLE', 'down')),
    )
    render(<TrendingSection onAnalyze={() => {}} />)

    expect(await screen.findByText(/GitHub is temporarily unavailable/)).toBeInTheDocument()
  })

  it('invokes onAnalyze with the repository URL when Analyze is clicked', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(samplePayload())))
    const onAnalyze = vi.fn()
    render(<TrendingSection onAnalyze={onAnalyze} />)

    const button = await screen.findByRole('button', { name: /Analyze acme\/widget/ })
    fireEvent.click(button)

    expect(onAnalyze).toHaveBeenCalledWith('https://github.com/acme/widget')
  })

  it('does not warn or update state after unmount', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(samplePayload())))

    const { unmount } = render(<TrendingSection onAnalyze={() => {}} />)
    unmount()

    await waitFor(() => expect(errorSpy).not.toHaveBeenCalled())
  })
})

function samplePayload(): TrendingRepositoriesResponse {
  return {
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
  }
}

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function errorResponse(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ code, message, status }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
