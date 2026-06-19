import { useCallback, useEffect, useMemo, useState } from 'react'

import { ProviderIcon } from '../components/ProviderIcon'
import { ApiError, getPricing, refreshPricing } from '../services/api'
import type { PricingModelResponse, PricingResponse } from '../types/api'
import { formatRelativeTime } from '../utils/relativeTime'
import type { PriceSortColumn, SortDirection } from '../utils/modelSort'
import { sortModels } from '../utils/modelSort'

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 })
const absoluteDate = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' })

const providerBadgeCls: Record<string, string> = {
  openai: 'border-primary/20 bg-primary/10 text-primary',
  anthropic: 'border-secondary/20 bg-secondary/10 text-secondary',
  google: 'border-accent/20 bg-accent/10 text-accent',
  deepseek: 'border-text/20 bg-text/10 text-text/70',
  mistral: 'border-orange-500/30 bg-orange-500/10 text-orange-400',
  alibaba: 'border-cyan-500/30 bg-cyan-500/10 text-cyan-400',
  xai: 'border-fuchsia-500/30 bg-fuchsia-500/10 text-fuchsia-400',
}

const sourceBadgeCls: Record<PricingModelResponse['source'], string> = {
  REMOTE: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-400',
  FALLBACK: 'border-amber-500/30 bg-amber-500/10 text-amber-400',
  OVERRIDE: 'border-violet-500/30 bg-violet-500/10 text-violet-400',
}

const modes = [
  { key: 'raw', label: 'Raw', outputMult: 1, inputMult: 0, description: 'Direct regeneration. Output tokens only, no prompt overhead.' },
  { key: 'assisted', label: 'Assisted', outputMult: 5, inputMult: 1, description: 'Human-in-the-loop workflow. Iterative prompts included.' },
  { key: 'agentic', label: 'Agentic', outputMult: 20, inputMult: 4, description: 'Autonomous loop. Full multi-step agent overhead.' },
]

export function ModelsPage() {
  const [response, setResponse] = useState<PricingResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showEconomics, setShowEconomics] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [refreshError, setRefreshError] = useState<string | null>(null)
  const [refreshSummary, setRefreshSummary] = useState<string | null>(null)
  const [sort, setSort] = useState<{ column: PriceSortColumn; direction: SortDirection } | null>(
    null,
  )

  const toggleSort = useCallback((column: PriceSortColumn) => {
    setSort((current) =>
      current?.column === column
        ? { column, direction: current.direction === 'asc' ? 'desc' : 'asc' }
        : { column, direction: 'asc' },
    )
  }, [])

  const loadPricing = useCallback(
    () =>
      getPricing()
        .then(setResponse)
        .catch(() => setError('Could not load pricing data.')),
    [],
  )

  useEffect(() => {
    void loadPricing()
  }, [loadPricing])

  const handleRefresh = useCallback(async () => {
    if (refreshing) return
    setRefreshing(true)
    setRefreshError(null)
    setRefreshSummary(null)
    try {
      const result = await refreshPricing()
      await loadPricing()
      setRefreshSummary(`Prices updated — ${result.updated} updated, ${result.skipped} skipped.`)
    } catch (err) {
      const message =
        err instanceof ApiError && err.status === 503
          ? 'Could not refresh prices — on-demand refresh is disabled or upstream is unavailable.'
          : 'Could not refresh prices. Please try again.'
      setRefreshError(message)
    } finally {
      setRefreshing(false)
    }
  }, [loadPricing, refreshing])

  const models = response?.models ?? null
  const displayModels = useMemo(
    () => (models && sort ? sortModels(models, sort.column, sort.direction) : models),
    [models, sort],
  )

  return (
    <section className="mx-auto max-w-6xl px-6 py-10 stage-enter">

      {/* Hero */}
      <div className="mb-8 rounded-[2rem] border border-text/10 bg-card/20 p-8 shadow-2xl shadow-bg/20">
        <p className="mb-3 text-sm font-semibold uppercase tracking-[0.3em] text-primary">Supported models</p>
        <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr] lg:items-end">
          <div>
            <h1 className="text-4xl font-semibold tracking-tight text-text sm:text-5xl">Model pricing reference</h1>
            <p className="mt-4 max-w-2xl text-base leading-7 text-text/80">
              Prices per million tokens used in TokenMeter cost estimates. All figures in USD.
            </p>
          </div>
          <div className="rounded-2xl border border-primary/20 bg-primary/10 p-5 text-sm text-primary">
            Base prices come from the public{' '}
            <a
              className="font-semibold underline-offset-2 hover:underline"
              href="https://github.com/BerriAI/litellm/blob/main/model_prices_and_context_window.json"
              rel="noreferrer"
              target="_blank"
            >
              LiteLLM pricing catalogue
            </a>
            , refreshed weekly.
          </div>
        </div>
      </div>

      {/* Generation Economics modes */}
      <button
        aria-expanded={showEconomics}
        className="mb-4 flex items-center gap-2 text-lg font-semibold text-text"
        onClick={() => setShowEconomics((v) => !v)}
        type="button"
      >
        Generation Economics modes
        <svg
          aria-hidden="true"
          className={`h-4 w-4 text-text/50 transition-transform duration-300 ${showEconomics ? 'rotate-180' : ''}`}
          fill="none"
          stroke="currentColor"
          strokeWidth={2}
          viewBox="0 0 24 24"
        >
          <path d="M19 9l-7 7-7-7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      {showEconomics ? (
        <div className="mb-8 grid gap-3 sm:grid-cols-3">
          {modes.map(m => (
            <div className="rounded-2xl border border-text/10 bg-card/20 p-5" key={m.key}>
              <span className="inline-block rounded-full border border-primary/20 bg-primary/10 px-3 py-0.5 text-xs font-semibold uppercase tracking-widest text-primary">
                {m.label}
              </span>
              <p className="mt-3 text-sm leading-6 text-text/70">{m.description}</p>
              <p className="mt-3 font-mono text-xs text-text/50">output ×{m.outputMult} · input ×{m.inputMult}</p>
            </div>
          ))}
        </div>
      ) : (
        <div className="mb-8" />
      )}

      {/* Pricing table */}
      <h2 className="mb-4 text-lg font-semibold text-text">Base prices</h2>

      {/* Freshness banner */}
      <div
        className={`mb-4 rounded-2xl border px-4 py-2 text-sm ${
          response && !response.lastRefreshedAt
            ? 'border-amber-500/30 bg-amber-500/10 text-amber-300'
            : 'border-text/10 bg-card/20 text-text/70'
        }`}
      >
        {!response ? (
          <div className="animate-pulse">
            <div className="h-4 w-72 rounded bg-text/10" />
          </div>
        ) : response.lastRefreshedAt ? (
          <span>
            Last updated <strong className="text-text">{absoluteDate.format(new Date(response.lastRefreshedAt))}</strong>
            {' '}({formatRelativeTime(response.lastRefreshedAt)}) — source: LiteLLM upstream
          </span>
        ) : (
          <span>Showing fallback prices — remote refresh has not yet succeeded.</span>
        )}
      </div>

      {/* On-demand refresh */}
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="text-sm" role="status">
          {refreshError ? (
            <span className="text-rose-400">{refreshError}</span>
          ) : refreshSummary ? (
            <span className="text-emerald-400">{refreshSummary}</span>
          ) : (
            <span className="text-text/50">Prices refresh weekly. Need them now?</span>
          )}
        </div>
        <button
          aria-label="Refresh prices now"
          className="inline-flex items-center justify-center gap-2 rounded-full border border-primary/30 bg-primary/10 px-4 py-2 text-sm font-semibold text-primary transition hover:bg-primary/20 disabled:cursor-not-allowed disabled:opacity-60"
          disabled={refreshing}
          onClick={handleRefresh}
          type="button"
        >
          {refreshing ? (
            <>
              <span className="h-3 w-3 animate-spin rounded-full border-2 border-primary/40 border-t-primary" />
              Refreshing prices…
            </>
          ) : (
            'Refresh prices now'
          )}
        </button>
      </div>

      <div className="overflow-hidden rounded-3xl border border-text/10 bg-card/20">
        <table className="min-w-full text-sm">
          <thead className="border-b border-text/10 bg-card/70 text-left">
            <tr>
              <th className="px-5 py-4 text-xs font-semibold uppercase tracking-[0.2em] text-text/60">Provider</th>
              <th className="px-5 py-4 text-xs font-semibold uppercase tracking-[0.2em] text-text/60">Model</th>
              <SortableHeader
                label="Input / 1M tokens"
                column="input"
                sort={sort}
                onSort={toggleSort}
              />
              <SortableHeader
                label="Output / 1M tokens"
                column="output"
                sort={sort}
                onSort={toggleSort}
              />
              <th className="px-5 py-4 text-xs font-semibold uppercase tracking-[0.2em] text-text/60">Source</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-text/10">
            {error ? (
              <tr><td className="px-5 py-6 text-rose-400" colSpan={5}>{error}</td></tr>
            ) : !models ? (
              Array.from({ length: 4 }).map((_, i) => (
                <tr className="animate-pulse" key={i}>
                  <td className="px-5 py-4"><div className="h-5 w-20 rounded bg-text/10" /></td>
                  <td className="px-5 py-4"><div className="h-5 w-36 rounded bg-text/10" /></td>
                  <td className="px-5 py-4 text-right"><div className="ml-auto h-5 w-16 rounded bg-text/10" /></td>
                  <td className="px-5 py-4 text-right"><div className="ml-auto h-5 w-16 rounded bg-text/10" /></td>
                  <td className="px-5 py-4"><div className="h-5 w-20 rounded bg-text/10" /></td>
                </tr>
              ))
            ) : displayModels!.map(m => (
              <tr className="transition hover:bg-card/40" key={`${m.provider}-${m.model}`}>
                <td className="px-5 py-4">
                  <span className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-0.5 text-xs font-semibold capitalize ${providerBadgeCls[m.provider] ?? 'border-text/20 bg-text/10 text-text/70'}`}>
                    <ProviderIcon provider={m.provider} />
                    {m.provider}
                  </span>
                </td>
                <td className="px-5 py-4 font-medium text-text">{m.model}</td>
                <td className="px-5 py-4 text-right font-mono text-text/80">{currency.format(m.inputTokenPricePerMillion)}</td>
                <td className="px-5 py-4 text-right font-mono text-text/80">{currency.format(m.outputTokenPricePerMillion)}</td>
                <td className="px-5 py-4">
                  <span className={`inline-block rounded-full border px-3 py-0.5 text-xs font-semibold uppercase tracking-widest ${sourceBadgeCls[m.source]}`}>
                    {m.source.toLowerCase()}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

    </section>
  )
}

function SortableHeader({
  label,
  column,
  sort,
  onSort,
}: {
  label: string
  column: PriceSortColumn
  sort: { column: PriceSortColumn; direction: SortDirection } | null
  onSort: (column: PriceSortColumn) => void
}) {
  const active = sort?.column === column
  const direction = active ? sort.direction : null

  return (
    <th className="px-5 py-4 text-right text-xs font-semibold uppercase tracking-[0.2em] text-text/60">
      <button
        aria-label={`Sort by ${label}`}
        aria-sort={active ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'}
        className={`ml-auto flex items-center gap-1 uppercase tracking-[0.2em] transition hover:text-text ${active ? 'text-text' : ''}`}
        onClick={() => onSort(column)}
        type="button"
      >
        {label}
        <span aria-hidden="true" className={`text-[0.65rem] ${active ? 'text-primary' : 'text-text/30'}`}>
          {direction === 'asc' ? '▲' : direction === 'desc' ? '▼' : '↕'}
        </span>
      </button>
    </th>
  )
}
