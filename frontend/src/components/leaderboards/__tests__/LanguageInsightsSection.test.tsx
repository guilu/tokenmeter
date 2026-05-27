/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import { LanguageInsightsSection } from '../LanguageInsightsSection'
import type { LeaderboardLanguagesResponse } from '../../../types/api'

const sampleLanguages: LeaderboardLanguagesResponse = {
  totalTokensAllLanguages: 5000000,
  languages: [
    { language: 'TypeScript', totalTokens: 3200000, repoCount: 87, sharePercent: '64.00' },
    { language: 'Python', totalTokens: 1100000, repoCount: 40, sharePercent: '22.00' },
    { language: 'Java', totalTokens: 700000, repoCount: 20, sharePercent: '14.00' },
  ],
}

describe('LanguageInsightsSection', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders loading skeleton when status is loading', () => {
    const { container } = render(<LanguageInsightsSection status="loading" data={null} error={null} />)
    const animatedElements = container.querySelectorAll('.animate-pulse')
    expect(animatedElements.length).toBeGreaterThan(0)
  })

  it('renders error message when status is error', () => {
    render(<LanguageInsightsSection status="error" data={null} error="Failed to load languages" />)
    expect(screen.getByText(/Failed to load languages/)).toBeInTheDocument()
  })

  it('renders a table row for each language entry', () => {
    render(<LanguageInsightsSection status="ok" data={sampleLanguages} error={null} />)

    expect(screen.getByText('TypeScript')).toBeInTheDocument()
    expect(screen.getByText('Python')).toBeInTheDocument()
    expect(screen.getByText('Java')).toBeInTheDocument()
  })

  it('renders compact token counts (3.2M) and repo counts', () => {
    render(<LanguageInsightsSection status="ok" data={sampleLanguages} error={null} />)

    expect(screen.getByText('3.2M')).toBeInTheDocument()
    expect(screen.getByText('87')).toBeInTheDocument()
  })

  it('renders share percent with % suffix', () => {
    render(<LanguageInsightsSection status="ok" data={sampleLanguages} error={null} />)

    expect(screen.getByText('64.00%')).toBeInTheDocument()
  })

  it('renders empty state when languages list is empty', () => {
    const emptyData: LeaderboardLanguagesResponse = {
      totalTokensAllLanguages: 0,
      languages: [],
    }
    render(<LanguageInsightsSection status="ok" data={emptyData} error={null} />)

    expect(screen.getByText(/No language data available/)).toBeInTheDocument()
  })

  it('renders inline SVG share bar for each language', () => {
    const { container } = render(<LanguageInsightsSection status="ok" data={sampleLanguages} error={null} />)

    const svgs = container.querySelectorAll('svg')
    // One SVG bar per language
    expect(svgs.length).toBeGreaterThanOrEqual(sampleLanguages.languages.length)
  })
})
