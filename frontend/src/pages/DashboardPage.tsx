import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import { PipelineTimeline } from '../components/PipelineTimeline'
import { TabBar } from '../components/TabBar'
import type { TabBarItem } from '../components/TabBar'
import { LanguagesTab } from '../components/results/LanguagesTab'
import { ModelsTab } from '../components/results/ModelsTab'
import { OverviewSection } from '../components/results/OverviewSection'
import { WhatIfTab } from '../components/results/WhatIfTab'
import { WorkflowTab } from '../components/results/WorkflowTab'
import { TrendingSection } from '../components/TrendingSection'
import { useAnalysisJob } from '../hooks/useAnalysisJob'
import { useAsync } from '../hooks/useAsync'
import { useElapsedSeconds } from '../hooks/useElapsedSeconds'
import { useStalledProgress } from '../hooks/useStalledProgress'
import { ApiError, DEFAULT_REPOSITORY_URL, getAnalysis, getCostBreakdown, submitAnalysis } from '../services/api'
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
import {
  costModes,
  currencyFormatter,
  numberFormatter,
} from '../utils/formatters'
import type { CostMode } from '../utils/formatters'
import {
  average,
  cheapest,
  highest,
  languageBreakdown,
  uniqueProviders,
} from '../utils/resultsCost'
import { buildPricingMap, derivePricing, pricingKey } from '../utils/whatIfCost'
import type { PricingMap } from '../utils/whatIfCost'

type ComparisonSort = 'cost' | 'relative' | 'efficiency' | 'model'
type ProviderFilter = 'all' | string
type ResultsTab = 'models' | 'languages' | 'workflow' | 'whatif'

const RESULTS_TABS: TabBarItem[] = [
  { key: 'models', label: 'Models' },
  { key: 'languages', label: 'Languages' },
  { key: 'workflow', label: 'Workflow & Effort' },
  { key: 'whatif', label: 'What-if' },
]

function ResultsPanel({
  tab,
  active,
  children,
}: {
  tab: ResultsTab
  active: boolean
  children: ReactNode
}) {
  return (
    <div
      role="tabpanel"
      id={`tab-panel-${tab}`}
      aria-labelledby={`tab-${tab}`}
      data-testid={`tab-panel-${tab}`}
      className={active ? 'mt-8 block' : 'mt-8 hidden print:block'}
    >
      {children}
    </div>
  )
}

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
  const [activeTab, setActiveTab] = useState<ResultsTab>('models')
  const [comparisonSort, setComparisonSort] = useState<ComparisonSort>('cost')
  const [providerFilter, setProviderFilter] = useState<ProviderFilter>('all')
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle')
  const [badgeCopyState, setBadgeCopyState] = useState<'idle' | 'copied' | 'failed'>('idle')
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
  // Range shown in the hero = cheapest / priciest model within the SELECTED mode.
  // Both react to the mode switch.
  const cheapestEstimate = useMemo(() => cheapest(estimatesForMode), [estimatesForMode])
  const highestEstimate = useMemo(() => highest(estimatesForMode), [estimatesForMode])
  const rawBaselineEstimate = useMemo(() => cheapest(rawEstimates), [rawEstimates])
  const primaryEstimate = cheapestEstimate ?? estimatesForMode[0] ?? null
  const providersForMode = useMemo(() => uniqueProviders(estimatesForMode), [estimatesForMode])
  const averageCost = average(estimatesForMode.map((estimate) => estimate.totalCost))

  // Cost-breakdown fetch — called exactly once per analysis load; factory is
  // memoized with useCallback so useAsync's [factory] dep is stable and does
  // not trigger infinite re-fetches.
  const breakdownFactory = useCallback(
    () => getCostBreakdown(analysis.id),
    [analysis.id],
  )
  const { data: breakdown } = useAsync(breakdownFactory)

  // Build the pricing map: prefer exact server-side pricing from the breakdown;
  // fall back to deriving from stored estimate rows for models whose server
  // pricing is null (or when breakdown hasn't loaded yet).
  const pricingMap = useMemo<PricingMap>(() => {
    const map = buildPricingMap(breakdown)
    // Group all cost estimates by provider:model for derivation fallback
    const grouped = new Map<string, RepositoryAnalysisCostEstimateResponse[]>()
    for (const estimate of analysis.costEstimates) {
      const key = pricingKey(estimate.provider, estimate.model)
      const existing = grouped.get(key)
      if (existing) {
        existing.push(estimate)
      } else {
        grouped.set(key, [estimate])
      }
    }
    // Fill in any model missing from the breakdown-derived map
    for (const [key, rows] of grouped.entries()) {
      if (!map.has(key)) {
        const derived = derivePricing(rows)
        if (derived) map.set(key, derived)
      }
    }
    return map
  }, [breakdown, analysis.costEstimates])

  async function handleCopyPublicUrl() {
    const copied = await copyPublicUrl(publicUrl)
    setCopyState(copied ? 'copied' : 'failed')
    window.setTimeout(() => setCopyState('idle'), 2200)
  }

  async function handleCopyBadgeMarkdown() {
    const repoPath = repositoryName(analysis.repositoryUrl)
    const [owner, repo] = repoPath.split('/')
    const origin = window.location.origin
    const snippet = `[![AI generation cost](${origin}/api/badge/${owner}/${repo}.svg)](${publicUrl})`
    const copied = await copyPublicUrl(snippet)
    setBadgeCopyState(copied ? 'copied' : 'failed')
    window.setTimeout(() => setBadgeCopyState('idle'), 2200)
  }

  return (
    <section className="mx-auto max-w-6xl px-4 py-10 sm:px-6 sm:py-16" id="results">
      <OverviewSection
        analysis={analysis}
        selectedMode={selectedMode}
        onSelectMode={setSelectedMode}
        lowestEstimate={cheapestEstimate}
        highestEstimate={highestEstimate}
        languageCount={languages.length}
        modelCount={estimatesForMode.length}
        averageCost={averageCost}
        onNewAnalysis={onNewAnalysis}
        copyState={copyState}
        onCopyPublicUrl={handleCopyPublicUrl}
        selectedOpenGraphImageUrl={selectedOpenGraphImageUrl}
        badgeCopyState={badgeCopyState}
        onCopyBadgeMarkdown={handleCopyBadgeMarkdown}
      />

      <TabBar
        tabs={RESULTS_TABS}
        activeTab={activeTab}
        onSelect={(key) => setActiveTab(key as ResultsTab)}
        idBase="tab"
        ariaLabel="Results sections"
      />

      <ResultsPanel tab="models" active={activeTab === 'models'}>
        <ModelsTab
          estimates={estimatesForMode}
          providerFilter={providerFilter}
          providers={providersForMode}
          selectedMode={selectedMode}
          sortBy={comparisonSort}
          onFilterProvider={setProviderFilter}
          onSort={setComparisonSort}
        />
      </ResultsPanel>

      <ResultsPanel tab="languages" active={activeTab === 'languages'}>
        <LanguagesTab languages={languages} totalTokens={analysis.metrics.totalTokens} />
      </ResultsPanel>

      <ResultsPanel tab="workflow" active={activeTab === 'workflow'}>
        <WorkflowTab
          estimates={estimatesForMode}
          selectedMode={selectedMode}
          primaryEstimate={primaryEstimate}
          rawBaselineEstimate={rawBaselineEstimate}
        />
      </ResultsPanel>

      <ResultsPanel tab="whatif" active={activeTab === 'whatif'}>
        <WhatIfTab estimates={estimatesForMode} selectedMode={selectedMode} pricingMap={pricingMap} />
      </ResultsPanel>
    </section>
  )
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
