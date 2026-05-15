import { useEffect, useMemo, useState } from 'react'

import { ApiError, getLeaderboard } from '../services/api'
import type { LeaderboardEntryResponse, LeaderboardPageResponse } from '../types/api'

const categories = [
  { id: 'most-expensive', label: 'Most expensive', metric: 'Cost' },
  { id: 'cheapest', label: 'Cheapest', metric: 'Cost' },
  { id: 'largest', label: 'Largest repositories', metric: 'Size' },
  { id: 'most-analyzed', label: 'Most analyzed', metric: 'Runs' },
  { id: 'highest-token-count', label: 'Highest token count', metric: 'Tokens' },
  { id: 'best-cost-efficiency', label: 'Best cost efficiency', metric: '$ / 1M tokens' },
] as const

const modes = ['raw', 'assisted', 'agentic'] as const
const providers = ['openai', 'anthropic', 'google', 'deepseek'] as const

const numberFormatter = new Intl.NumberFormat('en-US')
const compactFormatter = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })
const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 })
const dateFormatter = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' })

export function LeaderboardsPage() {
  const [category, setCategory] = useState(() => getInitialCategory())
  const [mode, setMode] = useState('raw')
  const [provider, setProvider] = useState('')
  const [model, setModel] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<{ key: string; data: LeaderboardPageResponse | null; error: string | null } | null>(null)

  const activeCategory = useMemo(() => categories.find((candidate) => candidate.id === category) ?? categories[0], [category])
  const usesCostFilters = ['most-expensive', 'cheapest', 'best-cost-efficiency'].includes(category)
  const requestKey = `${category}:${page}:${usesCostFilters ? mode : ''}:${usesCostFilters ? provider : ''}:${usesCostFilters ? model : ''}`
  const leaderboard = result?.key === requestKey ? result.data : null
  const error = result?.key === requestKey ? result.error : null
  const loading = result?.key !== requestKey

  useEffect(() => {
    let active = true
    getLeaderboard({
      category,
      page,
      mode: usesCostFilters ? mode : undefined,
      provider: usesCostFilters ? provider : undefined,
      model: usesCostFilters ? model : undefined,
    })
      .then((data) => {
        if (active) setResult({ key: requestKey, data, error: null })
      })
      .catch((reason: unknown) => {
        if (active) setResult({ key: requestKey, data: null, error: toUserMessage(reason) })
      })

    return () => {
      active = false
    }
  }, [category, mode, model, page, provider, requestKey, usesCostFilters])

  function selectCategory(nextCategory: string) {
    setCategory(nextCategory)
    setPage(0)
    window.history.replaceState(null, '', `/leaderboards?category=${nextCategory}`)
  }

  return (
    <section className="mx-auto max-w-6xl px-6 py-10">
      <div className="mb-8 rounded-[2rem] border border-text/10 bg-card/20 p-8 shadow-2xl shadow-bg/20">
        <p className="mb-3 text-sm font-semibold uppercase tracking-[0.3em] text-primary">Community benchmarks</p>
        <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr] lg:items-end">
          <div>
            <h1 className="text-4xl font-semibold tracking-tight text-text sm:text-5xl">Public repository leaderboards</h1>
            <p className="mt-4 max-w-2xl text-base leading-7 text-text/80">
              Browse the repositories TokenMeter has analyzed by AI generation cost, token footprint, size and popularity.
            </p>
          </div>
          <div className="rounded-2xl border border-primary/20 bg-primary/10 p-5 text-sm text-primary">
            Rankings update from persisted analyses automatically. Open any entry to inspect the full public cost report.
          </div>
        </div>
      </div>

      <div className="mb-6 flex flex-wrap gap-2">
        {categories.map((item) => (
          <button
            className={`rounded-full border px-4 py-2 text-sm transition ${
              item.id === category ? 'border-primary bg-primary text-bg' : 'border-text/10 bg-card/20 text-text/80 hover:border-primary/60 hover:text-text'
            }`}
            key={item.id}
            onClick={() => selectCategory(item.id)}
            type="button"
          >
            {item.label}
          </button>
        ))}
      </div>

      {usesCostFilters ? (
        <div className="mb-6 grid gap-3 rounded-2xl border border-text/10 bg-card/60 p-4 sm:grid-cols-3">
          <label className="text-xs font-semibold uppercase tracking-[0.2em] text-text/60">
            Mode
            <select className="mt-2 w-full rounded-xl border border-text/10 bg-bg px-3 py-2 text-sm text-text" value={mode} onChange={(event) => { setMode(event.target.value); setPage(0) }}>
              {modes.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <label className="text-xs font-semibold uppercase tracking-[0.2em] text-text/60">
            Provider
            <select className="mt-2 w-full rounded-xl border border-text/10 bg-bg px-3 py-2 text-sm text-text" value={provider} onChange={(event) => { setProvider(event.target.value); setPage(0) }}>
              <option value="">All providers</option>
              {providers.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <label className="text-xs font-semibold uppercase tracking-[0.2em] text-text/60">
            Model
            <input className="mt-2 w-full rounded-xl border border-text/10 bg-bg px-3 py-2 text-sm text-text placeholder:text-text/40" placeholder="e.g. gpt-4o" value={model} onChange={(event) => { setModel(event.target.value); setPage(0) }} />
          </label>
        </div>
      ) : null}

      <div className="overflow-hidden rounded-3xl border border-text/10 bg-card/70">
        <div className="flex items-center justify-between border-b border-text/10 px-5 py-4">
          <div>
            <h2 className="text-lg font-semibold text-text">{activeCategory.label}</h2>
            <p className="text-sm text-text/60">{leaderboard ? `${numberFormatter.format(leaderboard.totalElements)} ranked entries` : 'Loading rankings'}</p>
          </div>
          <span className="hidden rounded-full bg-text/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-text/80 sm:inline">{activeCategory.metric}</span>
        </div>

        {error ? <div className="p-6 text-sm text-rose-300">{error}</div> : null}
        {loading ? <div className="p-6 text-sm text-text/80">Loading leaderboards…</div> : null}
        {!loading && !error && leaderboard?.entries.length === 0 ? <div className="p-6 text-sm text-text/80">No repositories match this leaderboard yet.</div> : null}

        {!loading && !error && leaderboard?.entries.length ? (
          <div className="divide-y divide-text/10">
            {leaderboard.entries.map((entry) => (
              <LeaderboardRow entry={entry} category={category} key={`${entry.analysisId}-${entry.rank}`} />
            ))}
          </div>
        ) : null}
      </div>

      <div className="mt-6 flex items-center justify-between text-sm text-text/60">
        <button className="rounded-full border border-text/10 px-4 py-2 disabled:opacity-40" disabled={page === 0 || loading} onClick={() => setPage((value) => Math.max(0, value - 1))} type="button">Previous</button>
        <span>Page {page + 1}{leaderboard?.totalPages ? ` of ${leaderboard.totalPages}` : ''}</span>
        <button className="rounded-full border border-text/10 px-4 py-2 disabled:opacity-40" disabled={loading || !leaderboard || page + 1 >= leaderboard.totalPages} onClick={() => setPage((value) => value + 1)} type="button">Next</button>
      </div>
    </section>
  )
}

function LeaderboardRow({ entry, category }: { entry: LeaderboardEntryResponse; category: string }) {
  return (
    <a className="grid gap-4 px-5 py-5 transition hover:bg-card/20 sm:grid-cols-[4rem_1fr_auto] sm:items-center" href={`/analysis/${entry.analysisId}`}>
      <div className="text-3xl font-semibold text-primary/80">#{entry.rank}</div>
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-lg font-semibold text-text">{entry.owner}/{entry.name}</h3>
          {entry.mode ? <span className="rounded-full bg-text/10 px-2 py-1 text-xs text-text/80">{entry.mode}</span> : null}
          {entry.provider ? <span className="rounded-full bg-text/10 px-2 py-1 text-xs text-text/80">{entry.provider}</span> : null}
        </div>
        <p className="mt-1 break-all text-sm text-text/60">{entry.repositoryUrl}</p>
        <p className="mt-2 text-xs text-text/50">Analyzed {dateFormatter.format(new Date(entry.analyzedAt))} · {compactFormatter.format(entry.totalTokens)} tokens · {compactFormatter.format(entry.totalFiles)} files</p>
      </div>
      <div className="text-left sm:text-right">
        <div className="text-xl font-semibold text-text">{mainMetric(entry, category)}</div>
        <div className="mt-1 text-xs text-text/60">{entry.model ?? secondaryMetric(entry)}</div>
      </div>
    </a>
  )
}

function mainMetric(entry: LeaderboardEntryResponse, category: string) {
  if (category === 'largest') return formatBytes(entry.totalBytes)
  if (category === 'most-analyzed') return `${numberFormatter.format(entry.analysisCount)} runs`
  if (category === 'highest-token-count') return `${compactFormatter.format(entry.totalTokens)} tokens`
  if (category === 'best-cost-efficiency') return currencyFormatter.format(entry.costPerMillionTokens ?? 0)
  return currencyFormatter.format(entry.totalCost ?? 0)
}

function secondaryMetric(entry: LeaderboardEntryResponse) {
  return `${formatBytes(entry.totalBytes)} · ${compactFormatter.format(entry.totalLines)} lines`
}

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** exponent).toFixed(exponent === 0 ? 0 : 1)} ${units[exponent]}`
}

function getInitialCategory() {
  const category = new URLSearchParams(window.location.search).get('category')
  return categories.some((candidate) => candidate.id === category) ? category! : 'most-expensive'
}

function toUserMessage(reason: unknown) {
  if (reason instanceof ApiError) return reason.message
  if (reason instanceof Error) return reason.message
  return 'TokenMeter could not load the public leaderboards.'
}
