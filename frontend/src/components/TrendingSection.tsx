import { useCallback, useEffect, useRef, useState } from 'react'

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
    <div className={`mx-auto max-w-4xl px-6 ${open ? 'pb-6 md:pb-8' : 'pb-2'}`}>
      <button
        aria-controls="trending-panel"
        aria-expanded={open}
        className="mb-4 flex w-full items-center justify-center gap-2"
        onClick={() => setOpen((v) => !v)}
        type="button"
      >
        <span className="text-sm font-medium text-text/80">Popular this week</span>
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
      </button>

      {open ? (
        <div id="trending-panel">
          {loading ? (
            <div
              aria-live="polite"
              className="flex gap-4 overflow-hidden"
              role="status"
            >
              {Array.from({ length: 3 }).map((_, i) => (
                <div
                  className="h-44 shrink-0 basis-full animate-pulse rounded-2xl border border-text/10 bg-card/20 shadow-xl shadow-bg/20 sm:basis-[calc((100%-2rem)/3)]"
                  key={i}
                />
              ))}
            </div>
          ) : null}

          {!loading && error ? (
            <p
              aria-live="polite"
              className="rounded-2xl border border-text/10 bg-card/20 p-5 text-sm text-text/60 shadow-xl shadow-bg/20"
              role="status"
            >
              {error}
            </p>
          ) : null}

          {!loading && !error && items && items.length === 0 ? (
            <p
              className="rounded-2xl border border-text/10 bg-card/20 p-5 text-sm text-text/60 shadow-xl shadow-bg/20"
              role="status"
            >
              No suggestions available right now.
            </p>
          ) : null}

          {!loading && !error && items && items.length > 0 ? (
            <TrendingCarousel items={items} onAnalyze={onAnalyze} />
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

function TrendingCarousel({
  items,
  onAnalyze,
}: {
  items: TrendingRepositoryResponse[]
  onAnalyze: (url: string) => void
}) {
  const trackRef = useRef<HTMLDivElement>(null)
  const [active, setActive] = useState(0)
  const [atStart, setAtStart] = useState(true)
  const [atEnd, setAtEnd] = useState(false)

  const sync = useCallback(() => {
    const track = trackRef.current
    if (!track || track.scrollWidth === 0) return
    const stride = track.scrollWidth / items.length
    setActive(Math.round(track.scrollLeft / stride))
    setAtStart(track.scrollLeft <= 1)
    setAtEnd(track.scrollLeft >= track.scrollWidth - track.clientWidth - 1)
  }, [items.length])

  useEffect(() => {
    sync()
  }, [sync])

  const scrollToIndex = (index: number) => {
    const track = trackRef.current
    if (!track) return
    const stride = track.scrollWidth / items.length
    track.scrollTo?.({ left: index * stride, behavior: 'smooth' })
  }

  const step = (direction: 1 | -1) => {
    const track = trackRef.current
    if (!track) return
    const stride = track.scrollWidth / items.length
    track.scrollBy?.({ left: direction * stride, behavior: 'smooth' })
  }

  return (
    <div>
      <div className="relative">
        <div
          className="flex snap-x snap-mandatory gap-4 overflow-x-auto pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
          onScroll={sync}
          ref={trackRef}
        >
          {items.map((item) => (
            <div
              className="shrink-0 basis-full snap-start sm:basis-[calc((100%-2rem)/3)]"
              key={item.fullName}
            >
              <TrendingRepoCard item={item} onAnalyze={onAnalyze} />
            </div>
          ))}
        </div>

        {!atStart ? (
          <CarouselArrow direction="left" onClick={() => step(-1)} />
        ) : null}
        {!atEnd ? <CarouselArrow direction="right" onClick={() => step(1)} /> : null}
      </div>

      <div className="mt-4 flex items-center justify-center gap-1.5">
        {items.map((item, i) => (
          <button
            aria-current={active === i}
            aria-label={`Go to repository ${i + 1}`}
            className={`h-1.5 rounded-full transition-all duration-300 ${
              active === i ? 'w-6 bg-primary' : 'w-1.5 bg-text/25 hover:bg-text/40'
            }`}
            key={item.fullName}
            onClick={() => scrollToIndex(i)}
            type="button"
          />
        ))}
      </div>
    </div>
  )
}

function CarouselArrow({ direction, onClick }: { direction: 'left' | 'right'; onClick: () => void }) {
  const isLeft = direction === 'left'
  return (
    <button
      aria-label={isLeft ? 'Previous repositories' : 'Next repositories'}
      className={`absolute top-1/2 z-10 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full border border-text/10 bg-bg/80 text-text/70 shadow-lg shadow-bg/30 backdrop-blur transition hover:bg-bg hover:text-text ${
        isLeft ? 'left-1' : 'right-1'
      }`}
      onClick={onClick}
      type="button"
    >
      <svg aria-hidden="true" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
        <path d={isLeft ? 'M15 19l-7-7 7-7' : 'M9 5l7 7-7 7'} strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </button>
  )
}

function TrendingRepoCard({ item, onAnalyze }: { item: TrendingRepositoryResponse; onAnalyze: (url: string) => void }) {
  return (
    <div className="flex h-full flex-col rounded-2xl border border-text/10 bg-card/20 p-5 shadow-xl shadow-bg/20">
      <p className="truncate font-semibold text-text">{item.fullName}</p>
      {item.analyzed ? (
        <span className="mt-2 inline-flex w-fit items-center gap-1.5 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-widest text-emerald-400">
          <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
          Analyzed
        </span>
      ) : null}
      {item.description ? (
        <p className="mt-2 line-clamp-2 text-sm leading-6 text-text/60">{item.description}</p>
      ) : null}
      <div className="mt-auto flex items-center justify-between gap-2 pt-4">
        <p className="text-xs text-text/50">
          ★ {compactFormatter.format(item.stars)} · ⑂ {compactFormatter.format(item.forks)}
        </p>
        {item.language ? (
          <span className="shrink-0 rounded-full border border-primary/20 bg-primary/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-widest text-primary/80">
            {item.language}
          </span>
        ) : null}
      </div>
      <button
        aria-label={`Analyze ${item.fullName}`}
        className="mt-4 inline-flex min-h-9 items-center justify-center self-center rounded-2xl border border-primary/20 bg-primary/10 px-6 py-2 text-sm font-medium text-primary transition hover:bg-primary/20"
        onClick={() => onAnalyze(item.repositoryUrl)}
        type="button"
      >
        Analyze
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
