import type { RepositoryAnalysisCostEstimateResponse, RepositoryAnalysisResponse } from '../types/api'
import type { CostMode } from '../utils/formatters'
import { compactNumberFormatter, currencyFormatter, numberFormatter } from '../utils/formatters'

interface CostHeroProps {
  analysis: RepositoryAnalysisResponse
  lowestEstimate: RepositoryAnalysisCostEstimateResponse | null
  highestEstimate: RepositoryAnalysisCostEstimateResponse | null
  selectedMode: CostMode
  languageCount: number
  modelCount: number
  averageCost: number
}

export function CostHero({
  analysis,
  lowestEstimate,
  highestEstimate,
  selectedMode,
  languageCount,
  modelCount,
  averageCost,
}: CostHeroProps) {
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

        <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4" id="metrics">
          <MetricCard
            label="Tokens"
            value={compactNumberFormatter.format(analysis.metrics.totalTokens)}
            hint={`${numberFormatter.format(analysis.metrics.totalTokens)} tracked`}
          />
          <MetricCard
            label="Files"
            value={numberFormatter.format(analysis.metrics.totalFiles)}
            hint={`${numberFormatter.format(analysis.metrics.totalLines)} total lines`}
          />
          <MetricCard
            label="Languages"
            value={numberFormatter.format(languageCount)}
            hint={`${analysis.metrics.tokenEncoding} encoding`}
          />
          <MetricCard
            label="Avg. cost"
            value={currencyFormatter.format(averageCost)}
            hint={`${selectedMode} mode across ${modelCount} models`}
          />
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

function MetricCard({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <article className="rounded-2xl bg-card/20 p-4 sm:p-5">
      <p className="text-sm text-text/60">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-text sm:text-3xl">{value}</p>
      <p className="mt-2 text-sm text-text/50">{hint}</p>
    </article>
  )
}

function repositoryName(repositoryUrl: string) {
  try {
    const url = new URL(repositoryUrl)
    return url.pathname.replace(/^\//, '') || repositoryUrl
  } catch {
    return repositoryUrl
  }
}
