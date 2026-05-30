import type { LeaderboardOverviewResponse } from '../../types/api'

const compactFormatter = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })
const numberFormatter = new Intl.NumberFormat('en-US')

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** exponent).toFixed(exponent === 0 ? 0 : 1)} ${units[exponent]}`
}

interface OverviewMetricsSectionProps {
  status: 'loading' | 'ok' | 'error'
  data: LeaderboardOverviewResponse | null
  error: string | null
}

export function OverviewMetricsSection({ status, data, error }: OverviewMetricsSectionProps) {
  if (status === 'loading') {
    return (
      <div className="mt-6">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div className="rounded-2xl border border-text/10 bg-card/20 p-5 animate-pulse" key={i}>
              <div className="h-4 w-24 rounded bg-text/10 mb-3" />
              <div className="h-8 w-16 rounded bg-text/10" />
            </div>
          ))}
        </div>
        <div className="mt-4 rounded-2xl border border-text/10 bg-card/20 p-5 animate-pulse">
          <div className="h-4 w-32 rounded bg-text/10 mb-3" />
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <div className="h-3 w-full rounded bg-text/10" key={i} />
            ))}
          </div>
        </div>
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className="mt-6 rounded-2xl border border-rose-400/20 bg-rose-400/10 p-4 text-sm text-rose-300">
        {error ?? 'Could not load overview metrics.'}
      </div>
    )
  }

  if (!data) return null

  return (
    <div className="mt-6">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <article className="rounded-2xl border border-text/10 bg-card/20 p-5 shadow-2xl shadow-bg/20">
          <p className="text-sm text-text/60">Total repos</p>
          <p className="mt-3 text-3xl font-semibold text-text">{numberFormatter.format(data.totalRepos)}</p>
          <p className="mt-2 text-sm text-text/50">{numberFormatter.format(data.totalAnalyses)} total analyses</p>
        </article>

        <article className="rounded-2xl border border-text/10 bg-card/20 p-5 shadow-2xl shadow-bg/20">
          <p className="text-sm text-text/60">Total tokens</p>
          <p className="mt-3 text-3xl font-semibold text-text">{compactFormatter.format(data.totalTokens)}</p>
          <p className="mt-2 text-sm text-text/50">{numberFormatter.format(data.totalTokens)} tracked</p>
        </article>

        <article className="rounded-2xl border border-text/10 bg-card/20 p-5 shadow-2xl shadow-bg/20">
          <p className="text-sm text-text/60">Total repository size</p>
          <p className="mt-3 text-3xl font-semibold text-text">{formatBytes(data.totalBytes)}</p>
          <p className="mt-2 text-sm text-text/50">across all analyses</p>
        </article>
      </div>

      <div className="mt-4 rounded-2xl border border-text/10 bg-card/20 p-5 shadow-2xl shadow-bg/20">
        <p className="text-sm font-semibold text-text/80 mb-3">Breakdown by mode</p>
        {data.costsByMode.length === 0 ? (
          <p className="text-sm text-text/50">No cost data available.</p>
        ) : (
          <div className="space-y-2">
            {data.costsByMode.map((entry) => (
              <div
                className="flex items-center justify-between rounded-xl bg-bg/40 px-4 py-2 text-sm"
                key={entry.mode}
              >
                <span className="capitalize font-medium text-text">{entry.mode}</span>
                <div className="text-right">
                  <span className="text-primary font-semibold">${entry.totalCost}</span>
                  <span className="ml-2 text-text/50">· {numberFormatter.format(entry.analysisCount)} analyses</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
