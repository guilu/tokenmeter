import { useEffect, useState } from 'react'

import { ApiError, getTrendingRepositories } from '../services/api'
import type { TrendingRepositoryResponse } from '../types/api'

const compactFormatter = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })

export function TrendingSection({ onAnalyze }: { onAnalyze: (url: string) => void }) {
  const [items, setItems] = useState<TrendingRepositoryResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    getTrendingRepositories()
      .then((data) => {
        if (!active) return
        setItems(data.items)
        setError(null)
        setLoading(false)
      })
      .catch((reason: unknown) => {
        if (!active) return
        setError(toSuggestionMessage(reason))
        setLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  return (
    <section aria-labelledby="trending-heading" className="mx-auto mt-10 max-w-4xl px-6">
      <div className="mb-4 flex items-baseline justify-between">
        <h2 className="text-lg font-semibold text-text" id="trending-heading">
          Popular this week
        </h2>
        <span className="text-xs text-text/50">Suggested public repositories to analyze</span>
      </div>

      {loading ? (
        <div aria-live="polite" className="grid gap-3 sm:grid-cols-2" role="status">
          {Array.from({ length: 4 }).map((_, i) => (
            <div className="h-28 animate-pulse rounded-2xl border border-text/10 bg-card/30" key={i} />
          ))}
        </div>
      ) : null}

      {!loading && error ? (
        <p
          aria-live="polite"
          className="rounded-2xl border border-text/10 bg-card/20 px-4 py-3 text-sm text-text/60"
          role="status"
        >
          {error}
        </p>
      ) : null}

      {!loading && !error && items && items.length === 0 ? (
        <p
          className="rounded-2xl border border-text/10 bg-card/20 px-4 py-3 text-sm text-text/60"
          role="status"
        >
          No suggestions available right now.
        </p>
      ) : null}

      {!loading && !error && items && items.length > 0 ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {items.map((item) => (
            <TrendingRepoCard item={item} key={item.fullName} onAnalyze={onAnalyze} />
          ))}
        </div>
      ) : null}
    </section>
  )
}

function TrendingRepoCard({ item, onAnalyze }: { item: TrendingRepositoryResponse; onAnalyze: (url: string) => void }) {
  return (
    <div className="flex flex-col justify-between gap-3 rounded-2xl border border-text/10 bg-card/30 p-4">
      <div>
        <div className="flex items-center justify-between gap-2">
          <h3 className="truncate text-sm font-semibold text-text">{item.fullName}</h3>
          {item.language ? (
            <span className="shrink-0 rounded-full bg-text/10 px-2 py-0.5 text-xs text-text/70">{item.language}</span>
          ) : null}
        </div>
        {item.description ? (
          <p className="mt-1 line-clamp-2 text-xs text-text/60">{item.description}</p>
        ) : null}
        <p className="mt-2 text-xs text-text/50">
          ★ {compactFormatter.format(item.stars)} · ⑂ {compactFormatter.format(item.forks)}
        </p>
      </div>
      <button
        className="min-h-9 rounded-xl bg-primary/90 px-4 text-xs font-semibold text-bg transition hover:bg-primary"
        onClick={() => onAnalyze(item.repositoryUrl)}
        type="button"
      >
        Analyze {item.fullName}
      </button>
    </div>
  )
}

function toSuggestionMessage(reason: unknown): string {
  if (reason instanceof ApiError) {
    if (reason.code === 'GITHUB_RATE_LIMITED') {
      return 'Suggestions are rate-limited right now. Try again in a few minutes.'
    }
    if (reason.code === 'GITHUB_UNAVAILABLE') {
      return "Couldn't load suggestions — GitHub is temporarily unavailable."
    }
    return reason.message
  }
  return "Couldn't load repository suggestions."
}
