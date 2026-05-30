import type { LeaderboardEntryResponse, LeaderboardPageResponse } from '../../types/api'

const categories = [
  { id: 'most-expensive', label: 'Most expensive', metric: 'Cost' },
  { id: 'cheapest', label: 'Cheapest', metric: 'Cost' },
  { id: 'largest', label: 'Largest repositories', metric: 'Size' },
  { id: 'most-analyzed', label: 'Most analyzed', metric: 'Runs' },
  { id: 'highest-token-count', label: 'Highest token count', metric: 'Tokens' },
  { id: 'best-cost-efficiency', label: 'Best cost efficiency', metric: '$ / 1M tokens' },
] as const

const numberFormatter = new Intl.NumberFormat('en-US')
const compactFormatter = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })
const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 })
const dateFormatter = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' })

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** exponent).toFixed(exponent === 0 ? 0 : 1)} ${units[exponent]}`
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

interface RankingRowProps {
  entry: LeaderboardEntryResponse
  category: string
}

function RankingRow({ entry, category }: RankingRowProps) {
  return (
    <a
      className="grid gap-4 px-5 py-5 transition hover:bg-card/40 sm:grid-cols-[4rem_1fr_auto] sm:items-center"
      href={`/analysis/${entry.analysisId}`}
    >
      <div className="text-3xl font-semibold text-primary/80">#{entry.rank}</div>
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-lg font-semibold text-text">{entry.owner}/{entry.name}</h3>
          {entry.mode ? (
            <span className="rounded-full bg-text/10 px-2 py-1 text-xs text-text/80">{entry.mode}</span>
          ) : null}
          {entry.provider ? (
            <span className="rounded-full bg-text/10 px-2 py-1 text-xs text-text/80">{entry.provider}</span>
          ) : null}
          {entry.dominantLanguage ? (
            <span className="rounded-full bg-primary/10 px-2 py-1 text-xs text-primary/80">{entry.dominantLanguage}</span>
          ) : null}
        </div>
        <p className="mt-1 break-all text-sm text-text/60">{entry.repositoryUrl}</p>
        <p className="mt-2 text-xs text-text/50">
          Analyzed {dateFormatter.format(new Date(entry.analyzedAt))} · {compactFormatter.format(entry.totalTokens)} tokens · {compactFormatter.format(entry.totalFiles)} files
        </p>
      </div>
      <div className="text-left sm:text-right">
        <div className="text-xl font-semibold text-text">{mainMetric(entry, category)}</div>
        <div className="mt-1 text-xs text-text/60">{entry.model ?? secondaryMetric(entry)}</div>
      </div>
    </a>
  )
}

interface LeaderboardRankingsSectionProps {
  status: 'loading' | 'ok' | 'error'
  data: LeaderboardPageResponse | null
  error: string | null
  category: string
  page: number
  onCategoryChange: (category: string) => void
  onPageChange: (page: number) => void
}

export function LeaderboardRankingsSection({
  status,
  data,
  error,
  category,
  page,
  onCategoryChange,
  onPageChange,
}: LeaderboardRankingsSectionProps) {
  const activeCategory = categories.find((c) => c.id === category) ?? categories[0]

  return (
    <div className="mt-6 overflow-hidden rounded-3xl border border-text/10 bg-card/20">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-text/10 bg-card/70 px-5 py-4">
        <div>
          <h2 className="text-lg font-semibold text-text">{activeCategory.label}</h2>
          <p className="text-sm text-text/60">
            {data ? `${numberFormatter.format(data.totalElements)} ranked entries` : 'Loading rankings'}
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm text-text/60">
          Sort by
          <div className="relative">
            <select
              className="appearance-none rounded-xl border border-text/10 bg-bg px-3 py-2 pr-8 text-sm text-text"
              value={category}
              onChange={(event) => onCategoryChange(event.target.value)}
            >
              {categories.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.label}
                </option>
              ))}
            </select>
            <div className="pointer-events-none absolute inset-y-0 right-2.5 flex items-center">
              <svg
                className="h-4 w-4 text-text/50"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                viewBox="0 0 24 24"
              >
                <path d="M19 9l-7 7-7-7" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
          </div>
        </label>
      </div>

      {error ? <div className="p-6 text-sm text-rose-300">{error}</div> : null}

      {status === 'loading' ? (
        <div className="divide-y divide-text/10">
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              className="grid animate-pulse gap-4 px-5 py-5 sm:grid-cols-[4rem_1fr_auto] sm:items-center"
              key={i}
            >
              <div className="h-8 w-10 rounded-lg bg-text/10" />
              <div className="space-y-2">
                <div className="h-4 w-48 rounded bg-text/10" />
                <div className="h-3 w-32 rounded bg-text/10" />
              </div>
              <div className="h-6 w-24 rounded-full bg-text/10" />
            </div>
          ))}
        </div>
      ) : null}

      {status === 'ok' && !error && data?.entries.length === 0 ? (
        <div className="p-6 text-sm text-text/80">No repositories match this leaderboard yet.</div>
      ) : null}

      {status === 'ok' && !error && data?.entries.length ? (
        <div className="divide-y divide-text/10">
          {data.entries.map((entry) => (
            <RankingRow entry={entry} category={category} key={`${entry.analysisId}-${entry.rank}`} />
          ))}
        </div>
      ) : null}

      <div className="flex items-center justify-between border-t border-text/10 bg-card/70 px-5 py-4 text-sm text-text/60">
        <button
          className="rounded-full border border-text/10 px-4 py-2 disabled:opacity-40"
          disabled={page === 0 || status === 'loading'}
          onClick={() => onPageChange(Math.max(0, page - 1))}
          type="button"
        >
          Previous
        </button>
        <span>
          Page {page + 1}
          {data?.totalPages ? ` of ${data.totalPages}` : ''}
        </span>
        <button
          className="rounded-full border border-text/10 px-4 py-2 disabled:opacity-40"
          disabled={status === 'loading' || !data || page + 1 >= data.totalPages}
          onClick={() => onPageChange(page + 1)}
          type="button"
        >
          Next
        </button>
      </div>
    </div>
  )
}
