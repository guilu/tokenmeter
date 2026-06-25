import { CostHero } from '../CostHero'
import { FloorDisclaimer } from '../FloorDisclaimer'
import type { RepositoryAnalysisCostEstimateResponse, RepositoryAnalysisResponse } from '../../types/api'
import {
  compactNumberFormatter,
  costModes,
  currencyFormatter,
  numberFormatter,
} from '../../utils/formatters'
import type { CostMode, LanguageBreakdownItem } from '../../utils/formatters'

const dateFormatter = new Intl.DateTimeFormat('en-US', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

export interface OverviewSectionProps {
  analysis: RepositoryAnalysisResponse
  selectedMode: CostMode
  onSelectMode: (mode: CostMode) => void
  lowestEstimate: RepositoryAnalysisCostEstimateResponse | null
  highestEstimate: RepositoryAnalysisCostEstimateResponse | null
  topLanguage: LanguageBreakdownItem | undefined
  languageCount: number
  modelCount: number
  averageCost: number
  // Action row props (state stays in ResultsView, passed down as props)
  onNewAnalysis: () => void
  copyState: 'idle' | 'copied' | 'failed'
  onCopyPublicUrl: () => void
  selectedOpenGraphImageUrl: string
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

function ModeSwitch({
  selectedMode,
  onSelectMode,
}: {
  selectedMode: CostMode
  onSelectMode: (mode: CostMode) => void
}) {
  return (
    <div className="mt-6 rounded-2xl border border-text/10 bg-bg/60 p-1.5">
      <div className="grid grid-cols-3 gap-1.5">
        {costModes.map((mode) => {
          const active = selectedMode === mode
          return (
            <button
              className={`rounded-xl px-4 py-3 text-base font-semibold capitalize transition ${
                active
                  ? 'bg-primary text-bg shadow-lg shadow-primary/20'
                  : 'text-text/80 hover:bg-card/40 hover:text-text'
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

function repositoryName(repositoryUrl: string) {
  try {
    const url = new URL(repositoryUrl)
    return url.pathname.replace(/^\//, '') || repositoryUrl
  } catch {
    return repositoryUrl
  }
}

export function OverviewSection({
  analysis,
  selectedMode,
  onSelectMode,
  lowestEstimate,
  highestEstimate,
  topLanguage,
  languageCount,
  modelCount,
  averageCost,
  onNewAnalysis,
  copyState,
  onCopyPublicUrl,
  selectedOpenGraphImageUrl,
}: OverviewSectionProps) {
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button
          className="text-sm text-primary/80 transition hover:text-primary"
          onClick={onNewAnalysis}
          type="button"
        >
          ← Analyze another repository
        </button>
        <div className="flex flex-wrap items-center gap-2 print:hidden">
          <button
            className="rounded-2xl border border-text/10 bg-card/20 px-4 py-2 text-sm text-text/80 transition hover:bg-card/40"
            onClick={() => void onCopyPublicUrl()}
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
          <svg
            aria-hidden="true"
            className="h-7 w-7 shrink-0 text-text/60 sm:h-9 sm:w-9"
            fill="currentColor"
            viewBox="0 0 24 24"
          >
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
            Pricing: {analysis.pricing.primarySource} · captured{' '}
            {dateFormatter.format(new Date(analysis.pricing.capturedAt))}
          </p>
        ) : null}
      </header>

      <ModeSwitch selectedMode={selectedMode} onSelectMode={onSelectMode} />

      <CostHero
        analysis={analysis}
        lowestEstimate={lowestEstimate}
        highestEstimate={highestEstimate}
        selectedMode={selectedMode}
        topLanguage={topLanguage}
      />

      <div className="mt-8">
        <FloorDisclaimer />
      </div>

      <div className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4" id="metrics">
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
    </>
  )
}

