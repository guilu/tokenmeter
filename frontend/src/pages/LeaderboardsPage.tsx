import { useEffect, useRef, useState } from 'react'

import { LeaderboardFilterBar } from '../components/leaderboards/LeaderboardFilterBar'
import { LanguageInsightsSection } from '../components/leaderboards/LanguageInsightsSection'
import { LeaderboardRankingsSection } from '../components/leaderboards/LeaderboardRankingsSection'
import { OverviewMetricsSection } from '../components/leaderboards/OverviewMetricsSection'
import { ApiError, getLeaderboard, getLeaderboardLanguages, getLeaderboardOverview } from '../services/api'
import type {
  LeaderboardLanguagesResponse,
  LeaderboardOverviewResponse,
  LeaderboardPageResponse,
} from '../types/api'

type FetchResult<T> =
  | { status: 'ok'; data: T; error: null }
  | { status: 'error'; data: null; error: string }

function toUserMessage(reason: unknown) {
  if (reason instanceof ApiError) return reason.message
  if (reason instanceof Error) return reason.message
  return 'TokenMeter could not load leaderboard data.'
}

function getInitialCategory() {
  const category = new URLSearchParams(window.location.search).get('category')
  const valid = [
    'most-expensive',
    'cheapest',
    'largest',
    'most-analyzed',
    'highest-token-count',
    'best-cost-efficiency',
  ]
  return valid.includes(category ?? '') ? category! : 'most-expensive'
}

export function LeaderboardsPage() {
  // Shared filter state — drives overview + languages + rankings
  const [mode, setMode] = useState('raw')
  const [provider, setProvider] = useState('')
  const [model, setModel] = useState('')

  // Rankings-specific state (category + page don't affect overview/languages)
  const [category, setCategory] = useState(() => getInitialCategory())
  const [page, setPage] = useState(0)

  // Keys track which filter combination each result belongs to
  const filterKey = `${mode}:${provider}:${model}`
  const rankingsKey = `${filterKey}:${category}:${page}`

  // Per-section results indexed by the key that produced them
  const [overviewResult, setOverviewResult] = useState<(FetchResult<LeaderboardOverviewResponse> & { key: string }) | null>(null)
  const [languagesResult, setLanguagesResult] = useState<(FetchResult<LeaderboardLanguagesResponse> & { key: string }) | null>(null)
  const [rankingsResult, setRankingsResult] = useState<(FetchResult<LeaderboardPageResponse> & { key: string }) | null>(null)

  // Derive per-section loading/ok/error states from key comparison
  const overviewLoading = overviewResult?.key !== filterKey
  const overviewData = overviewResult?.key === filterKey && overviewResult.status === 'ok' ? overviewResult.data : null
  const overviewError = overviewResult?.key === filterKey && overviewResult.status === 'error' ? overviewResult.error : null

  const languagesLoading = languagesResult?.key !== filterKey
  const languagesData = languagesResult?.key === filterKey && languagesResult.status === 'ok' ? languagesResult.data : null
  const languagesError = languagesResult?.key === filterKey && languagesResult.status === 'error' ? languagesResult.error : null

  const rankingsLoading = rankingsResult?.key !== rankingsKey
  const rankingsData = rankingsResult?.key === rankingsKey && rankingsResult.status === 'ok' ? rankingsResult.data : null
  const rankingsError = rankingsResult?.key === rankingsKey && rankingsResult.status === 'error' ? rankingsResult.error : null

  // Track active effect instances to cancel stale fetches
  const overviewActiveRef = useRef(true)
  const languagesActiveRef = useRef(true)
  const rankingsActiveRef = useRef(true)

  // Overview fetch
  useEffect(() => {
    overviewActiveRef.current = true
    const capturedKey = filterKey
    getLeaderboardOverview({ mode, provider, model })
      .then((data) => {
        if (overviewActiveRef.current) {
          setOverviewResult({ key: capturedKey, status: 'ok', data, error: null })
        }
      })
      .catch((err: unknown) => {
        if (overviewActiveRef.current) {
          setOverviewResult({ key: capturedKey, status: 'error', data: null, error: toUserMessage(err) })
        }
      })
    return () => {
      overviewActiveRef.current = false
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterKey])

  // Languages fetch
  useEffect(() => {
    languagesActiveRef.current = true
    const capturedKey = filterKey
    getLeaderboardLanguages({ mode, provider, model })
      .then((data) => {
        if (languagesActiveRef.current) {
          setLanguagesResult({ key: capturedKey, status: 'ok', data, error: null })
        }
      })
      .catch((err: unknown) => {
        if (languagesActiveRef.current) {
          setLanguagesResult({ key: capturedKey, status: 'error', data: null, error: toUserMessage(err) })
        }
      })
    return () => {
      languagesActiveRef.current = false
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterKey])

  // Rankings fetch
  useEffect(() => {
    rankingsActiveRef.current = true
    const capturedKey = rankingsKey
    getLeaderboard({ category, page, mode, provider, model })
      .then((data) => {
        if (rankingsActiveRef.current) {
          setRankingsResult({ key: capturedKey, status: 'ok', data, error: null })
        }
      })
      .catch((err: unknown) => {
        if (rankingsActiveRef.current) {
          setRankingsResult({ key: capturedKey, status: 'error', data: null, error: toUserMessage(err) })
        }
      })
    return () => {
      rankingsActiveRef.current = false
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rankingsKey])

  function handleCategoryChange(nextCategory: string) {
    setCategory(nextCategory)
    setPage(0)
    window.history.replaceState(null, '', `/leaderboards?category=${nextCategory}`)
  }

  const overviewStatus = overviewLoading ? 'loading' : overviewError ? 'error' : 'ok'
  const languagesStatus = languagesLoading ? 'loading' : languagesError ? 'error' : 'ok'
  const rankingsStatus = rankingsLoading ? 'loading' : rankingsError ? 'error' : 'ok'

  return (
    <div>
      {/* Sticky filter bar — drives all three sections */}
      <LeaderboardFilterBar
        mode={mode}
        provider={provider}
        model={model}
        onModeChange={(next) => {
          setMode(next)
          setPage(0)
        }}
        onProviderChange={(next) => {
          setProvider(next)
          setPage(0)
        }}
        onModelChange={(next) => {
          setModel(next)
          setPage(0)
        }}
      />

      <section className="mx-auto max-w-6xl px-6 py-10">
        <div className="mb-8 rounded-[2rem] border border-text/10 bg-card/20 p-8 shadow-2xl shadow-bg/20">
          <p className="mb-3 text-sm font-semibold uppercase tracking-[0.3em] text-primary">
            Community benchmarks
          </p>
          <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr] lg:items-end">
            <div>
              <h1 className="text-4xl font-semibold tracking-tight text-text sm:text-5xl">
                Public repository leaderboards
              </h1>
              <p className="mt-4 max-w-2xl text-base leading-7 text-text/80">
                Browse the repositories TokenMeter has analyzed by AI generation cost, token footprint, size and popularity.
              </p>
            </div>
            <div className="rounded-2xl border border-primary/20 bg-primary/10 p-5 text-sm text-primary">
              Rankings update from persisted analyses automatically. Open any entry to inspect the full public cost report.
            </div>
          </div>
        </div>

        {/* Overview metrics section */}
        <OverviewMetricsSection
          status={overviewStatus}
          data={overviewData}
          error={overviewError}
        />

        {/* Language insights section */}
        <LanguageInsightsSection
          status={languagesStatus}
          data={languagesData}
          error={languagesError}
        />

        {/* Rankings section — has its own category/sort control */}
        <LeaderboardRankingsSection
          status={rankingsStatus}
          data={rankingsData}
          error={rankingsError}
          category={category}
          page={page}
          onCategoryChange={handleCategoryChange}
          onPageChange={setPage}
        />
      </section>
    </div>
  )
}
