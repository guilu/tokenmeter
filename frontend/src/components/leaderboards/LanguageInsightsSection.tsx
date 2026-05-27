import type { LeaderboardLanguagesResponse } from '../../types/api'

const compactFormatter = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })
const numberFormatter = new Intl.NumberFormat('en-US')

interface ShareBarProps {
  percent: number
}

function ShareBar({ percent }: ShareBarProps) {
  const width = Math.max(2, Math.min(100, percent))
  return (
    <svg aria-hidden="true" className="h-3 w-20" viewBox="0 0 80 12">
      <rect fill="currentColor" className="text-text/10" width="80" height="12" rx="6" />
      <rect fill="currentColor" className="text-primary" width={String(width * 0.8)} height="12" rx="6" />
    </svg>
  )
}

interface LanguageInsightsSectionProps {
  status: 'loading' | 'ok' | 'error'
  data: LeaderboardLanguagesResponse | null
  error: string | null
}

export function LanguageInsightsSection({ status, data, error }: LanguageInsightsSectionProps) {
  if (status === 'loading') {
    return (
      <div className="mt-6 overflow-hidden rounded-3xl border border-text/10 bg-card/70">
        <div className="border-b border-text/10 px-5 py-4">
          <div className="h-5 w-48 rounded bg-text/10 animate-pulse" />
        </div>
        <div className="divide-y divide-text/10">
          {Array.from({ length: 5 }).map((_, i) => (
            <div className="grid grid-cols-4 gap-4 px-5 py-3 animate-pulse" key={i}>
              <div className="h-4 w-24 rounded bg-text/10" />
              <div className="h-4 w-16 rounded bg-text/10" />
              <div className="h-4 w-8 rounded bg-text/10" />
              <div className="h-4 w-20 rounded bg-text/10" />
            </div>
          ))}
        </div>
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className="mt-6 rounded-2xl border border-rose-400/20 bg-rose-400/10 p-4 text-sm text-rose-300">
        {error ?? 'Could not load language insights.'}
      </div>
    )
  }

  if (!data) return null

  return (
    <div className="mt-6 overflow-hidden rounded-3xl border border-text/10 bg-card/70">
      <div className="flex items-center justify-between border-b border-text/10 px-5 py-4">
        <div>
          <h2 className="text-lg font-semibold text-text">Top languages by token volume</h2>
          <p className="text-sm text-text/60">
            {numberFormatter.format(data.totalTokensAllLanguages)} total tokens across all languages
          </p>
        </div>
      </div>

      {data.languages.length === 0 ? (
        <div className="p-6 text-sm text-text/80">No language data available.</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-text/10 text-sm">
            <thead className="bg-card/20 text-left text-text/60">
              <tr>
                <th className="px-5 py-3 font-medium">Language</th>
                <th className="px-5 py-3 font-medium text-right">Tokens</th>
                <th className="px-5 py-3 font-medium text-right">Repos</th>
                <th className="px-5 py-3 font-medium">Share</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-text/10 text-text/80">
              {data.languages.map((entry) => (
                <tr className="transition hover:bg-card/20" key={entry.language}>
                  <td className="px-5 py-3 font-medium text-text">{entry.language}</td>
                  <td className="px-5 py-3 text-right tabular-nums">
                    {compactFormatter.format(entry.totalTokens)}
                  </td>
                  <td className="px-5 py-3 text-right tabular-nums">
                    {numberFormatter.format(entry.repoCount)}
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2">
                      <ShareBar percent={parseFloat(entry.sharePercent)} />
                      <span className="tabular-nums text-text/70">{entry.sharePercent}%</span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
