import { useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import { HeuristicDisclaimer } from '../components/HeuristicDisclaimer'
import { PipelineTimeline } from '../components/PipelineTimeline'
import { PrecisionBadge } from '../components/PrecisionBadge'
import { TrendingSection } from '../components/TrendingSection'
import { useAnalysisJob } from '../hooks/useAnalysisJob'
import { useElapsedSeconds } from '../hooks/useElapsedSeconds'
import { useStalledProgress } from '../hooks/useStalledProgress'
import { ApiError, DEFAULT_REPOSITORY_URL, getAnalysis, submitAnalysis } from '../services/api'
import type {
  RepositoryAnalysisCostEstimateResponse,
  RepositoryAnalysisResponse,
} from '../types/api'
import {
  analysisStages,
  etaFromJob,
  liveStatsFromMetrics,
  loadingDetailFromJob,
  progressFromJob,
  stageIndexFromJob,
} from '../utils/analysisJobProgress'

const numberFormatter = new Intl.NumberFormat('en-US')
const compactNumberFormatter = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })
const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})
const dateFormatter = new Intl.DateTimeFormat('en-US', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const costModes = ['raw', 'assisted', 'agentic'] as const
type CostMode = (typeof costModes)[number]
type ComparisonSort = 'cost' | 'relative' | 'efficiency' | 'model'
type ProviderFilter = 'all' | string

const modeCopy: Record<CostMode, string> = {
  raw: 'Price the final codebase as if the repository appeared in one clean generation pass.',
  assisted: 'Model the real workflow: prompts, reviews, fixes and context loaded by engineers.',
  agentic: 'Estimate autonomous build loops with planning, tool calls, retries and reasoning overhead.',
}

const modeMultiplierLabel: Record<CostMode, string> = {
  raw: '1× output tokens — baseline floor',
  assisted: '5× output + 1× input — workflow overhead',
  agentic: '20× output + 4× input — autonomous loop',
}

const workflowAssumptions: Record<CostMode, { title: string; summary: string; multiplierLabel: string; items: string[] }> = {
  raw: {
    title: 'Raw mode assumptions',
    summary: 'A baseline simulation that prices the repository as final generated output, without collaboration, retries or extra planning context.',
    multiplierLabel: 'Baseline output-only estimate',
    items: [
      'Counts the final repository token footprint as the minimum generation surface.',
      'Assumes a clean one-pass generation with no prompt refinement or correction loops.',
      'Excludes architecture discussion, review feedback and partial rewrites.',
      'Best used as the absolute floor, not as a realistic delivery forecast.',
    ],
  },
  assisted: {
    title: 'Assisted mode assumptions',
    summary: 'A human-in-the-loop simulation that adds the collaboration overhead usually required to turn AI output into working software.',
    multiplierLabel: 'Includes prompt and correction overhead',
    items: [
      'Prompt refinement iterations to steer structure, naming and implementation details.',
      'Human correction loops for bugs, tests, edge cases and review feedback.',
      'Architecture discussions and context sharing before larger changes.',
      'Partial rewrites when generated files need to be reshaped instead of accepted directly.',
      'Additional context overhead from resending files, snippets and constraints.',
    ],
  },
  agentic: {
    title: 'Agentic mode assumptions',
    summary: 'An autonomous-workflow simulation that models heavier reasoning, tools and retries across a longer-running AI build loop.',
    multiplierLabel: 'Includes autonomous loop overhead',
    items: [
      'Autonomous planning before implementation and between milestones.',
      'Retry loops when commands fail, tests break or generated changes need repair.',
      'Tool usage overhead from reading files, running commands and inspecting outputs.',
      'Reasoning amplification for decomposition, debugging and validation steps.',
      'Long-running context accumulation as the agent keeps project state active.',
    ],
  },
}

function ShareIcon() {
  return (
    <svg
      aria-hidden="true"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      viewBox="0 0 24 24"
    >
      <circle cx="18" cy="5" r="3" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="6" cy="12" r="3" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="18" cy="19" r="3" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M8.59 13.51l6.83 3.98M15.41 6.51l-6.82 3.98" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function DashboardPage() {
  const [repositoryUrl, setRepositoryUrl] = useState('')
  const [analysis, setAnalysis] = useState<RepositoryAnalysisResponse | null>(null)
  const [routeAnalysisId, setRouteAnalysisId] = useState(() => getAnalysisIdFromLocation())
  const [loading, setLoading] = useState(false)
  const [sharedLoading, setSharedLoading] = useState(() => Boolean(getAnalysisIdFromLocation()))
  const [error, setError] = useState<string | null>(null)
  const [sharedError, setSharedError] = useState<string | null>(null)
  const [showModes, setShowModes] = useState(false)
  const [activeJobId, setActiveJobId] = useState<string | null>(null)

  useEffect(() => {
    function handlePopState() {
      const nextAnalysisId = getAnalysisIdFromLocation()
      setRouteAnalysisId(nextAnalysisId)
      setSharedError(null)
      setSharedLoading(Boolean(nextAnalysisId))
    }

    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  useEffect(() => {
    if (!routeAnalysisId || analysis?.id === routeAnalysisId) return

    getAnalysis(routeAnalysisId)
      .then((result) => {
        setAnalysis(result)
        setRepositoryUrl(result.repositoryUrl)
      })
      .catch((reason: unknown) => setSharedError(toUserMessage(reason)))
      .finally(() => setSharedLoading(false))
  }, [analysis?.id, routeAnalysisId])

  async function triggerAnalysis(url: string) {
    const trimmedUrl = url.trim() || DEFAULT_REPOSITORY_URL

    if (!isValidGitHubUrl(trimmedUrl)) {
      setError('Enter a valid public GitHub repository URL, e.g. https://github.com/guilu/tokenmeter')
      return
    }

    setRepositoryUrl(trimmedUrl)
    setLoading(true)
    setError(null)
    setActiveJobId(null)
    try {
      const accepted = await submitAnalysis(trimmedUrl)
      setActiveJobId(accepted.jobId)
    } catch (reason) {
      setError(toUserMessage(reason))
      setLoading(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await triggerAnalysis(repositoryUrl)
  }

  function handleJobSuccess(analysisId: string) {
    setActiveJobId(null)
    setLoading(false)
    setRouteAnalysisId(analysisId)
    window.history.pushState(null, '', analysisPath(analysisId))
  }

  function handleJobFailure(message: string) {
    setError(message)
    setActiveJobId(null)
    setLoading(false)
  }

  function handleNewAnalysis() {
    setAnalysis(null)
    setRouteAnalysisId(null)
    setSharedError(null)
    setActiveJobId(null)
    window.history.pushState(null, '', '/')
    resetDocumentMetadata()
  }

  if (routeAnalysisId) {
    if (analysis?.id === routeAnalysisId) {
      return <ResultsView analysis={analysis} onNewAnalysis={handleNewAnalysis} />
    }

    return (
      <SharedAnalysisState
        error={sharedError}
        loading={sharedLoading}
        onBack={handleNewAnalysis}
      />
    )
  }

  if (analysis) {
    return <ResultsView analysis={analysis} onNewAnalysis={handleNewAnalysis} />
  }

  return (
    <section className="relative overflow-hidden" id="overview">
      <div
        className="absolute inset-x-0 top-0 -z-10 h-[32rem]"
        style={{ background: 'radial-gradient(circle at top, color-mix(in srgb, var(--tm-primary) 15%, transparent), transparent 55%)' }}
      />
      <div className="mx-auto max-w-4xl px-6 pt-8 pb-4 text-center sm:pt-12 md:pt-14 lg:pt-20">
        <p className="mb-4 inline-flex rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-sm text-primary/80">
          AI repository cost intelligence
        </p>
        <h1 className="text-4xl font-semibold tracking-tight text-text sm:text-6xl">
          AI cost of any GitHub repository.
        </h1>
        <p className="mt-4 text-base leading-7 text-text/60">
          Token footprint and AI generation cost benchmark across raw, assisted and agentic workflows.
        </p>
      </div>

      <div className="mx-auto max-w-4xl px-6 pb-4 sm:pb-5">
        <form
          className="rounded-3xl bg-card/20 p-3 shadow-2xl shadow-bg backdrop-blur"
          onSubmit={handleSubmit}
        >
          <label className="sr-only" htmlFor="repository-url">
            Repository URL
          </label>
          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="flex flex-1 items-center gap-2 rounded-2xl border border-secondary/30 bg-[var(--tm-input)] px-4 transition focus-within:border-primary/50 focus-within:ring-2 focus-within:ring-primary/30">
              <svg aria-hidden="true" className="h-8 w-8 shrink-0 text-text/50" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
              </svg>
              <input
                className="min-h-12 flex-1 bg-transparent text-sm text-text outline-none placeholder:text-text/50"
                disabled={loading}
                id="repository-url"
                inputMode="url"
                onChange={(event) => setRepositoryUrl(event.target.value)}
                placeholder="https://github.com/guilu/tokenmeter"
                type="url"
                value={repositoryUrl}
              />
              {repositoryUrl && !loading ? (
                <button
                  aria-label="Clear repository URL"
                  className="shrink-0 text-text/40 transition hover:text-text/70"
                  onClick={() => setRepositoryUrl('')}
                  type="button"
                >
                  <svg aria-hidden="true" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path d="M6 18L18 6M6 6l12 12" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </button>
              ) : null}
            </div>
            <button
              className="min-h-12 rounded-2xl bg-primary px-6 text-sm font-semibold text-bg transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 sm:min-w-[15rem]"
              disabled={loading}
              type="submit"
            >
              {loading ? 'Simulating…' : 'Simulate generation cost'}
            </button>
          </div>
          {error ? (
            <p
              aria-live="polite"
              className="mt-3 flex items-start gap-2 rounded-2xl border border-red-600/60 bg-red-500/15 px-4 py-3 text-sm font-medium text-red-800 dark:border-red-500/50 dark:text-red-200"
              role="alert"
            >
              <svg aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-red-700 dark:text-red-300" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                <path d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              <span>{error}</span>
            </p>
          ) : null}
        </form>

        {loading ? (
          <LoadingState
            jobId={activeJobId}
            repositoryUrl={repositoryUrl}
            onFailure={handleJobFailure}
            onSuccess={handleJobSuccess}
          />
        ) : null}
      </div>

      {!loading ? <TrendingSection onAnalyze={triggerAnalysis} /> : null}

      <div className={`mx-auto max-w-4xl px-6 ${showModes ? 'pb-6 md:pb-8 lg:pb-20' : 'pb-2'}`}>
        <button
          className="mb-4 flex w-full items-center justify-center gap-2"
          onClick={() => setShowModes((v) => !v)}
          type="button"
        >
          <span className="text-sm font-medium text-text/80">Generation Economics Model</span>
          <svg
            aria-hidden="true"
            className={`h-4 w-4 text-text/50 transition-transform duration-300 ${showModes ? 'rotate-180' : ''}`}
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            viewBox="0 0 24 24"
          >
            <path d="M19 9l-7 7-7-7" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
        {showModes ? (
          <div className="grid gap-4 sm:grid-cols-3">
            {costModes.map((mode) => (
              <div
                className="rounded-2xl border border-text/10 bg-card/20 p-5 shadow-xl shadow-bg/20"
                key={mode}
              >
                <div className="flex items-center justify-between gap-2">
                  <p className="font-semibold text-text capitalize">{mode}</p>
                  {mode === 'assisted' ? (
                    <span className="shrink-0 rounded-full border border-primary/30 bg-primary/20 px-3 py-1 text-xs font-semibold uppercase tracking-widest text-primary/80">
                      Default
                    </span>
                  ) : null}
                </div>
                <p className="mt-2 text-sm leading-6 text-text/60">{modeCopy[mode]}</p>
                <p className="mt-4 text-xs text-text/50">{modeMultiplierLabel[mode]}</p>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </section>
  )
}

function LoadingState({
  jobId,
  repositoryUrl,
  onSuccess,
  onFailure,
}: {
  jobId: string | null
  repositoryUrl: string
  onSuccess: (analysisId: string) => void
  onFailure: (message: string) => void
}) {
  const trimmedRepositoryUrl = repositoryUrl.trim()
  const repositoryLabel = repositoryNameFromUrl(trimmedRepositoryUrl)
  const { job, error } = useAnalysisJob(jobId)

  const activeStage = useMemo(() => stageIndexFromJob(job), [job])
  const progress = useMemo(() => progressFromJob(job), [job])
  const isStalled = useStalledProgress(progress)
  const liveStats = useMemo(() => liveStatsFromMetrics(job?.metrics ?? null), [job?.metrics])

  useEffect(() => {
    if (!job) return
    if (job.status === 'SUCCESS' && job.analysisId) {
      onSuccess(job.analysisId)
      return
    }
    if (job.status === 'FAILED') {
      const message = toUserMessage(
        new ApiError(job.error?.message ?? 'Analysis failed', 0, job.error?.code),
      )
      onFailure(message)
    }
  }, [job, onFailure, onSuccess])

  useEffect(() => {
    if (!error) return
    onFailure(toUserMessage(error))
  }, [error, onFailure])

  const stage = analysisStages[activeStage]
  const phaseLabel = job?.phaseLabel ?? stage.label
  const detail = useMemo(() => loadingDetailFromJob(job, stage.detail), [job, stage.detail])
  const elapsedSeconds = useElapsedSeconds(job?.timestamps.startedAt ?? null)
  const eta = useMemo(() => etaFromJob(job, elapsedSeconds), [job, elapsedSeconds])

  return (
    <div className="relative mt-8 overflow-hidden rounded-3xl border border-primary/20 bg-bg/80 p-5 shadow-2xl shadow-bg">
      <div
        className="absolute inset-0"
        style={{ background: 'radial-gradient(circle at top left, color-mix(in srgb, var(--tm-primary) 15%, transparent), transparent 35%)' }}
      />
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary/70 to-transparent" />

      <div className="relative space-y-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.28em] text-primary/80">AI analysis pipeline</p>
            <h2 className="mt-2 text-xl font-semibold text-text">Scanning {repositoryLabel}</h2>
            <p className="mt-1 text-sm text-text/60">Tracking repository generation economics in real time.</p>
          </div>
          <div className="rounded-2xl border border-primary/20 bg-primary/10 px-4 py-2 text-right">
            <p className="text-2xl font-semibold text-primary">{progress}%</p>
            <p className="text-xs text-primary/70">pipeline progress</p>
            {eta.kind !== 'hidden' ? (
              <p className="mt-1 text-xs text-text/60">{eta.label}</p>
            ) : null}
          </div>
        </div>

        <div className="h-2 overflow-hidden rounded-full bg-text/10">
          <div
            className={`h-full rounded-full bg-gradient-to-r from-primary via-secondary to-accent transition-all duration-[1200ms] ease-out${isStalled ? ' animate-shimmer' : ''}`}
            style={{ width: `${progress}%` }}
          />
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          {liveStats.map((stat) => (
            <div className="rounded-2xl bg-card/20 p-3" key={stat.label}>
              <p className="text-lg font-semibold text-text">{stat.value}</p>
              <p className="mt-1 text-xs text-text/60">{stat.label}</p>
            </div>
          ))}
        </div>

        <PipelineTimeline job={job} />

        <div className="rounded-2xl border border-text/10 bg-bg/20 p-4 font-mono text-xs text-primary/80">
          <p>&gt; pipeline.run --repository {trimmedRepositoryUrl || repositoryLabel}</p>
          <p className="mt-1 text-text/60">&gt; stage.{activeStage + 1}: {phaseLabel.toLowerCase()}...</p>
          <p className="mt-1 text-text/80">&gt; {detail.message}</p>
          {detail.microcopy ? (
            <p className="mt-1 text-text/50">&gt; {detail.microcopy}</p>
          ) : null}
        </div>
      </div>
    </div>
  )
}

function SharedAnalysisState({ error, loading, onBack }: { error: string | null; loading: boolean; onBack: () => void }) {
  return (
    <section className="mx-auto max-w-3xl px-6 py-20">
      <button className="text-sm text-primary/80 transition hover:text-primary" onClick={onBack} type="button">
        ← Back to analyzer
      </button>
      <div className="mt-8 rounded-3xl bg-card/20 p-8 shadow-2xl shadow-bg/20">
        {loading && !error ? (
          <div className="flex items-center gap-3 text-primary">
            <span className="h-3 w-3 animate-pulse rounded-full bg-primary" />
            <span>Loading public analysis…</span>
          </div>
        ) : null}
        {error ? (
          <>
            <p className="text-sm font-medium text-red-700 dark:text-red-300">Analysis not available</p>
            <h1 className="mt-3 text-3xl font-semibold text-text">This public analysis could not be loaded.</h1>
            <p className="mt-3 text-text/60">{error}</p>
          </>
        ) : null}
      </div>
    </section>
  )
}

function ResultsView({ analysis, onNewAnalysis }: { analysis: RepositoryAnalysisResponse; onNewAnalysis: () => void }) {
  const [selectedMode, setSelectedMode] = useState<CostMode>('raw')
  const [comparisonSort, setComparisonSort] = useState<ComparisonSort>('cost')
  const [providerFilter, setProviderFilter] = useState<ProviderFilter>('all')
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle')
  const publicUrl = typeof window === 'undefined' ? analysisPath(analysis.id) : new URL(analysisPath(analysis.id), window.location.origin).toString()
  const selectedOpenGraphImageUrl = openGraphImageUrl(analysis.id, publicUrl, selectedMode)

  useEffect(() => {
    updateDocumentMetadata(analysis, publicUrl)
  }, [analysis, publicUrl])

  const languages = useMemo(() => languageBreakdown(analysis), [analysis])
  const estimatesForMode = useMemo(
    () => analysis.costEstimates.filter((estimate) => estimate.mode === selectedMode),
    [analysis.costEstimates, selectedMode],
  )
  const rawEstimates = useMemo(
    () => analysis.costEstimates.filter((estimate) => estimate.mode === 'raw'),
    [analysis.costEstimates],
  )
  const cheapestEstimate = useMemo(() => cheapest(estimatesForMode), [estimatesForMode])
  const highestEstimate = useMemo(() => highest(estimatesForMode), [estimatesForMode])
  const rawBaselineEstimate = useMemo(() => cheapest(rawEstimates), [rawEstimates])
  const primaryEstimate = cheapestEstimate ?? estimatesForMode[0] ?? null
  const topLanguage = languages[0]
  const providersForMode = useMemo(() => uniqueProviders(estimatesForMode), [estimatesForMode])
  const averageCost = average(estimatesForMode.map((estimate) => estimate.totalCost))

  async function handleCopyPublicUrl() {
    const copied = await copyPublicUrl(publicUrl)
    setCopyState(copied ? 'copied' : 'failed')
    window.setTimeout(() => setCopyState('idle'), 2200)
  }

  return (
    <section className="mx-auto max-w-6xl px-4 py-10 sm:px-6 sm:py-16" id="results">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button className="text-sm text-primary/80 transition hover:text-primary" onClick={onNewAnalysis} type="button">
          ← Analyze another repository
        </button>
        <div className="flex flex-wrap items-center gap-2 print:hidden">
          <button
            className="rounded-2xl border border-text/10 bg-card/20 px-4 py-2 text-sm text-text/80 transition hover:bg-card/40"
            onClick={() => void handleCopyPublicUrl()}
            type="button"
          >
            {copyState === 'copied' ? 'Copied!' : copyState === 'failed' ? 'Copy failed' : 'Copy URL'}
          </button>
          <a
            className="inline-flex items-center gap-2 rounded-2xl border border-primary/20 bg-primary/10 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/20"
            href={selectedOpenGraphImageUrl}
            rel="noreferrer"
            target="_blank"
          >
            <ShareIcon />
            Badge
          </a>
          <a
            className="inline-flex items-center gap-2 rounded-2xl border border-primary/20 bg-primary/10 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/20"
            href={`/api/analyze/${analysis.id}/badge.svg`}
            rel="noreferrer"
            target="_blank"
          >
            <ShareIcon />
            Mini badge
          </a>
          <a
            className="inline-flex items-center gap-2 rounded-2xl border border-primary/20 bg-primary/10 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/20"
            download
            href={`/api/analyze/${analysis.id}/export.md`}
          >
            Markdown
          </a>
          <button
            className="inline-flex items-center gap-2 rounded-2xl border border-primary/20 bg-primary/10 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/20"
            onClick={() => window.print()}
            type="button"
          >
            Export PDF
          </button>
        </div>
      </div>

      <header className="mt-6 rounded-3xl bg-secondary/10 p-5 sm:p-6">
        <p className="mb-4 inline-flex rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-sm text-primary">
          Analysis complete
        </p>
        <h1 className="flex items-center gap-3 text-2xl font-semibold tracking-tight text-text sm:text-4xl">
          <svg aria-hidden="true" className="h-7 w-7 shrink-0 text-text/60 sm:h-9 sm:w-9" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
          </svg>
          <a
            className="break-all transition hover:opacity-80"
            href={analysis.repositoryUrl}
            rel="noopener noreferrer"
            target="_blank"
          >
            {repositoryName(analysis.repositoryUrl)}
          </a>
        </h1>
        <p className="mt-3 text-sm text-text/60">
          Analysis id: {analysis.id} · {dateFormatter.format(new Date(analysis.createdAt))}
        </p>
        {analysis.pricing ? (
          <p className="mt-1 text-sm text-text/60">
            Pricing: {analysis.pricing.primarySource} · captured {dateFormatter.format(new Date(analysis.pricing.capturedAt))}
          </p>
        ) : null}
      </header>

      <ModeSwitch selectedMode={selectedMode} onSelectMode={setSelectedMode} />

      <CostHero
        analysis={analysis}
        highestEstimate={highestEstimate}
        lowestEstimate={cheapestEstimate}
        selectedMode={selectedMode}
        topLanguage={topLanguage}
      />

      <div className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4" id="metrics">
        <MetricCard label="Tokens" value={compactNumberFormatter.format(analysis.metrics.totalTokens)} hint={`${numberFormatter.format(analysis.metrics.totalTokens)} tracked`} />
        <MetricCard label="Files" value={numberFormatter.format(analysis.metrics.totalFiles)} hint={`${numberFormatter.format(analysis.metrics.totalLines)} total lines`} />
        <MetricCard label="Languages" value={numberFormatter.format(languages.length)} hint={`${analysis.metrics.tokenEncoding} encoding`} />
        <MetricCard label="Avg. cost" value={currencyFormatter.format(averageCost)} hint={`${selectedMode} mode across ${estimatesForMode.length} models`} />
      </div>

      <div className="mt-8 grid gap-6 lg:grid-cols-[1.05fr_0.95fr]">
        <Panel eyebrow="Language breakdown" title="Repository composition">
          <BarList
            emptyLabel="No language metrics available."
            items={languages.slice(0, 8).map((language) => ({
              label: language.language,
              value: language.tokens,
              helper: `${numberFormatter.format(language.files)} files · ${numberFormatter.format(language.lines)} lines`,
              percent: percentOf(language.tokens, analysis.metrics.totalTokens),
            }))}
            valueFormatter={(value) => `${compactNumberFormatter.format(value)} tokens`}
          />
        </Panel>

        <Panel eyebrow="AI costs" title={`${capitalize(selectedMode)} mode estimates`}>
          <div className="mb-5 grid gap-3 sm:grid-cols-2">
            <CostSummaryCard label="Lowest" estimate={cheapestEstimate} />
            <CostSummaryCard label="Highest" estimate={highestEstimate} />
          </div>
          <BarList
            emptyLabel="No cost estimates available for this mode."
            items={estimatesForMode
              .slice()
              .sort((left, right) => right.totalCost - left.totalCost)
              .map((estimate) => ({
                label: estimate.model,
                value: estimate.totalCost,
                helper: estimate.provider,
                percent: percentOf(estimate.totalCost, highestEstimate?.totalCost ?? 0),
              }))}
            valueFormatter={(value) => currencyFormatter.format(value)}
          />
        </Panel>
      </div>

      <WorkflowAssumptions selectedMode={selectedMode} estimate={primaryEstimate} rawBaselineEstimate={rawBaselineEstimate} />

      <EngineeringEffortPanel estimates={estimatesForMode} selectedMode={selectedMode} />

      {estimatesForMode.length > 0 ? (
        <div className="mt-8">
          <HeuristicDisclaimer estimates={estimatesForMode} />
        </div>
      ) : null}

      <ModelComparison
        estimates={estimatesForMode}
        providerFilter={providerFilter}
        providers={providersForMode}
        selectedMode={selectedMode}
        sortBy={comparisonSort}
        onFilterProvider={setProviderFilter}
        onSort={setComparisonSort}
      />

      <div className="mt-8 rounded-3xl bg-card/20 p-4 sm:p-6">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <p className="text-sm text-text/60">Cost breakdown</p>
            <h2 className="mt-1 text-2xl font-semibold text-text">AI generation estimates</h2>
          </div>
          <p className="rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-sm text-primary">
            {capitalize(selectedMode)} mode
          </p>
        </div>
        <div className="mt-6 overflow-hidden rounded-2xl">
          <table className="min-w-full divide-y divide-text/10 text-sm">
            <thead className="bg-card/20 text-left text-text/60">
              <tr>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">Provider</th>
                <th className="px-4 py-3 font-medium">Model</th>
                <th className="px-4 py-3 font-medium">Mode</th>
                <th className="hidden px-4 py-3 text-right font-medium sm:table-cell">Output tokens</th>
                <th className="px-4 py-3 text-right font-medium">Total cost</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-text/10 text-text/80">
              {estimatesForMode.map((estimate) => (
                <tr key={`${estimate.provider}-${estimate.model}-${estimate.mode}`}>
                  <td className="hidden px-4 py-3 capitalize sm:table-cell">{estimate.provider}</td>
                  <td className="px-4 py-3">{estimate.model}</td>
                  <td className="px-4 py-3 capitalize">{estimate.mode}</td>
                  <td className="hidden px-4 py-3 text-right sm:table-cell">{numberFormatter.format(estimate.estimatedOutputTokens)}</td>
                  <td className="px-4 py-3 text-right font-medium text-text">{currencyFormatter.format(estimate.totalCost)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}

function EngineeringEffortPanel({ estimates, selectedMode }: { estimates: RepositoryAnalysisCostEstimateResponse[]; selectedMode: CostMode }) {
  const representativeEstimate = cheapest(estimates) ?? estimates[0] ?? null
  const highestEffortEstimate = estimates.reduce<RepositoryAnalysisCostEstimateResponse | null>((best, estimate) => {
    if (best === null) return estimate
    return estimate.engineeringEffort.seniorEngineerHours > best.engineeringEffort.seniorEngineerHours ? estimate : best
  }, null)
  const averageHours = average(estimates.map((estimate) => estimate.engineeringEffort.seniorEngineerHours))
  const assumptions = representativeEstimate?.engineeringEffort.assumptions

  return (
    <section className="mt-8 rounded-3xl border border-secondary/20 bg-secondary/[0.04] p-5 shadow-2xl shadow-bg/20 sm:p-6">
      <div className="grid gap-6 lg:grid-cols-[0.95fr_1.05fr] lg:items-start">
        <div>
          <p className="text-sm text-secondary/80">Engineering effort equivalence</p>
          <h2 className="mt-1 text-2xl font-semibold text-text">Human-readable scale for {selectedMode} mode</h2>
          <p className="mt-3 text-sm leading-6 text-text/60">
            TokenMeter translates token and workflow estimates into senior-engineering time, so cost numbers have a practical delivery-scale reference instead of feeling like abstract cents.
          </p>
          {representativeEstimate ? (
            <div className="mt-5 rounded-2xl border border-secondary/20 bg-secondary/10 p-5">
              <p className="text-xs uppercase tracking-[0.2em] text-secondary/70">Lowest-cost model equivalence</p>
              <p className="mt-3 text-3xl font-semibold text-text">{representativeEstimate.engineeringEffort.summary}</p>
              <p className="mt-2 text-sm text-secondary/80">
                {representativeEstimate.provider} · {representativeEstimate.model}
              </p>
            </div>
          ) : null}
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <AssumptionMetric label="Average senior hours" value={`${formatDecimal(averageHours, 1)} h`} />
          <AssumptionMetric label="Max manual effort" value={highestEffortEstimate ? highestEffortEstimate.engineeringEffort.manualImplementationEffort : 'No estimate'} />
          <AssumptionMetric label="Engineering day" value={assumptions ? `${formatDecimal(assumptions.hoursPerEngineeringDay, 1)} h/day` : 'Configurable'} />
          <AssumptionMetric label="Mode multiplier" value={assumptions ? `${formatDecimal(assumptions.modeComplexityMultiplier, 2)}×` : 'Mode-aware'} />
        </div>
      </div>
    </section>
  )
}

function ModelComparison({
  estimates,
  providerFilter,
  providers,
  selectedMode,
  sortBy,
  onFilterProvider,
  onSort,
}: {
  estimates: RepositoryAnalysisCostEstimateResponse[]
  providerFilter: ProviderFilter
  providers: string[]
  selectedMode: CostMode
  sortBy: ComparisonSort
  onFilterProvider: (provider: ProviderFilter) => void
  onSort: (sort: ComparisonSort) => void
}) {
  const cheapestEstimate = cheapest(estimates)
  const highestEstimate = highest(estimates)
  const comparisonRows = useMemo(() => {
    const baselineCost = cheapestEstimate?.totalCost ?? 0
    const maxCost = highestEstimate?.totalCost ?? 0

    return estimates
      .filter((estimate) => providerFilter === 'all' || estimate.provider === providerFilter)
      .map((estimate) => ({
        estimate,
        relativeCost: baselineCost > 0 ? estimate.totalCost / baselineCost : 1,
        costPercent: percentOf(estimate.totalCost, maxCost),
        efficiencyScore: maxCost > 0 ? 1 - estimate.totalCost / maxCost : 1,
        tier: modelTier(estimate, baselineCost, maxCost),
        note: modelComparisonNote(estimate, baselineCost, maxCost),
      }))
      .sort((left, right) => {
        if (sortBy === 'model') return left.estimate.model.localeCompare(right.estimate.model)
        if (sortBy === 'relative') return left.relativeCost - right.relativeCost
        if (sortBy === 'efficiency') return right.efficiencyScore - left.efficiencyScore
        return left.estimate.totalCost - right.estimate.totalCost
      })
  }, [cheapestEstimate, estimates, highestEstimate, providerFilter, sortBy])

  return (
    <section className="mt-8 rounded-3xl bg-card/20 p-5 shadow-2xl shadow-bg/20 sm:p-6">
      <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-start">
        <div>
          <p className="text-sm text-text/60">Model benchmark</p>
          <h2 className="mt-1 text-2xl font-semibold text-text">AI model comparison</h2>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-text/60">
            Compare {selectedMode} generation estimates side-by-side by provider, cost, relative efficiency and quality tier.
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="grid gap-1 text-sm text-text/60">
            Provider
            <div className="relative">
              <select
                className="w-full appearance-none rounded-2xl border border-text/10 bg-bg px-3 py-2 pr-8 text-sm text-text outline-none transition focus:border-primary/60"
                onChange={(event) => onFilterProvider(event.target.value)}
                value={providerFilter}
              >
                <option value="all">All providers</option>
                {providers.map((provider) => (
                  <option key={provider} value={provider}>
                    {capitalize(provider)}
                  </option>
                ))}
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-2.5 flex items-center">
                <svg className="h-4 w-4 text-text/50" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24"><path d="M19 9l-7 7-7-7" strokeLinecap="round" strokeLinejoin="round" /></svg>
              </div>
            </div>
          </label>
          <label className="grid gap-1 text-sm text-text/60">
            Sort by
            <div className="relative">
              <select
                className="w-full appearance-none rounded-2xl border border-text/10 bg-bg px-3 py-2 pr-8 text-sm text-text outline-none transition focus:border-primary/60"
                onChange={(event) => onSort(event.target.value as ComparisonSort)}
                value={sortBy}
              >
                <option value="cost">Lowest cost</option>
                <option value="relative">Relative cost</option>
                <option value="efficiency">Efficiency</option>
                <option value="model">Model name</option>
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-2.5 flex items-center">
                <svg className="h-4 w-4 text-text/50" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24"><path d="M19 9l-7 7-7-7" strokeLinecap="round" strokeLinejoin="round" /></svg>
              </div>
            </div>
          </label>
        </div>
      </div>

      <div className="mt-6 grid gap-3 lg:hidden">
        {comparisonRows.map((row) => (
          <ModelComparisonCard key={`${row.estimate.provider}-${row.estimate.model}`} row={row} />
        ))}
      </div>

      <div className="mt-6 hidden overflow-hidden rounded-2xl lg:block">
        <table className="min-w-full divide-y divide-text/10 text-sm">
          <thead className="bg-card/20 text-left text-text/60">
            <tr>
              <th className="px-4 py-3 font-medium">Model</th>
              <th className="px-4 py-3 font-medium">Provider</th>
              <th className="px-4 py-3 text-right font-medium">Estimated cost</th>
              <th className="px-4 py-3 font-medium">Relative cost</th>
              <th className="px-4 py-3 font-medium">Efficiency tier</th>
              <th className="px-4 py-3 font-medium">Notes</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-text/10 text-text/80">
            {comparisonRows.map((row) => (
              <tr className="transition hover:bg-card/20" key={`${row.estimate.provider}-${row.estimate.model}`}>
                <td className="px-4 py-3 font-medium text-text">
                  <span className="flex items-center gap-2">
                    {row.estimate.model}
                    <PrecisionBadge precision={row.estimate.precision ?? undefined} />
                  </span>
                </td>
                <td className="px-4 py-3 capitalize text-text/80">{row.estimate.provider}</td>
                <td className="px-4 py-3 text-right font-medium text-text">{currencyFormatter.format(row.estimate.totalCost)}</td>
                <td className="px-4 py-3">
                  <RelativeCostBar percent={row.costPercent} label={`${row.relativeCost.toFixed(1)}×`} />
                </td>
                <td className="px-4 py-3"><TierBadge tier={row.tier} /></td>
                <td className="px-4 py-3 text-text/60">{row.note}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

type ModelComparisonRow = {
  estimate: RepositoryAnalysisCostEstimateResponse
  relativeCost: number
  costPercent: number
  efficiencyScore: number
  tier: string
  note: string
}

function ModelComparisonCard({ row }: { row: ModelComparisonRow }) {
  return (
    <article className="rounded-2xl bg-bg/45 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <span className="flex items-center gap-2">
            <p className="truncate font-medium text-text" title={row.estimate.model}>{row.estimate.model}</p>
            <PrecisionBadge precision={row.estimate.precision ?? undefined} />
          </span>
          <p className="mt-1 text-sm capitalize text-text/50">{row.estimate.provider}</p>
        </div>
        <p className="shrink-0 text-lg font-semibold text-text">{currencyFormatter.format(row.estimate.totalCost)}</p>
      </div>
      <div className="mt-4">
        <RelativeCostBar percent={row.costPercent} label={`${row.relativeCost.toFixed(1)}× vs cheapest`} />
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <TierBadge tier={row.tier} />
        <span className="text-sm text-text/60">{row.note}</span>
      </div>
    </article>
  )
}

function RelativeCostBar({ percent, label }: { percent: number; label: string }) {
  return (
    <div>
      <div className="mb-2 flex items-center justify-between gap-3 text-xs text-text/60">
        <span>Relative cost</span>
        <span className="font-medium text-primary">{label}</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-text/10">
        <div className="h-full rounded-full bg-gradient-to-r from-secondary via-primary to-accent" style={{ width: `${Math.max(4, Math.min(100, percent))}%` }} />
      </div>
    </div>
  )
}

function TierBadge({ tier }: { tier: string }) {
  const tone = tier === 'Cheapest' ? 'secondary' : tier === 'Premium' || tier === 'High reasoning' ? 'accent' : tier === 'Experimental' ? 'secondary' : 'primary'
  const className =
    tone === 'secondary'
      ? 'border-secondary/20 bg-secondary/10 text-secondary'
      : tone === 'accent'
        ? 'border-accent/20 bg-accent/10 text-accent'
        : 'border-primary/20 bg-primary/10 text-primary'

  return <span className={`rounded-full border px-3 py-1 text-xs font-medium ${className}`}>{tier}</span>
}

function CostHero({
  analysis,
  highestEstimate,
  lowestEstimate,
  selectedMode,
  topLanguage,
}: {
  analysis: RepositoryAnalysisResponse
  highestEstimate: RepositoryAnalysisCostEstimateResponse | null
  lowestEstimate: RepositoryAnalysisCostEstimateResponse | null
  selectedMode: CostMode
  topLanguage: ReturnType<typeof languageBreakdown>[number] | undefined
}) {
  const repositoryLabel = repositoryName(analysis.repositoryUrl)

  return (
    <section className="relative mt-8 overflow-hidden rounded-[2rem] border border-primary/20 bg-bg/80 p-6 shadow-2xl shadow-bg sm:p-8">
      <div
        className="absolute inset-0 print:hidden"
        style={{ background: 'radial-gradient(circle at top left, color-mix(in srgb, var(--tm-primary) 18%, transparent), transparent 38%), radial-gradient(circle at bottom right, color-mix(in srgb, var(--tm-secondary) 14%, transparent), transparent 36%)' }}
      />
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary/80 to-transparent print:hidden" />

      <div className="relative">
        <p className="inline-flex rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-sm text-primary">
          Estimated generation cost range
        </p>
        <div
          className="mt-5 grid gap-4 transition-all duration-500 sm:grid-cols-2"
          key={`${selectedMode}-${lowestEstimate?.provider ?? 'none'}-${highestEstimate?.provider ?? 'none'}`}
        >
          <div className="relative min-w-0 rounded-2xl bg-card/20 p-5 pr-20">
            <CostRangeBadge label="Min" />
            <p className="text-5xl font-semibold tracking-tight text-text sm:text-6xl">
              {lowestEstimate ? currencyFormatter.format(lowestEstimate.totalCost) : '—'}
            </p>
            <p className="mt-3 truncate text-sm text-text/60">
              {lowestEstimate ? `${lowestEstimate.provider} · ${lowestEstimate.model} · ${selectedMode} workflow mode` : 'No estimate'}
            </p>
          </div>
          <div className="relative min-w-0 rounded-2xl bg-card/20 p-5 pr-20">
            <CostRangeBadge label="Max" />
            <p className="text-5xl font-semibold tracking-tight text-text sm:text-6xl">
              {highestEstimate ? currencyFormatter.format(highestEstimate.totalCost) : '—'}
            </p>
            <p className="mt-3 truncate text-sm text-text/60">
              {highestEstimate ? `${highestEstimate.provider} · ${highestEstimate.model} · ${selectedMode} workflow mode` : 'No estimate'}
            </p>
          </div>
        </div>

        <p className="mt-4 max-w-3xl text-sm leading-6 text-text/60">
          TokenMeter estimates what it would cost to regenerate {repositoryLabel} with AI, including repository size,
          token footprint and workflow overhead for the selected mode.
        </p>

        <div className="mt-6 grid gap-3 sm:grid-cols-4">
          <HeroMeta label="Repository" value={repositoryLabel} />
          <HeroMeta label="Total tokens" value={compactNumberFormatter.format(analysis.metrics.totalTokens)} />
          <HeroMeta label="Files · languages" value={`${numberFormatter.format(analysis.metrics.totalFiles)} · ${numberFormatter.format(Object.keys(analysis.metrics.languages).length)}`} />
          <HeroMeta label="Top language" value={topLanguage ? topLanguage.language : 'Unknown'} />
        </div>
      </div>
    </section>
  )
}

function CostRangeBadge({ label }: { label: 'Min' | 'Max' }) {
  return (
    <span className="absolute right-4 top-4 rounded-full border border-primary/30 bg-primary/20 px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em] text-primary shadow-lg shadow-bg/20 backdrop-blur">
      {label}
    </span>
  )
}

function HeroMeta({ label, value }: { label: string; value: string }) {
  return (
    <article className="min-w-0 rounded-2xl bg-card/20 p-4">
      <p className="text-xs uppercase tracking-[0.2em] text-text/50">{label}</p>
      <p className="mt-2 truncate text-lg font-semibold text-text" title={value}>
        {value}
      </p>
    </article>
  )
}

function WorkflowAssumptions({
  selectedMode,
  estimate,
  rawBaselineEstimate,
}: {
  selectedMode: CostMode
  estimate: RepositoryAnalysisCostEstimateResponse | null
  rawBaselineEstimate: RepositoryAnalysisCostEstimateResponse | null
}) {
  const assumptions = workflowAssumptions[selectedMode]
  const multiplier = estimate && rawBaselineEstimate && rawBaselineEstimate.totalCost > 0 ? estimate.totalCost / rawBaselineEstimate.totalCost : null

  return (
    <section className="mt-8 rounded-3xl bg-card/20 p-5 shadow-2xl shadow-bg/20 sm:p-6">
      <div className="grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
        <div>
          <p className="text-sm text-text/60">Workflow assumptions</p>
          <h2 className="mt-1 text-2xl font-semibold text-text">{assumptions.title}</h2>
          <p className="mt-3 text-sm leading-6 text-text/60">{assumptions.summary}</p>
          <div className="mt-5 rounded-2xl border border-primary/20 bg-primary/10 p-4">
            <p className="text-xs uppercase tracking-[0.2em] text-primary/70">Heuristic simulation</p>
            <p className="mt-2 text-sm leading-6 text-text">
              These estimates are directional, not invoices. They expose the assumptions TokenMeter applies so Raw, Assisted
              and Agentic modes can be compared transparently.
            </p>
          </div>
        </div>

        <div className="grid gap-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <AssumptionMetric label="Selected mode" value={`${capitalize(selectedMode)} workflow`} />
            <AssumptionMetric label="Cost multiplier" value={multiplier ? `${multiplier.toFixed(1)}× vs raw floor` : assumptions.multiplierLabel} />
          </div>
          <ul className="grid gap-2">
            {assumptions.items.map((item) => (
              <li className="flex gap-3 rounded-2xl bg-bg/45 p-3 text-sm leading-6 text-text/80" key={item}>
                <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />
                <span>{item}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}

function AssumptionMetric({ label, value }: { label: string; value: string }) {
  return (
    <article className="rounded-2xl bg-card/20 p-4">
      <p className="text-xs uppercase tracking-[0.2em] text-text/50">{label}</p>
      <p className="mt-2 text-lg font-semibold text-text">{value}</p>
    </article>
  )
}

function ModeSwitch({ selectedMode, onSelectMode }: { selectedMode: CostMode; onSelectMode: (mode: CostMode) => void }) {
  return (
    <div className="mt-6 rounded-2xl border border-text/10 bg-bg/60 p-1.5">
      <div className="grid grid-cols-3 gap-1.5">
        {costModes.map((mode) => {
          const active = selectedMode === mode
          return (
            <button
              className={`rounded-xl px-4 py-3 text-base font-semibold capitalize transition ${
                active ? 'bg-primary text-bg shadow-lg shadow-primary/20' : 'text-text/80 hover:bg-card/40 hover:text-text'
              }`}
              key={mode}
              onClick={() => onSelectMode(mode)}
              type="button"
            >
              {mode}
            </button>
          )
        })}
      </div>
    </div>
  )
}

function MetricCard({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <article className="rounded-2xl bg-card/20 p-5 shadow-2xl shadow-bg/20 sm:p-6">
      <p className="text-sm text-text/60">{label}</p>
      <p className="mt-3 text-3xl font-semibold text-text">{value}</p>
      <p className="mt-2 text-sm text-text/50">{hint}</p>
    </article>
  )
}

function Panel({ eyebrow, title, children }: { eyebrow: string; title: string; children: ReactNode }) {
  return (
    <section className="rounded-3xl bg-card/20 p-5 shadow-2xl shadow-bg/20 sm:p-6">
      <p className="text-sm text-text/60">{eyebrow}</p>
      <h2 className="mt-1 text-2xl font-semibold text-text">{title}</h2>
      <div className="mt-6">{children}</div>
    </section>
  )
}

function BarList({
  emptyLabel,
  items,
  valueFormatter,
}: {
  emptyLabel: string
  items: Array<{ label: string; value: number; helper: string; percent: number }>
  valueFormatter: (value: number) => string
}) {
  if (items.length === 0) {
    return <p className="rounded-2xl border border-dashed border-text/10 p-6 text-sm text-text/60">{emptyLabel}</p>
  }

  return (
    <div className="space-y-4">
      {items.map((item) => (
        <div key={`${item.label}-${item.helper}`}>
          <div className="mb-2 flex items-start justify-between gap-3 text-sm">
            <div className="min-w-0">
              <p className="truncate font-medium text-text" title={item.label}>
                {item.label}
              </p>
              <p className="truncate text-text/50" title={item.helper}>
                {item.helper}
              </p>
            </div>
            <p className="shrink-0 text-right font-medium text-primary">{valueFormatter(item.value)}</p>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-text/10">
            <div
              className="h-full rounded-full bg-gradient-to-r from-primary to-secondary"
              style={{ width: `${Math.max(2, Math.min(100, item.percent))}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  )
}

function CostSummaryCard({ label, estimate }: { label: string; estimate: RepositoryAnalysisCostEstimateResponse | null }) {
  return (
    <article className="rounded-2xl bg-bg/50 p-4">
      <p className="text-sm text-text/60">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-text">
        {estimate ? currencyFormatter.format(estimate.totalCost) : '$0.00'}
      </p>
      <p className="mt-1 truncate text-sm text-text/50" title={estimate ? `${estimate.provider} · ${estimate.model}` : undefined}>
        {estimate ? `${estimate.provider} · ${estimate.model}` : 'No estimates available'}
      </p>
    </article>
  )
}

function languageBreakdown(analysis: RepositoryAnalysisResponse) {
  return Object.values(analysis.metrics.languages).sort((left, right) => right.tokens - left.tokens)
}

function getAnalysisIdFromLocation() {
  const match = window.location.pathname.match(/^\/analysis\/([^/]+)\/?$/)
  if (match) return decodeURIComponent(match[1])

  const searchParams = new URLSearchParams(window.location.search)
  return searchParams.get('analysis')
}

function analysisPath(analysisId: string) {
  return `/analysis/${encodeURIComponent(analysisId)}`
}

async function copyPublicUrl(publicUrl: string) {
  if (navigator.clipboard?.writeText && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(publicUrl)
      return true
    } catch {
      // Fall back to the legacy selection path below.
    }
  }

  const input = document.createElement('input')
  input.value = publicUrl
  input.setAttribute('readonly', '')
  input.style.position = 'fixed'
  input.style.left = '-9999px'
  input.style.top = '0'
  document.body.appendChild(input)
  input.focus()
  input.select()
  input.setSelectionRange(0, input.value.length)

  try {
    return document.execCommand('copy')
  } finally {
    document.body.removeChild(input)
  }
}

function updateDocumentMetadata(analysis: RepositoryAnalysisResponse, publicUrl: string) {
  const title = `TokenMeter analysis for ${repositoryName(analysis.repositoryUrl)}`
  const rawEstimates = analysis.costEstimates.filter((estimate) => estimate.mode === 'raw')
  const lowestRawEstimate = cheapest(rawEstimates) ?? cheapest(analysis.costEstimates)
  const highestRawEstimate = highest(rawEstimates) ?? highest(analysis.costEstimates)
  const costSummary = lowestRawEstimate && highestRawEstimate ? `${costRangeLabel(lowestRawEstimate, highestRawEstimate)} across supported models` : 'AI generation cost benchmark'
  const description = `${numberFormatter.format(analysis.metrics.totalTokens)} tokens, ${numberFormatter.format(analysis.metrics.totalFiles)} files and ${numberFormatter.format(analysis.metrics.totalLines)} lines analyzed. ${costSummary}.`
  const imageUrl = openGraphImageUrl(analysis.id, publicUrl, lowestRawEstimate?.mode ?? 'raw')

  document.title = title
  setMeta('description', description)
  setMeta('og:title', title, 'property')
  setMeta('og:description', description, 'property')
  setMeta('og:type', 'website', 'property')
  setMeta('og:url', publicUrl, 'property')
  setMeta('og:image', imageUrl, 'property')
  setMeta('og:image:secure_url', imageUrl, 'property')
  setMeta('og:image:type', 'image/png', 'property')
  setMeta('og:image:width', '1200', 'property')
  setMeta('og:image:height', '630', 'property')
  setMeta('twitter:card', 'summary_large_image')
  setMeta('twitter:title', title)
  setMeta('twitter:description', description)
  setMeta('twitter:image', imageUrl)
}

function resetDocumentMetadata() {
  document.title = 'TokenMeter — AI repository cost intelligence'
  setMeta('description', 'Simulate the cost of generating public GitHub repositories with modern AI models and workflow modes.')
  setMeta('og:image', '/tokenmeter-logo.png', 'property')
  setMeta('twitter:image', '/tokenmeter-logo.png')
}

function currentTheme(): 'light' | 'dark' {
  if (typeof document === 'undefined') return 'dark'
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light'
}

function openGraphImageUrl(analysisId: string, publicUrl: string, mode: CostMode) {
  const publicUrlOrigin = new URL(publicUrl).origin
  const theme = currentTheme()
  return new URL(
    `/api/analyze/${encodeURIComponent(analysisId)}/og-image.png?mode=${mode}&theme=${theme}&v=range`,
    publicUrlOrigin,
  ).toString()
}

function setMeta(key: string, content: string, attribute: 'name' | 'property' = 'name') {
  let element = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)

  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(attribute, key)
    document.head.appendChild(element)
  }

  element.content = content
}

function repositoryName(repositoryUrl: string) {
  try {
    const url = new URL(repositoryUrl)
    return url.pathname.replace(/^\//, '') || repositoryUrl
  } catch {
    return repositoryUrl
  }
}

function repositoryNameFromUrl(repositoryUrl: string) {
  if (!repositoryUrl) return 'repository'

  try {
    const url = new URL(repositoryUrl)
    return url.pathname.replace(/^\//, '') || repositoryUrl
  } catch {
    return repositoryUrl
  }
}

function costRangeLabel(
  lowestEstimate: RepositoryAnalysisCostEstimateResponse | null,
  highestEstimate: RepositoryAnalysisCostEstimateResponse | null,
) {
  if (!lowestEstimate || !highestEstimate) return '$0.00'
  const lowest = currencyFormatter.format(lowestEstimate.totalCost)
  const highest = currencyFormatter.format(highestEstimate.totalCost)
  return lowest === highest ? lowest : `${lowest} – ${highest}`
}

function uniqueProviders(estimates: RepositoryAnalysisCostEstimateResponse[]) {
  return Array.from(new Set(estimates.map((estimate) => estimate.provider))).sort((left, right) => left.localeCompare(right))
}

function modelTier(estimate: RepositoryAnalysisCostEstimateResponse, baselineCost: number, maxCost: number) {
  const model = estimate.model.toLowerCase()
  const provider = estimate.provider.toLowerCase()
  const relativeCost = baselineCost > 0 ? estimate.totalCost / baselineCost : 1
  const premiumThreshold = maxCost * 0.82

  if (relativeCost <= 1.05) return 'Cheapest'
  if (model.includes('reason') || model.includes('opus') || model.includes('o1') || model.includes('o3')) return 'High reasoning'
  if (model.includes('preview') || model.includes('experimental') || provider.includes('xai')) return 'Experimental'
  if (estimate.totalCost >= premiumThreshold) return 'Premium'
  return 'Balanced'
}

function modelComparisonNote(estimate: RepositoryAnalysisCostEstimateResponse, baselineCost: number, maxCost: number) {
  const tier = modelTier(estimate, baselineCost, maxCost)
  if (tier === 'Cheapest') return 'Lowest simulated cost for this workflow.'
  if (tier === 'High reasoning') return 'Higher reasoning profile; useful for complex repositories.'
  if (tier === 'Premium') return 'Higher cost option, likely best reserved for quality-sensitive work.'
  if (tier === 'Experimental') return 'Useful benchmark candidate; validate quality before relying on it.'
  return 'Middle-ground cost profile for routine generation workflows.'
}

function cheapest(estimates: RepositoryAnalysisCostEstimateResponse[]) {
  return estimates.reduce<RepositoryAnalysisCostEstimateResponse | null>((best, estimate) => {
    if (best === null) return estimate
    return estimate.totalCost < best.totalCost ? estimate : best
  }, null)
}

function highest(estimates: RepositoryAnalysisCostEstimateResponse[]) {
  return estimates.reduce<RepositoryAnalysisCostEstimateResponse | null>((best, estimate) => {
    if (best === null) return estimate
    return estimate.totalCost > best.totalCost ? estimate : best
  }, null)
}

function average(values: number[]) {
  if (values.length === 0) return 0
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

function formatDecimal(value: number, maximumFractionDigits: number) {
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits,
    minimumFractionDigits: value % 1 === 0 ? 0 : Math.min(1, maximumFractionDigits),
  }).format(value)
}

function percentOf(value: number, total: number) {
  if (total <= 0) return 0
  return (value / total) * 100
}

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1)
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
    if (reason.code === 'INVALID_URL') return 'That repository URL is not valid. Use a public GitHub repository URL, e.g. https://github.com/owner/repo.'
    if (reason.code === 'REPOSITORY_NOT_ACCESSIBLE') return 'TokenMeter could not access that repository. Make sure it is public and the URL is correct.'
    if (reason.code === 'CLONE_TIMEOUT') return 'The repository took too long to clone. Try a smaller or shallower repository.'
    if (reason.code === 'REPOSITORY_TOO_LARGE') return 'That repository exceeds the size limit (300 MiB). Try a smaller repository.'
    if (reason.code === 'CLONE_FAILED') return `Repository clone failed. ${reason.message}`
    if (reason.code === 'RATE_LIMITED') return 'Too many requests. Wait a moment and try again.'
    if (reason.code === 'ANALYSIS_NOT_FOUND') return 'Analysis not found. It may have been removed or the ID is incorrect.'
    if (reason.code === 'ANALYSIS_FAILED') return 'Analysis failed due to an unexpected error. Try again or pick a different repository.'
    if (reason.code === 'INVALID_REQUEST') return reason.message
    return reason.message
  }
  return 'Something went wrong. Try again or pick a different repository.'
}
