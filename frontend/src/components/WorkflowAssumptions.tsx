import type { RepositoryAnalysisCostEstimateResponse } from '../types/api'
import type { CostMode } from '../utils/formatters'

interface WorkflowAssumptionsProps {
  selectedMode: CostMode
  estimate: RepositoryAnalysisCostEstimateResponse | null
  rawBaselineEstimate: RepositoryAnalysisCostEstimateResponse | null
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

export function WorkflowAssumptions({
  selectedMode,
  estimate,
  rawBaselineEstimate,
}: WorkflowAssumptionsProps) {
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

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1)
}
