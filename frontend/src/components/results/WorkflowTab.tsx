import { HeuristicDisclaimer } from '../HeuristicDisclaimer'
import { WorkflowAssumptions } from '../WorkflowAssumptions'
import type { RepositoryAnalysisCostEstimateResponse } from '../../types/api'
import { formatDecimal } from '../../utils/resultsCost'
import { cheapest } from '../../utils/resultsCost'
import type { CostMode } from '../../utils/formatters'

export interface WorkflowTabProps {
  estimates: RepositoryAnalysisCostEstimateResponse[]
  selectedMode: CostMode
  primaryEstimate: RepositoryAnalysisCostEstimateResponse | null
  rawBaselineEstimate: RepositoryAnalysisCostEstimateResponse | null
}

function average(values: number[]): number {
  if (values.length === 0) return 0
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

function AssumptionMetric({ label, value }: { label: string; value: string }) {
  return (
    <article className="rounded-2xl bg-card/20 p-4">
      <p className="text-xs uppercase tracking-[0.2em] text-text/50">{label}</p>
      <p className="mt-2 text-lg font-semibold text-text">{value}</p>
    </article>
  )
}

function EngineeringEffortPanel({
  estimates,
  selectedMode,
}: {
  estimates: RepositoryAnalysisCostEstimateResponse[]
  selectedMode: CostMode
}) {
  const representativeEstimate = cheapest(estimates) ?? estimates[0] ?? null
  const highestEffortEstimate = estimates.reduce<RepositoryAnalysisCostEstimateResponse | null>(
    (best, estimate) => {
      if (best === null) return estimate
      return estimate.engineeringEffort.seniorEngineerHours >
        best.engineeringEffort.seniorEngineerHours
        ? estimate
        : best
    },
    null,
  )
  const averageHours = average(
    estimates.map((estimate) => estimate.engineeringEffort.seniorEngineerHours),
  )
  const assumptions = representativeEstimate?.engineeringEffort.assumptions

  return (
    <section className="mt-8 rounded-3xl border border-secondary/20 bg-secondary/[0.04] p-5 shadow-2xl shadow-bg/20 sm:p-6">
      <div className="grid gap-6 lg:grid-cols-[0.95fr_1.05fr] lg:items-start">
        <div>
          <p className="text-sm text-secondary/80">Engineering effort equivalence</p>
          <h2 className="mt-1 text-2xl font-semibold text-text">
            Human-readable scale for {selectedMode} mode
          </h2>
          <p className="mt-3 text-sm leading-6 text-text/60">
            TokenMeter translates token and workflow estimates into senior-engineering time, so cost
            numbers have a practical delivery-scale reference instead of feeling like abstract cents.
          </p>
          {representativeEstimate ? (
            <div className="mt-5 rounded-2xl border border-secondary/20 bg-secondary/10 p-5">
              <p className="text-xs uppercase tracking-[0.2em] text-secondary/70">
                Lowest-cost model equivalence
              </p>
              <p className="mt-3 text-3xl font-semibold text-text">
                {representativeEstimate.engineeringEffort.summary}
              </p>
              <p className="mt-2 text-sm text-secondary/80">
                {representativeEstimate.provider} · {representativeEstimate.model}
              </p>
            </div>
          ) : null}
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <AssumptionMetric
            label="Average senior hours"
            value={`${formatDecimal(averageHours, 1)} h`}
          />
          <AssumptionMetric
            label="Max manual effort"
            value={
              highestEffortEstimate
                ? highestEffortEstimate.engineeringEffort.manualImplementationEffort
                : 'No estimate'
            }
          />
          <AssumptionMetric
            label="Engineering day"
            value={
              assumptions
                ? `${formatDecimal(assumptions.hoursPerEngineeringDay, 1)} h/day`
                : 'Configurable'
            }
          />
          <AssumptionMetric
            label="Mode multiplier"
            value={
              assumptions
                ? `${formatDecimal(assumptions.modeComplexityMultiplier, 2)}×`
                : 'Mode-aware'
            }
          />
        </div>
      </div>
    </section>
  )
}

export function WorkflowTab({
  estimates,
  selectedMode,
  primaryEstimate,
  rawBaselineEstimate,
}: WorkflowTabProps) {
  return (
    <>
      <WorkflowAssumptions
        selectedMode={selectedMode}
        estimate={primaryEstimate}
        rawBaselineEstimate={rawBaselineEstimate}
      />
      <EngineeringEffortPanel estimates={estimates} selectedMode={selectedMode} />
      {estimates.length > 0 ? (
        <div className="mt-8">
          <HeuristicDisclaimer estimates={estimates} />
        </div>
      ) : null}
    </>
  )
}
