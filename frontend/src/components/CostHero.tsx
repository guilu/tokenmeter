import type { RepositoryAnalysisCostEstimateResponse, RepositoryAnalysisResponse } from '../types/api'
import type { CostMode, LanguageBreakdownItem } from '../utils/formatters'
import { compactNumberFormatter, currencyFormatter, numberFormatter } from '../utils/formatters'

interface CostHeroProps {
  analysis: RepositoryAnalysisResponse
  floorEstimate: RepositoryAnalysisCostEstimateResponse | null
  ceilingEstimate: RepositoryAnalysisCostEstimateResponse | null
  selectedModeEstimate: RepositoryAnalysisCostEstimateResponse | null
  selectedMode: CostMode
  topLanguage: LanguageBreakdownItem | undefined
}

// Position (0–100%) of the selected-mode cost inside the floor→ceiling band.
// Returns null when the band is degenerate (no spread) or any anchor is missing.
function markerPercent(
  floor: RepositoryAnalysisCostEstimateResponse | null,
  ceiling: RepositoryAnalysisCostEstimateResponse | null,
  selected: RepositoryAnalysisCostEstimateResponse | null,
): number | null {
  if (!floor || !ceiling || !selected) return null
  const span = ceiling.totalCost - floor.totalCost
  if (span <= 0) return null
  const raw = ((selected.totalCost - floor.totalCost) / span) * 100
  return Math.max(0, Math.min(100, raw))
}

export function CostHero({
  analysis,
  floorEstimate,
  ceilingEstimate,
  selectedModeEstimate,
  selectedMode,
  topLanguage,
}: CostHeroProps) {
  const repositoryLabel = repositoryName(analysis.repositoryUrl)
  const marker = markerPercent(floorEstimate, ceilingEstimate, selectedModeEstimate)

  return (
    <section className="relative mt-8 overflow-hidden rounded-[2rem] border border-primary/20 bg-bg/80 p-6 shadow-2xl shadow-bg sm:p-8">
      <div
        className="absolute inset-0 print:hidden"
        style={{ background: 'radial-gradient(circle at top left, color-mix(in srgb, var(--tm-primary) 18%, transparent), transparent 38%), radial-gradient(circle at bottom right, color-mix(in srgb, var(--tm-secondary) 14%, transparent), transparent 36%)' }}
      />
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary/80 to-transparent print:hidden" />

      <div className="relative">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="inline-flex rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-sm text-primary">
            Estimated generation cost range
          </p>
          <p className="inline-flex items-center gap-2 rounded-full border border-secondary/20 bg-secondary/10 px-3 py-1 text-sm text-secondary">
            <span className="font-medium">Viewing:</span>
            <span className="capitalize">{selectedMode} mode</span>
          </p>
        </div>

        {/* Headline — the selected mode cost. Moves with the mode switch. */}
        <div
          className="mt-5 rounded-2xl bg-card/20 p-5 transition-all duration-500"
          key={`${selectedMode}-${selectedModeEstimate?.provider ?? 'none'}`}
        >
          <p className="text-xs uppercase tracking-[0.2em] text-text/50">
            <span className="capitalize">{selectedMode}</span> mode estimate
          </p>
          <p className="mt-2 text-5xl font-semibold tracking-tight text-text sm:text-6xl">
            {selectedModeEstimate ? currencyFormatter.format(selectedModeEstimate.totalCost) : '—'}
          </p>
          <p className="mt-3 truncate text-sm text-text/60">
            {selectedModeEstimate
              ? `${selectedModeEstimate.provider} · ${selectedModeEstimate.model} · cheapest in ${selectedMode} mode`
              : 'No estimate for this mode'}
          </p>
        </div>

        {/* Floor → ceiling band with a marker for the selected mode. */}
        <div className="mt-5 rounded-2xl bg-card/20 p-5">
          <div className="flex items-end justify-between gap-3">
            <div className="min-w-0">
              <span className="rounded-full border border-primary/30 bg-primary/20 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                Floor (RAW)
              </span>
              <p className="mt-2 text-2xl font-semibold text-text">
                {floorEstimate ? currencyFormatter.format(floorEstimate.totalCost) : '—'}
              </p>
            </div>
            <div className="min-w-0 text-right">
              <span className="rounded-full border border-accent/30 bg-accent/20 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-accent">
                Ceiling (AGENTIC)
              </span>
              <p className="mt-2 text-2xl font-semibold text-text">
                {ceilingEstimate ? currencyFormatter.format(ceilingEstimate.totalCost) : '—'}
              </p>
            </div>
          </div>

          <div className="relative mt-4 h-2 rounded-full bg-gradient-to-r from-primary/40 via-secondary/40 to-accent/40">
            {marker !== null ? (
              <span
                aria-hidden="true"
                className="absolute top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-bg bg-secondary shadow-lg shadow-bg/40"
                data-testid="mode-marker"
                style={{ left: `${marker}%` }}
              />
            ) : null}
          </div>
          {marker !== null && selectedModeEstimate ? (
            <p className="mt-3 text-xs text-text/50">
              <span className="capitalize">{selectedMode}</span> mode sits at{' '}
              {currencyFormatter.format(selectedModeEstimate.totalCost)} within the floor→ceiling range.
            </p>
          ) : null}
        </div>

        <p className="mt-4 max-w-3xl text-sm leading-6 text-text/60">
          TokenMeter estimates what it would cost to regenerate {repositoryLabel} with AI, including repository size,
          token footprint and workflow overhead. The floor is the cheapest raw pass; the ceiling is the priciest agentic run.
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
    <article className="min-w-0 rounded-2xl bg-card/20 p-4">
      <p className="text-xs uppercase tracking-[0.2em] text-text/50">{label}</p>
      <p className="mt-2 truncate text-lg font-semibold text-text" title={value}>
        {value}
      </p>
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
