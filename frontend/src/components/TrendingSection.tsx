import { useEffect, useRef, useState } from 'react'

import { ApiError, getTrendingRepositories } from '../services/api'
import type { TrendingRepositoryResponse } from '../types/api'

const compactFormatter = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })

export function TrendingSection({ onAnalyze }: { onAnalyze: (url: string) => void }) {
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<TrendingRepositoryResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const requested = useRef(false)

  // Lazy fetch: only hit GitHub the first time the section is expanded.
  useEffect(() => {
    if (!open || requested.current) return
    requested.current = true
    let active = true
    getTrendingRepositories()
      .then((data) => {
        if (active) {
          setItems(data.items)
          setError(null)
        }
      })
      .catch((reason: unknown) => {
        if (active) setError(toSuggestionMessage(reason))
      })

    return () => {
      active = false
    }
  }, [open])

  const loading = open && items === null && error === null

  return (
    <div className="mx-auto max-w-4xl px-6 pb-6 md:pb-8">
      <button
        aria-controls="trending-panel"
        aria-expanded={open}
        className="mb-4 flex w-full items-center justify-between text-left"
        onClick={() => setOpen((v) => !v)}
        type="button"
      >
        <span className="flex items-center gap-2 text-sm font-medium text-text/80">
          Popular this week
          <svg
            aria-hidden="true"
            className={`h-4 w-4 text-text/50 transition-transform duration-300 ${open ? 'rotate-180' : ''}`}
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            viewBox="0 0 24 24"
          >
            <path d="M19 9l-7 7-7-7" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
        <span className="text-xs text-text/40">Suggested public repositories to analyze</span>
      </button>

      {open ? (
        <div id="trending-panel">
          {loading ? (
            <div aria-live="polite" className="grid gap-4 sm:grid-cols-3" role="status">
              {Array.from({ length: 6 }).map((_, i) => (
                <div className="h-36 animate-pulse rounded-2xl bg-card/60 shadow-xl shadow-bg/20" key={i} />
              ))}
            </div>
          ) : null}

          {!loading && error ? (
            <p
              aria-live="polite"
              className="rounded-2xl bg-card/60 p-5 text-sm text-text/60 shadow-xl shadow-bg/20"
              role="status"
            >
              {error}
            </p>
          ) : null}

          {!loading && !error && items && items.length === 0 ? (
            <p
              className="rounded-2xl bg-card/60 p-5 text-sm text-text/60 shadow-xl shadow-bg/20"
              role="status"
            >
              No suggestions available right now.
            </p>
          ) : null}

          {!loading && !error && items && items.length > 0 ? (
            <div className="grid gap-4 sm:grid-cols-3">
              {items.map((item) => (
                <TrendingRepoCard item={item} key={item.fullName} onAnalyze={onAnalyze} />
              ))}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

function TrendingRepoCard({ item, onAnalyze }: { item: TrendingRepositoryResponse; onAnalyze: (url: string) => void }) {
  return (
    <div className="flex flex-col rounded-2xl bg-card/60 p-5 shadow-xl shadow-bg/20">
      <div className="flex items-start justify-between gap-2">
        <p className="truncate font-semibold text-text">{item.fullName}</p>
        {item.language ? (
          <span className="shrink-0 rounded-full border border-primary/20 bg-primary/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-widest text-primary/80">
            {item.language}
          </span>
        ) : null}
      </div>
      {item.description ? (
        <p className="mt-2 line-clamp-2 text-sm leading-6 text-text/60">{item.description}</p>
      ) : null}
      <p className="mt-4 text-xs text-text/50">
        ★ {compactFormatter.format(item.stars)} · ⑂ {compactFormatter.format(item.forks)}
      </p>
      <button
        className="mt-4 min-h-10 rounded-2xl bg-primary px-4 text-sm font-semibold text-bg transition hover:bg-primary/90"
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
