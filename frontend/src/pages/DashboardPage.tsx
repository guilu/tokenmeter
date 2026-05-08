import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'

import { analyzeRepository, ApiError, DEFAULT_REPOSITORY_URL } from '../services/api'
import type { RepositoryAnalysisCostEstimateResponse, RepositoryAnalysisResponse } from '../types/api'

const numberFormatter = new Intl.NumberFormat('en-US')
const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 4,
})

export function DashboardPage() {
  const [repositoryUrl, setRepositoryUrl] = useState(DEFAULT_REPOSITORY_URL)
  const [analysis, setAnalysis] = useState<RepositoryAnalysisResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmedUrl = repositoryUrl.trim()

    if (!isValidGitHubUrl(trimmedUrl)) {
      setError('Enter a valid public GitHub repository URL, e.g. https://github.com/guilu/tokenmeter')
      return
    }

    setLoading(true)
    setError(null)

    try {
      const result = await analyzeRepository(trimmedUrl)
      setAnalysis(result)
      window.history.pushState(null, '', `#results-${result.id}`)
    } catch (reason) {
      setError(toUserMessage(reason))
    } finally {
      setLoading(false)
    }
  }

  if (analysis) {
    return <ResultsView analysis={analysis} onNewAnalysis={() => setAnalysis(null)} />
  }

  return (
    <section className="relative overflow-hidden" id="overview">
      <div className="absolute inset-x-0 top-0 -z-10 h-96 bg-[radial-gradient(circle_at_top,_rgba(34,211,238,0.18),_transparent_55%)]" />
      <div className="mx-auto grid max-w-6xl gap-12 px-6 py-16 lg:grid-cols-[1.1fr_0.9fr] lg:py-24">
        <div>
          <p className="mb-4 inline-flex rounded-full border border-cyan-400/20 bg-cyan-400/10 px-3 py-1 text-sm text-cyan-200">
            Repository cost intelligence
          </p>
          <h1 className="text-4xl font-semibold tracking-tight text-white sm:text-6xl">
            Estimate what a repository would cost to generate with AI.
          </h1>
          <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-400">
            Submit a public GitHub repository and TokenMeter will scan files, count tokens, and calculate raw, assisted, and agentic generation costs per AI model.
          </p>

          <form
            className="mt-10 rounded-3xl border border-white/10 bg-white/[0.04] p-3 shadow-2xl shadow-cyan-950/30 backdrop-blur"
            onSubmit={handleSubmit}
          >
            <label className="sr-only" htmlFor="repository-url">
              Repository URL
            </label>
            <div className="flex flex-col gap-3 sm:flex-row">
              <input
                className="min-h-12 flex-1 rounded-2xl border border-white/10 bg-slate-950/80 px-4 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-cyan-300/70 focus:ring-4 focus:ring-cyan-400/10"
                disabled={loading}
                id="repository-url"
                inputMode="url"
                onChange={(event) => setRepositoryUrl(event.target.value)}
                placeholder="https://github.com/user/repo"
                type="url"
                value={repositoryUrl}
              />
              <button
                className="min-h-12 rounded-2xl bg-cyan-300 px-6 text-sm font-semibold text-slate-950 transition hover:bg-cyan-200 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={loading}
                type="submit"
              >
                {loading ? 'Analyzing…' : 'Analyze repository'}
              </button>
            </div>
            {error ? (
              <p className="mt-3 rounded-2xl border border-red-400/20 bg-red-400/10 px-4 py-3 text-sm text-red-200">
                {error}
              </p>
            ) : null}
          </form>

          {loading ? <LoadingState repositoryUrl={repositoryUrl} /> : null}
        </div>

        <aside className="rounded-3xl border border-white/10 bg-slate-900/60 p-6 shadow-2xl shadow-black/30">
          <p className="text-sm text-slate-400">Workflow simulation</p>
          <div className="mt-6 space-y-4">
            {[
              ['Raw mode', 'Final repository output only — the absolute floor.'],
              ['Assisted mode', 'Human-in-the-loop iterations, prompts, context and corrections.'],
              ['Agentic mode', 'Autonomous loops with heavier reasoning and tool overhead.'],
            ].map(([title, copy]) => (
              <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4" key={title}>
                <p className="font-medium text-white">{title}</p>
                <p className="mt-1 text-sm leading-6 text-slate-400">{copy}</p>
              </div>
            ))}
          </div>
        </aside>
      </div>
    </section>
  )
}

function LoadingState({ repositoryUrl }: { repositoryUrl: string }) {
  return (
    <div className="mt-6 rounded-3xl border border-cyan-300/20 bg-cyan-300/10 p-5 text-sm text-cyan-100">
      <div className="flex items-center gap-3">
        <span className="h-3 w-3 animate-pulse rounded-full bg-cyan-300" />
        <span>Cloning, scanning and pricing {repositoryUrl.trim()}…</span>
      </div>
    </div>
  )
}

function ResultsView({ analysis, onNewAnalysis }: { analysis: RepositoryAnalysisResponse; onNewAnalysis: () => void }) {
  const cheapestEstimate = useMemo(() => cheapest(analysis.costEstimates), [analysis.costEstimates])
  const providers = new Set(analysis.costEstimates.map((estimate) => estimate.provider))

  return (
    <section className="mx-auto max-w-6xl px-6 py-16" id="results">
      <button className="text-sm text-cyan-200 transition hover:text-cyan-100" onClick={onNewAnalysis} type="button">
        ← Analyze another repository
      </button>
      <div className="mt-6 max-w-3xl">
        <p className="mb-4 inline-flex rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1 text-sm text-emerald-200">
          Analysis complete
        </p>
        <h1 className="text-4xl font-semibold tracking-tight text-white sm:text-5xl">{analysis.repositoryUrl}</h1>
        <p className="mt-4 text-slate-400">Analysis id: {analysis.id}</p>
      </div>

      <div className="mt-10 grid gap-4 md:grid-cols-3" id="metrics">
        <MetricCard label="Tokens tracked" value={numberFormatter.format(analysis.metrics.totalTokens)} hint={`${numberFormatter.format(analysis.metrics.totalFiles)} files analyzed`} />
        <MetricCard label="Lowest estimate" value={cheapestEstimate ? currencyFormatter.format(cheapestEstimate.totalCost) : '$0.0000'} hint={cheapestEstimate ? `${cheapestEstimate.provider} · ${cheapestEstimate.model} · ${cheapestEstimate.mode}` : 'No estimates available'} />
        <MetricCard label="Providers" value={numberFormatter.format(providers.size)} hint={`${analysis.costEstimates.length} model/mode estimates`} />
      </div>

      <div className="mt-8 rounded-3xl border border-white/10 bg-white/[0.03] p-6">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-sm text-slate-400">Cost breakdown</p>
            <h2 className="mt-1 text-2xl font-semibold text-white">AI generation estimates</h2>
          </div>
        </div>
        <div className="mt-6 overflow-hidden rounded-2xl border border-white/10">
          <table className="min-w-full divide-y divide-white/10 text-sm">
            <thead className="bg-white/[0.04] text-left text-slate-400">
              <tr>
                <th className="px-4 py-3 font-medium">Provider</th>
                <th className="px-4 py-3 font-medium">Model</th>
                <th className="px-4 py-3 font-medium">Mode</th>
                <th className="px-4 py-3 text-right font-medium">Output tokens</th>
                <th className="px-4 py-3 text-right font-medium">Total cost</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10 text-slate-200">
              {analysis.costEstimates.slice(0, 12).map((estimate) => (
                <tr key={`${estimate.provider}-${estimate.model}-${estimate.mode}`}>
                  <td className="px-4 py-3 capitalize">{estimate.provider}</td>
                  <td className="px-4 py-3">{estimate.model}</td>
                  <td className="px-4 py-3 capitalize">{estimate.mode}</td>
                  <td className="px-4 py-3 text-right">{numberFormatter.format(estimate.estimatedOutputTokens)}</td>
                  <td className="px-4 py-3 text-right font-medium text-white">{currencyFormatter.format(estimate.totalCost)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}

function MetricCard({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <article className="rounded-2xl border border-white/10 bg-white/[0.03] p-6 shadow-2xl shadow-black/20">
      <p className="text-sm text-slate-400">{label}</p>
      <p className="mt-3 text-3xl font-semibold text-white">{value}</p>
      <p className="mt-2 text-sm text-slate-500">{hint}</p>
    </article>
  )
}

function cheapest(estimates: RepositoryAnalysisCostEstimateResponse[]) {
  return estimates.reduce<RepositoryAnalysisCostEstimateResponse | null>((best, estimate) => {
    if (best === null) return estimate
    return estimate.totalCost < best.totalCost ? estimate : best
  }, null)
}

function isValidGitHubUrl(value: string) {
  try {
    const url = new URL(value)
    const segments = url.pathname.split('/').filter(Boolean)
    return url.protocol === 'https:' && url.hostname === 'github.com' && segments.length >= 2
  } catch {
    return false
  }
}

function toUserMessage(reason: unknown) {
  if (reason instanceof ApiError) {
    if (reason.code === 'INVALID_URL') return 'That repository URL is not valid. Use a public GitHub repository URL.'
    if (reason.code === 'REPOSITORY_NOT_ACCESSIBLE') return 'TokenMeter could not access that repository. Check that it is public and exists.'
    if (reason.code === 'CLONE_TIMEOUT') return 'The repository took too long to clone. Try a smaller repository.'
    return reason.message
  }
  return 'Something went wrong while analyzing the repository. Try again.'
}
