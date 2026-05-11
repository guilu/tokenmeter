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

const modeCopy: Record<CostMode, string> = {
  raw: 'Price the final codebase as if the repository appeared in one clean generation pass.',
  assisted: 'Model the real workflow: prompts, reviews, fixes and context loaded by engineers.',
  agentic: 'Estimate autonomous build loops with planning, tool calls, retries and reasoning overhead.',
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
      <div className="absolute inset-x-0 top-0 -z-10 h-96 bg-[radial-gradient(circle_at_top,_rgba(34,211,238,0.18),_transparent_55%)]" />
      <div className="mx-auto grid max-w-6xl gap-12 px-6 py-16 lg:grid-cols-[1.1fr_0.9fr] lg:py-24">
        <div>
          <p className="mb-4 inline-flex rounded-full border border-cyan-400/20 bg-cyan-400/10 px-3 py-1 text-sm text-cyan-200">
            AI repository cost intelligence
          </p>
          <h1 className="text-4xl font-semibold tracking-tight text-white sm:text-6xl">
            Simulate the AI generation cost of any GitHub repository.
          </h1>
          <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-400">
            TokenMeter scans a public codebase, measures its token footprint and benchmarks what it would cost to generate with modern AI models across raw, assisted and agentic workflows.
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

        <aside className="rounded-3xl border border-white/10 bg-slate-900/60 p-6 shadow-2xl shadow-black/30">
          <p className="text-sm text-slate-400">Generation economics model</p>
          <div className="mt-6 space-y-4">
            {costModes.map((mode) => (
              <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4" key={mode}>
                <p className="font-medium text-white capitalize">{mode} mode</p>
                <p className="mt-1 text-sm leading-6 text-slate-400">{modeCopy[mode]}</p>
              </div>
            ))}
          </div>
        </aside>
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
            <div className="rounded-2xl border border-white/10 bg-white/[0.04] p-3" key={stat.label}>
              <p className="text-lg font-semibold text-white">{stat.value}</p>
              <p className="mt-1 text-xs text-slate-400">{stat.label}</p>
            </div>
          ))}
        </div>

        <ol className="space-y-2">
          {analysisStages.map((stage, index) => {
            const complete = index < activeStage
            const active = index === activeStage

            return (
              <li
                className={`rounded-2xl border p-3 transition-all duration-500 ${
                  active
                    ? 'border-cyan-300/40 bg-cyan-300/10 shadow-lg shadow-cyan-950/30'
                    : complete
                      ? 'border-emerald-300/20 bg-emerald-300/5'
                      : 'border-white/10 bg-white/[0.025] opacity-70'
                }`}
                key={stage.label}
              >
                <div className="flex items-start gap-3">
                  <span
                    className={`mt-1 flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${
                      complete
                        ? 'bg-emerald-300 text-slate-950'
                        : active
                          ? 'animate-pulse bg-cyan-300 text-slate-950'
                          : 'bg-white/10 text-slate-500'
                    }`}
                  >
                    {complete ? '✓' : index + 1}
                  </span>
                  <div>
                    <p className="text-sm font-medium text-white">{stage.label}</p>
                    <p className="mt-1 text-xs leading-5 text-slate-400">{stage.detail}</p>
                  </div>
                </div>
              </li>
            )
          })}
        </ol>

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
      <div className="mt-8 rounded-3xl border border-white/10 bg-white/[0.04] p-8 shadow-2xl shadow-black/20">
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
  const publicUrl = typeof window === 'undefined' ? analysisPath(analysis.id) : new URL(analysisPath(analysis.id), window.location.origin).toString()

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
  const averageCost = average(estimatesForMode.map((estimate) => estimate.totalCost))

  return (
    <section className="mx-auto max-w-6xl px-4 py-10 sm:px-6 sm:py-16" id="results">
      <button className="text-sm text-cyan-200 transition hover:text-cyan-100" onClick={onNewAnalysis} type="button">
        ← Analyze another repository
      </button>

      <header className="mt-6 grid gap-6 lg:grid-cols-[1fr_auto] lg:items-end">
        <div className="min-w-0">
          <p className="mb-4 inline-flex rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1 text-sm text-emerald-200">
            Analysis complete
          </p>
          <h1 className="truncate text-2xl font-semibold tracking-tight text-white sm:text-4xl" title={analysis.repositoryUrl}>
            {analysis.repositoryUrl}
          </h1>
          <p className="mt-3 truncate text-sm text-slate-400" title={analysis.id}>
            Analysis id: {analysis.id} · {dateFormatter.format(new Date(analysis.createdAt))}
          </p>
        </div>
        <div className="grid gap-3">
          <ModeSwitch selectedMode={selectedMode} onSelectMode={setSelectedMode} />
          <button
            className="rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-3 text-sm text-slate-200 transition hover:bg-white/10"
            onClick={() => void copyPublicUrl(publicUrl)}
            type="button"
          >
            Copy public URL
          </button>
        </div>
      </header>

      <CostHero analysis={analysis} estimate={primaryEstimate} selectedMode={selectedMode} topLanguage={topLanguage} />

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

      <div className="mt-8 rounded-3xl border border-white/10 bg-white/[0.03] p-4 sm:p-6">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <p className="text-sm text-slate-400">Cost breakdown</p>
            <h2 className="mt-1 text-2xl font-semibold text-white">AI generation estimates</h2>
          </div>
          <p className="rounded-full border border-cyan-400/20 bg-cyan-400/10 px-3 py-1 text-sm text-cyan-100">
            {capitalize(selectedMode)} mode
          </p>
        </div>
        <div className="mt-6 overflow-hidden rounded-2xl border border-white/10">
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

function CostHero({
  analysis,
  estimate,
  selectedMode,
  topLanguage,
}: {
  analysis: RepositoryAnalysisResponse
  estimate: RepositoryAnalysisCostEstimateResponse | null
  selectedMode: CostMode
  topLanguage: ReturnType<typeof languageBreakdown>[number] | undefined
}) {
  const repositoryLabel = repositoryName(analysis.repositoryUrl)
  const modelLabel = estimate ? `${estimate.provider} · ${estimate.model}` : 'No model estimate available'

  return (
    <section className="relative mt-8 overflow-hidden rounded-[2rem] border border-cyan-300/20 bg-slate-950/90 p-6 shadow-2xl shadow-cyan-950/30 sm:p-8">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(34,211,238,0.2),_transparent_38%),radial-gradient(circle_at_bottom_right,_rgba(16,185,129,0.16),_transparent_36%)]" />
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-cyan-300/80 to-transparent" />

      <div className="relative grid gap-8 lg:grid-cols-[1.15fr_0.85fr] lg:items-end">
        <div className="min-w-0">
          <p className="inline-flex rounded-full border border-cyan-300/20 bg-cyan-300/10 px-3 py-1 text-sm text-cyan-100">
            Estimated generation cost
          </p>
          <div className="mt-5 transition-all duration-500" key={`${selectedMode}-${estimate?.provider ?? 'none'}-${estimate?.model ?? 'none'}`}>
            <p className="text-6xl font-semibold tracking-tight text-white sm:text-7xl">
              {estimate ? currencyFormatter.format(estimate.totalCost) : '$0.00'}
            </p>
            <p className="mt-3 text-lg text-slate-300">
              using <span className="font-medium text-cyan-100">{modelLabel}</span> in {selectedMode} workflow mode
            </p>
            <p className="mt-4 max-w-2xl text-sm leading-6 text-slate-400">
              TokenMeter estimates what it would cost to regenerate {repositoryLabel} with AI, including repository size,
              token footprint and workflow overhead for the selected mode.
            </p>
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
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
    <article className="min-w-0 rounded-2xl border border-white/10 bg-white/[0.04] p-4">
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
    <section className="mt-8 rounded-3xl border border-white/10 bg-white/[0.03] p-5 shadow-2xl shadow-black/20 sm:p-6">
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
              <li className="flex gap-3 rounded-2xl border border-white/10 bg-slate-950/45 p-3 text-sm leading-6 text-slate-300" key={item}>
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
    <article className="rounded-2xl border border-white/10 bg-white/[0.04] p-4">
      <p className="text-xs uppercase tracking-[0.2em] text-slate-500">{label}</p>
      <p className="mt-2 text-lg font-semibold text-white">{value}</p>
    </article>
  )
}

function ModeSwitch({ selectedMode, onSelectMode }: { selectedMode: CostMode; onSelectMode: (mode: CostMode) => void }) {
  return (
    <div className="rounded-2xl border border-white/10 bg-slate-950/60 p-1">
      <div className="grid grid-cols-3 gap-1">
        {costModes.map((mode) => {
          const active = selectedMode === mode
          return (
            <button
              className={`rounded-xl px-3 py-2 text-sm font-medium capitalize transition ${
                active ? 'bg-cyan-300 text-slate-950' : 'text-slate-300 hover:bg-white/10 hover:text-white'
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
    <article className="rounded-2xl border border-white/10 bg-white/[0.03] p-5 shadow-2xl shadow-black/20 sm:p-6">
      <p className="text-sm text-slate-400">{label}</p>
      <p className="mt-3 text-3xl font-semibold text-white">{value}</p>
      <p className="mt-2 text-sm text-slate-500">{hint}</p>
    </article>
  )
}

function Panel({ eyebrow, title, children }: { eyebrow: string; title: string; children: ReactNode }) {
  return (
    <section className="rounded-3xl border border-white/10 bg-white/[0.03] p-5 shadow-2xl shadow-black/20 sm:p-6">
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
    <article className="rounded-2xl border border-white/10 bg-slate-950/50 p-4">
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
  return match ? decodeURIComponent(match[1]) : null
}

function analysisPath(analysisId: string) {
  return `/analysis/${encodeURIComponent(analysisId)}`
}

async function copyPublicUrl(publicUrl: string) {
  if (!navigator.clipboard) return
  await navigator.clipboard.writeText(publicUrl)
}

function updateDocumentMetadata(analysis: RepositoryAnalysisResponse, publicUrl: string) {
  const title = `TokenMeter analysis for ${repositoryName(analysis.repositoryUrl)}`
  const description = `${numberFormatter.format(analysis.metrics.totalTokens)} tokens, ${numberFormatter.format(analysis.metrics.totalFiles)} files and ${numberFormatter.format(analysis.metrics.totalLines)} lines analyzed.`

  document.title = title
  setMeta('description', description)
  setMeta('og:title', title, 'property')
  setMeta('og:description', description, 'property')
  setMeta('og:type', 'website', 'property')
  setMeta('og:url', publicUrl, 'property')
  setMeta('twitter:card', 'summary_large_image')
  setMeta('twitter:title', title)
  setMeta('twitter:description', description)
}

function resetDocumentMetadata() {
  document.title = 'TokenMeter — AI repository cost intelligence'
  setMeta('description', 'Simulate the cost of generating public GitHub repositories with modern AI models and workflow modes.')
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
    if (reason.code === 'INVALID_URL') return 'That repository URL is not valid. Use a public GitHub repository URL.'
    if (reason.code === 'REPOSITORY_NOT_ACCESSIBLE') return 'TokenMeter could not access that repository. Check that it is public and exists.'
    if (reason.code === 'CLONE_TIMEOUT') return 'The repository took too long to clone. Try a smaller repository.'
    return reason.message
  }
  return 'Something went wrong while analyzing the repository. Try again.'
}
