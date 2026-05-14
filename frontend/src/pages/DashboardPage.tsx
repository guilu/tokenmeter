import { useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import { analyzeRepository, ApiError, DEFAULT_REPOSITORY_URL, getAnalysis } from '../services/api'
import type { RepositoryAnalysisCostEstimateResponse, RepositoryAnalysisResponse } from '../types/api'

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

const analysisStages = [
  { label: 'Cloning repository', detail: 'Opening a clean workspace and fetching the public Git history.' },
  { label: 'Detecting languages', detail: 'Classifying source files, frameworks and generated artifacts.' },
  { label: 'Parsing files', detail: 'Filtering noise and preparing a normalized code corpus.' },
  { label: 'Counting tokens', detail: 'Measuring the repository footprint with model-compatible encoding.' },
  { label: 'Building context windows', detail: 'Estimating prompt chunks and context required to recreate the project.' },
  { label: 'Simulating AI workflows', detail: 'Comparing raw, assisted and agentic generation strategies.' },
  { label: 'Calculating pricing models', detail: 'Applying input, output and workflow overhead across model families.' },
  { label: 'Generating estimates', detail: 'Compiling the cost intelligence report and shareable analysis.' },
] as const

export function DashboardPage() {
  const [repositoryUrl, setRepositoryUrl] = useState(DEFAULT_REPOSITORY_URL)
  const [analysis, setAnalysis] = useState<RepositoryAnalysisResponse | null>(null)
  const [routeAnalysisId, setRouteAnalysisId] = useState(() => getAnalysisIdFromLocation())
  const [loading, setLoading] = useState(false)
  const [sharedLoading, setSharedLoading] = useState(() => Boolean(getAnalysisIdFromLocation()))
  const [error, setError] = useState<string | null>(null)
  const [sharedError, setSharedError] = useState<string | null>(null)
  const [showModes, setShowModes] = useState(false)

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
      setRouteAnalysisId(result.id)
      window.history.pushState(null, '', analysisPath(result.id))
    } catch (reason) {
      setError(toUserMessage(reason))
    } finally {
      setLoading(false)
    }
  }

  function handleNewAnalysis() {
    setAnalysis(null)
    setRouteAnalysisId(null)
    setSharedError(null)
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
      <div className="absolute inset-x-0 top-0 -z-10 h-[32rem] bg-[radial-gradient(circle_at_top,_rgba(34,211,238,0.18),_transparent_55%)]" />
      <div className="mx-auto max-w-4xl px-6 pt-20 pb-6 text-center">
        <p className="mb-4 inline-flex rounded-full border border-cyan-400/20 bg-cyan-400/10 px-3 py-1 text-sm text-cyan-200">
          AI repository cost intelligence
        </p>
        <h1 className="text-4xl font-semibold tracking-tight text-white sm:text-6xl">
          Simulate the AI generation cost of any GitHub repository.
        </h1>
        <p className="mt-6 text-lg leading-8 text-slate-400">
          TokenMeter scans a public codebase, measures its token footprint and benchmarks what it would cost to generate with modern AI models across raw, assisted and agentic workflows.
        </p>
      </div>

      <div className="mx-auto max-w-4xl px-6 pb-6">
        <form
          className="rounded-3xl bg-white/[0.04] p-3 shadow-2xl shadow-cyan-950/30 backdrop-blur"
          onSubmit={handleSubmit}
        >
          <label className="sr-only" htmlFor="repository-url">
            Repository URL
          </label>
          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="flex flex-1 items-center gap-2 rounded-2xl border border-white/10 bg-slate-950/80 px-4 transition focus-within:border-cyan-300/70 focus-within:ring-4 focus-within:ring-cyan-400/10">
              <svg aria-hidden="true" className="h-4 w-4 shrink-0 text-slate-500" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
              </svg>
              <input
                className="min-h-12 flex-1 bg-transparent text-sm text-white outline-none placeholder:text-slate-500"
                disabled={loading}
                id="repository-url"
                inputMode="url"
                onChange={(event) => setRepositoryUrl(event.target.value)}
                placeholder="https://github.com/user/repo"
                type="url"
                value={repositoryUrl}
              />
            </div>
            <button
              className="min-h-12 rounded-2xl bg-cyan-300 px-6 text-sm font-semibold text-slate-950 transition hover:bg-cyan-200 disabled:cursor-not-allowed disabled:opacity-60"
              disabled={loading}
              type="submit"
            >
              {loading ? 'Simulating…' : 'Simulate generation cost'}
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

      <div className="mx-auto max-w-4xl px-6 pb-20">
        <button
          className="mb-4 flex w-full items-center justify-between text-left"
          onClick={() => setShowModes((v) => !v)}
          type="button"
        >
          <p className="text-sm font-medium text-slate-300">Generation Economics Model</p>
          <svg
            aria-hidden="true"
            className={`h-4 w-4 text-slate-500 transition-transform duration-300 ${showModes ? 'rotate-180' : ''}`}
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
                className="relative rounded-2xl bg-slate-900/60 p-5 shadow-xl shadow-black/20"
                key={mode}
              >
                {mode === 'assisted' ? (
                  <span className="absolute -top-2.5 left-1/2 -translate-x-1/2 rounded-full border border-cyan-400/30 bg-cyan-400/20 px-2.5 py-0.5 text-[10px] font-semibold uppercase tracking-widest text-cyan-200">
                    Default
                  </span>
                ) : null}
                <p className="font-semibold text-white capitalize">{mode}</p>
                <p className="mt-2 text-sm leading-6 text-slate-400">{modeCopy[mode]}</p>
                <p className="mt-4 text-xs text-slate-500">{modeMultiplierLabel[mode]}</p>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </section>
  )
}

function LoadingState({ repositoryUrl }: { repositoryUrl: string }) {
  const [activeStage, setActiveStage] = useState(0)
  const trimmedRepositoryUrl = repositoryUrl.trim()
  const repositoryLabel = repositoryNameFromUrl(trimmedRepositoryUrl)
  const progress = Math.round(((activeStage + 1) / analysisStages.length) * 100)

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setActiveStage((currentStage) => Math.min(currentStage + 1, analysisStages.length - 1))
    }, 1500)

    return () => window.clearInterval(intervalId)
  }, [])

  const liveStats = useMemo(() => {
    const seed = repositoryLabel.length || trimmedRepositoryUrl.length || 12
    const files = Math.min(2400, Math.round((activeStage + 1) * seed * 7.4))
    const tokens = Math.min(1_900_000, files * 690 + activeStage * seed * 173)
    const contextWindows = Math.max(1, Math.ceil(tokens / 120_000))

    return [
      { label: 'Files inspected', value: compactNumberFormatter.format(files) },
      { label: 'Tokens sampled', value: compactNumberFormatter.format(tokens) },
      { label: 'Context windows', value: numberFormatter.format(contextWindows) },
    ]
  }, [activeStage, repositoryLabel.length, trimmedRepositoryUrl.length])

  return (
    <div className="relative mt-8 overflow-hidden rounded-3xl border border-cyan-300/20 bg-slate-950/90 p-5 shadow-2xl shadow-cyan-950/30">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(34,211,238,0.18),_transparent_35%),linear-gradient(120deg,_rgba(15,23,42,0),_rgba(34,211,238,0.08),_rgba(15,23,42,0))]" />
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-cyan-300/70 to-transparent" />

      <div className="relative space-y-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.28em] text-cyan-200/80">AI analysis pipeline</p>
            <h2 className="mt-2 text-xl font-semibold text-white">Scanning {repositoryLabel}</h2>
            <p className="mt-1 text-sm text-slate-400">Simulating repository generation economics in real time.</p>
          </div>
          <div className="rounded-2xl border border-cyan-300/20 bg-cyan-300/10 px-4 py-2 text-right">
            <p className="text-2xl font-semibold text-cyan-100">{progress}%</p>
            <p className="text-xs text-cyan-200/70">pipeline progress</p>
          </div>
        </div>

        <div className="h-2 overflow-hidden rounded-full bg-white/10">
          <div
            className="h-full rounded-full bg-gradient-to-r from-cyan-300 via-sky-300 to-emerald-300 transition-all duration-700 ease-out"
            style={{ width: `${progress}%` }}
          />
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          {liveStats.map((stat) => (
            <div className="rounded-2xl bg-white/[0.04] p-3" key={stat.label}>
              <p className="text-lg font-semibold text-white">{stat.value}</p>
              <p className="mt-1 text-xs text-slate-400">{stat.label}</p>
            </div>
          ))}
        </div>

        <div className="stage-enter rounded-2xl border border-cyan-300/40 bg-cyan-300/10 p-4 shadow-lg shadow-cyan-950/30" key={activeStage}>
          <div className="flex items-center gap-3">
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full animate-pulse bg-cyan-300 text-[10px] font-bold text-slate-950">
              {activeStage + 1}
            </span>
            <div>
              <p className="text-sm font-semibold text-white">{analysisStages[activeStage].label}</p>
              <p className="mt-0.5 text-xs leading-5 text-slate-400">{analysisStages[activeStage].detail}</p>
            </div>
            <p className="ml-auto text-xs text-slate-500 tabular-nums">{activeStage + 1} / {analysisStages.length}</p>
          </div>
        </div>

        <div className="rounded-2xl border border-white/10 bg-black/20 p-4 font-mono text-xs text-cyan-100/80">
          <p>&gt; pipeline.run --repository {trimmedRepositoryUrl || repositoryLabel}</p>
          <p className="mt-1 text-slate-400">&gt; stage.{activeStage + 1}: {analysisStages[activeStage].label.toLowerCase()}...</p>
        </div>
      </div>
    </div>
  )
}

function SharedAnalysisState({ error, loading, onBack }: { error: string | null; loading: boolean; onBack: () => void }) {
  return (
    <section className="mx-auto max-w-3xl px-6 py-20">
      <button className="text-sm text-cyan-200 transition hover:text-cyan-100" onClick={onBack} type="button">
        ← Back to analyzer
      </button>
      <div className="mt-8 rounded-3xl bg-white/[0.04] p-8 shadow-2xl shadow-black/20">
        {loading && !error ? (
          <div className="flex items-center gap-3 text-cyan-100">
            <span className="h-3 w-3 animate-pulse rounded-full bg-cyan-300" />
            <span>Loading public analysis…</span>
          </div>
        ) : null}
        {error ? (
          <>
            <p className="text-sm text-red-200">Analysis not available</p>
            <h1 className="mt-3 text-3xl font-semibold text-white">This public analysis could not be loaded.</h1>
            <p className="mt-3 text-slate-400">{error}</p>
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
        <button className="text-sm text-cyan-200 transition hover:text-cyan-100" onClick={onNewAnalysis} type="button">
          ← Analyze another repository
        </button>
        <div className="flex flex-wrap items-center gap-2">
          <button
            className="rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-2 text-sm text-slate-200 transition hover:bg-white/10"
            onClick={() => void handleCopyPublicUrl()}
            type="button"
          >
            {copyState === 'copied' ? 'Copied!' : copyState === 'failed' ? 'Copy failed' : 'Copy public URL'}
          </button>
          <a
            className="rounded-2xl border border-cyan-300/20 bg-cyan-300/10 px-4 py-2 text-sm font-medium text-cyan-100 transition hover:bg-cyan-300/20"
            href={selectedOpenGraphImageUrl}
            rel="noreferrer"
            target="_blank"
          >
            Download badge
          </a>
          <a
            className="rounded-2xl border border-cyan-300/20 bg-cyan-300/10 px-4 py-2 text-sm font-medium text-cyan-100 transition hover:bg-cyan-300/20"
            href={`/api/analyze/${analysis.id}/badge.svg`}
            rel="noreferrer"
            target="_blank"
          >
            Download mini badge
          </a>
        </div>
      </div>

      <header className="mt-6">
        <p className="mb-4 inline-flex rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1 text-sm text-emerald-200">
          Analysis complete
        </p>
        <h1 className="flex items-center gap-3 text-2xl font-semibold tracking-tight text-white sm:text-4xl">
          <svg aria-hidden="true" className="h-7 w-7 shrink-0 text-slate-400 sm:h-9 sm:w-9" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
          </svg>
          <span className="break-all">{analysis.repositoryUrl}</span>
        </h1>
        <p className="mt-3 text-sm text-slate-400">
          Analysis id: {analysis.id} · {dateFormatter.format(new Date(analysis.createdAt))}
        </p>
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

      <ModelComparison
        estimates={estimatesForMode}
        providerFilter={providerFilter}
        providers={providersForMode}
        selectedMode={selectedMode}
        sortBy={comparisonSort}
        onFilterProvider={setProviderFilter}
        onSort={setComparisonSort}
      />

      <div className="mt-8 rounded-3xl bg-white/[0.03] p-4 sm:p-6">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <p className="text-sm text-slate-400">Cost breakdown</p>
            <h2 className="mt-1 text-2xl font-semibold text-white">AI generation estimates</h2>
          </div>
          <p className="rounded-full border border-cyan-400/20 bg-cyan-400/10 px-3 py-1 text-sm text-cyan-100">
            {capitalize(selectedMode)} mode
          </p>
        </div>
        <div className="mt-6 overflow-hidden rounded-2xl">
          <table className="min-w-full divide-y divide-white/10 text-sm">
            <thead className="bg-white/[0.04] text-left text-slate-400">
              <tr>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">Provider</th>
                <th className="px-4 py-3 font-medium">Model</th>
                <th className="px-4 py-3 font-medium">Mode</th>
                <th className="hidden px-4 py-3 text-right font-medium sm:table-cell">Output tokens</th>
                <th className="px-4 py-3 text-right font-medium">Total cost</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10 text-slate-200">
              {estimatesForMode.map((estimate) => (
                <tr key={`${estimate.provider}-${estimate.model}-${estimate.mode}`}>
                  <td className="hidden px-4 py-3 capitalize sm:table-cell">{estimate.provider}</td>
                  <td className="px-4 py-3">{estimate.model}</td>
                  <td className="px-4 py-3 capitalize">{estimate.mode}</td>
                  <td className="hidden px-4 py-3 text-right sm:table-cell">{numberFormatter.format(estimate.estimatedOutputTokens)}</td>
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

function EngineeringEffortPanel({ estimates, selectedMode }: { estimates: RepositoryAnalysisCostEstimateResponse[]; selectedMode: CostMode }) {
  const representativeEstimate = cheapest(estimates) ?? estimates[0] ?? null
  const highestEffortEstimate = estimates.reduce<RepositoryAnalysisCostEstimateResponse | null>((best, estimate) => {
    if (best === null) return estimate
    return estimate.engineeringEffort.seniorEngineerHours > best.engineeringEffort.seniorEngineerHours ? estimate : best
  }, null)
  const averageHours = average(estimates.map((estimate) => estimate.engineeringEffort.seniorEngineerHours))
  const assumptions = representativeEstimate?.engineeringEffort.assumptions

  return (
    <section className="mt-8 rounded-3xl border border-emerald-300/20 bg-emerald-300/[0.04] p-5 shadow-2xl shadow-black/20 sm:p-6">
      <div className="grid gap-6 lg:grid-cols-[0.95fr_1.05fr] lg:items-start">
        <div>
          <p className="text-sm text-emerald-200/80">Engineering effort equivalence</p>
          <h2 className="mt-1 text-2xl font-semibold text-white">Human-readable scale for {selectedMode} mode</h2>
          <p className="mt-3 text-sm leading-6 text-slate-400">
            TokenMeter translates token and workflow estimates into senior-engineering time, so cost numbers have a practical delivery-scale reference instead of feeling like abstract cents.
          </p>
          {representativeEstimate ? (
            <div className="mt-5 rounded-2xl border border-emerald-300/20 bg-emerald-300/10 p-5">
              <p className="text-xs uppercase tracking-[0.2em] text-emerald-100/70">Lowest-cost model equivalence</p>
              <p className="mt-3 text-3xl font-semibold text-white">{representativeEstimate.engineeringEffort.summary}</p>
              <p className="mt-2 text-sm text-emerald-100/80">
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
    <section className="mt-8 rounded-3xl bg-white/[0.03] p-5 shadow-2xl shadow-black/20 sm:p-6">
      <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-start">
        <div>
          <p className="text-sm text-slate-400">Model benchmark</p>
          <h2 className="mt-1 text-2xl font-semibold text-white">AI model comparison</h2>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-400">
            Compare {selectedMode} generation estimates side-by-side by provider, cost, relative efficiency and quality tier.
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="grid gap-1 text-sm text-slate-400">
            Provider
            <select
              className="rounded-2xl border border-white/10 bg-slate-950 px-3 py-2 text-sm text-white outline-none transition focus:border-cyan-300/60"
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
          </label>
          <label className="grid gap-1 text-sm text-slate-400">
            Sort by
            <select
              className="rounded-2xl border border-white/10 bg-slate-950 px-3 py-2 text-sm text-white outline-none transition focus:border-cyan-300/60"
              onChange={(event) => onSort(event.target.value as ComparisonSort)}
              value={sortBy}
            >
              <option value="cost">Lowest cost</option>
              <option value="relative">Relative cost</option>
              <option value="efficiency">Efficiency</option>
              <option value="model">Model name</option>
            </select>
          </label>
        </div>
      </div>

      <div className="mt-6 grid gap-3 lg:hidden">
        {comparisonRows.map((row) => (
          <ModelComparisonCard key={`${row.estimate.provider}-${row.estimate.model}`} row={row} />
        ))}
      </div>

      <div className="mt-6 hidden overflow-hidden rounded-2xl lg:block">
        <table className="min-w-full divide-y divide-white/10 text-sm">
          <thead className="bg-white/[0.04] text-left text-slate-400">
            <tr>
              <th className="px-4 py-3 font-medium">Model</th>
              <th className="px-4 py-3 font-medium">Provider</th>
              <th className="px-4 py-3 text-right font-medium">Estimated cost</th>
              <th className="px-4 py-3 font-medium">Relative cost</th>
              <th className="px-4 py-3 font-medium">Efficiency tier</th>
              <th className="px-4 py-3 font-medium">Notes</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/10 text-slate-200">
            {comparisonRows.map((row) => (
              <tr className="transition hover:bg-white/[0.03]" key={`${row.estimate.provider}-${row.estimate.model}`}>
                <td className="px-4 py-3 font-medium text-white">{row.estimate.model}</td>
                <td className="px-4 py-3 capitalize text-slate-300">{row.estimate.provider}</td>
                <td className="px-4 py-3 text-right font-medium text-white">{currencyFormatter.format(row.estimate.totalCost)}</td>
                <td className="px-4 py-3">
                  <RelativeCostBar percent={row.costPercent} label={`${row.relativeCost.toFixed(1)}×`} />
                </td>
                <td className="px-4 py-3"><TierBadge tier={row.tier} /></td>
                <td className="px-4 py-3 text-slate-400">{row.note}</td>
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
    <article className="rounded-2xl bg-slate-950/45 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-medium text-white" title={row.estimate.model}>{row.estimate.model}</p>
          <p className="mt-1 text-sm capitalize text-slate-500">{row.estimate.provider}</p>
        </div>
        <p className="shrink-0 text-lg font-semibold text-white">{currencyFormatter.format(row.estimate.totalCost)}</p>
      </div>
      <div className="mt-4">
        <RelativeCostBar percent={row.costPercent} label={`${row.relativeCost.toFixed(1)}× vs cheapest`} />
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <TierBadge tier={row.tier} />
        <span className="text-sm text-slate-400">{row.note}</span>
      </div>
    </article>
  )
}

function RelativeCostBar({ percent, label }: { percent: number; label: string }) {
  return (
    <div>
      <div className="mb-2 flex items-center justify-between gap-3 text-xs text-slate-400">
        <span>Relative cost</span>
        <span className="font-medium text-cyan-100">{label}</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-white/10">
        <div className="h-full rounded-full bg-gradient-to-r from-emerald-300 via-cyan-300 to-amber-300" style={{ width: `${Math.max(4, Math.min(100, percent))}%` }} />
      </div>
    </div>
  )
}

function TierBadge({ tier }: { tier: string }) {
  const tone = tier === 'Cheapest' ? 'emerald' : tier === 'Premium' || tier === 'High reasoning' ? 'amber' : tier === 'Experimental' ? 'violet' : 'cyan'
  const className =
    tone === 'emerald'
      ? 'border-emerald-300/20 bg-emerald-300/10 text-emerald-100'
      : tone === 'amber'
        ? 'border-amber-300/20 bg-amber-300/10 text-amber-100'
        : tone === 'violet'
          ? 'border-violet-300/20 bg-violet-300/10 text-violet-100'
          : 'border-cyan-300/20 bg-cyan-300/10 text-cyan-100'

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
    <section className="relative mt-8 overflow-hidden rounded-[2rem] border border-cyan-300/20 bg-slate-950/90 p-6 shadow-2xl shadow-cyan-950/30 sm:p-8">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(34,211,238,0.2),_transparent_38%),radial-gradient(circle_at_bottom_right,_rgba(16,185,129,0.16),_transparent_36%)]" />
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-cyan-300/80 to-transparent" />

      <div className="relative">
        <p className="inline-flex rounded-full border border-cyan-300/20 bg-cyan-300/10 px-3 py-1 text-sm text-cyan-100">
          Estimated generation cost range
        </p>
        <div
          className="mt-5 grid gap-4 transition-all duration-500 sm:grid-cols-2"
          key={`${selectedMode}-${lowestEstimate?.provider ?? 'none'}-${highestEstimate?.provider ?? 'none'}`}
        >
          <div className="min-w-0 rounded-2xl bg-white/[0.04] p-5">
            <p className="text-5xl font-semibold tracking-tight text-white sm:text-6xl">
              {lowestEstimate ? currencyFormatter.format(lowestEstimate.totalCost) : '—'}
            </p>
            <p className="mt-3 truncate text-sm text-slate-400">
              {lowestEstimate ? `${lowestEstimate.provider} · ${lowestEstimate.model} · ${selectedMode} workflow mode` : 'No estimate'}
            </p>
          </div>
          <div className="min-w-0 rounded-2xl bg-white/[0.04] p-5">
            <p className="text-5xl font-semibold tracking-tight text-white sm:text-6xl">
              {highestEstimate ? currencyFormatter.format(highestEstimate.totalCost) : '—'}
            </p>
            <p className="mt-3 truncate text-sm text-slate-400">
              {highestEstimate ? `${highestEstimate.provider} · ${highestEstimate.model} · ${selectedMode} workflow mode` : 'No estimate'}
            </p>
          </div>
        </div>

        <p className="mt-4 max-w-3xl text-sm leading-6 text-slate-400">
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

function HeroMeta({ label, value }: { label: string; value: string }) {
  return (
    <article className="min-w-0 rounded-2xl bg-white/[0.04] p-4">
      <p className="text-xs uppercase tracking-[0.2em] text-slate-500">{label}</p>
      <p className="mt-2 truncate text-lg font-semibold text-white" title={value}>
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
    <section className="mt-8 rounded-3xl bg-white/[0.03] p-5 shadow-2xl shadow-black/20 sm:p-6">
      <div className="grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
        <div>
          <p className="text-sm text-slate-400">Workflow assumptions</p>
          <h2 className="mt-1 text-2xl font-semibold text-white">{assumptions.title}</h2>
          <p className="mt-3 text-sm leading-6 text-slate-400">{assumptions.summary}</p>
          <div className="mt-5 rounded-2xl border border-cyan-300/20 bg-cyan-300/10 p-4">
            <p className="text-xs uppercase tracking-[0.2em] text-cyan-200/70">Heuristic simulation</p>
            <p className="mt-2 text-sm leading-6 text-cyan-50">
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
              <li className="flex gap-3 rounded-2xl bg-slate-950/45 p-3 text-sm leading-6 text-slate-300" key={item}>
                <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-cyan-300" />
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
    <article className="rounded-2xl bg-white/[0.04] p-4">
      <p className="text-xs uppercase tracking-[0.2em] text-slate-500">{label}</p>
      <p className="mt-2 text-lg font-semibold text-white">{value}</p>
    </article>
  )
}

function ModeSwitch({ selectedMode, onSelectMode }: { selectedMode: CostMode; onSelectMode: (mode: CostMode) => void }) {
  return (
    <div className="mt-6 rounded-2xl border border-white/10 bg-slate-950/60 p-1.5">
      <div className="grid grid-cols-3 gap-1.5">
        {costModes.map((mode) => {
          const active = selectedMode === mode
          return (
            <button
              className={`rounded-xl px-4 py-3 text-base font-semibold capitalize transition ${
                active ? 'bg-cyan-300 text-slate-950 shadow-lg shadow-cyan-300/20' : 'text-slate-300 hover:bg-white/10 hover:text-white'
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
    <article className="rounded-2xl bg-white/[0.03] p-5 shadow-2xl shadow-black/20 sm:p-6">
      <p className="text-sm text-slate-400">{label}</p>
      <p className="mt-3 text-3xl font-semibold text-white">{value}</p>
      <p className="mt-2 text-sm text-slate-500">{hint}</p>
    </article>
  )
}

function Panel({ eyebrow, title, children }: { eyebrow: string; title: string; children: ReactNode }) {
  return (
    <section className="rounded-3xl bg-white/[0.03] p-5 shadow-2xl shadow-black/20 sm:p-6">
      <p className="text-sm text-slate-400">{eyebrow}</p>
      <h2 className="mt-1 text-2xl font-semibold text-white">{title}</h2>
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
    return <p className="rounded-2xl border border-dashed border-white/10 p-6 text-sm text-slate-400">{emptyLabel}</p>
  }

  return (
    <div className="space-y-4">
      {items.map((item) => (
        <div key={`${item.label}-${item.helper}`}>
          <div className="mb-2 flex items-start justify-between gap-3 text-sm">
            <div className="min-w-0">
              <p className="truncate font-medium text-white" title={item.label}>
                {item.label}
              </p>
              <p className="truncate text-slate-500" title={item.helper}>
                {item.helper}
              </p>
            </div>
            <p className="shrink-0 text-right font-medium text-cyan-100">{valueFormatter(item.value)}</p>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-white/10">
            <div
              className="h-full rounded-full bg-gradient-to-r from-cyan-300 to-emerald-300"
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
    <article className="rounded-2xl bg-slate-950/50 p-4">
      <p className="text-sm text-slate-400">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-white">
        {estimate ? currencyFormatter.format(estimate.totalCost) : '$0.00'}
      </p>
      <p className="mt-1 truncate text-sm text-slate-500" title={estimate ? `${estimate.provider} · ${estimate.model}` : undefined}>
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

function openGraphImageUrl(analysisId: string, publicUrl: string, mode: CostMode) {
  const publicUrlOrigin = new URL(publicUrl).origin
  return new URL(`/api/analyze/${encodeURIComponent(analysisId)}/og-image.png?mode=${mode}&v=range`, publicUrlOrigin).toString()
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
