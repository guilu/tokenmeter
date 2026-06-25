import { useMemo } from 'react'

import { PrecisionBadge } from '../PrecisionBadge'
import type { RepositoryAnalysisCostEstimateResponse } from '../../types/api'
import { currencyFormatter } from '../../utils/formatters'
import type { CostMode } from '../../utils/formatters'
import {
  capitalize,
  cheapest,
  highest,
  modelComparisonNote,
  modelTier,
  percentOf,
} from '../../utils/resultsCost'
import type { ModelComparisonRow } from '../../utils/resultsCost'

type ComparisonSort = 'cost' | 'relative' | 'efficiency' | 'model'
type ProviderFilter = 'all' | string

export interface ModelsTabProps {
  estimates: RepositoryAnalysisCostEstimateResponse[]
  selectedMode: CostMode
  providers: string[]
  providerFilter: ProviderFilter
  onFilterProvider: (p: ProviderFilter) => void
  sortBy: ComparisonSort
  onSort: (s: ComparisonSort) => void
}

function RelativeCostBar({ percent, label }: { percent: number; label: string }) {
  return (
    <div>
      <div className="mb-2 flex items-center justify-between gap-3 text-xs text-text/60">
        <span>Relative cost</span>
        <span className="font-medium text-primary">{label}</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-text/10">
        <div
          className="h-full rounded-full bg-gradient-to-r from-secondary via-primary to-accent"
          style={{ width: `${Math.max(4, Math.min(100, percent))}%` }}
        />
      </div>
    </div>
  )
}

function TierBadge({ tier }: { tier: string }) {
  const tone =
    tier === 'Cheapest'
      ? 'secondary'
      : tier === 'Premium' || tier === 'High reasoning'
        ? 'accent'
        : tier === 'Experimental'
          ? 'secondary'
          : 'primary'
  const className =
    tone === 'secondary'
      ? 'border-secondary/20 bg-secondary/10 text-secondary'
      : tone === 'accent'
        ? 'border-accent/20 bg-accent/10 text-accent'
        : 'border-primary/20 bg-primary/10 text-primary'

  return (
    <span className={`rounded-full border px-3 py-1 text-xs font-medium ${className}`}>{tier}</span>
  )
}

function ModelComparisonCard({ row }: { row: ModelComparisonRow }) {
  return (
    <article className="rounded-2xl bg-bg/45 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <span className="flex items-center gap-2">
            <p className="truncate font-medium text-text" title={row.estimate.model}>
              {row.estimate.model}
            </p>
            <PrecisionBadge precision={row.estimate.precision ?? undefined} />
          </span>
          <p className="mt-1 text-sm capitalize text-text/50">{row.estimate.provider}</p>
        </div>
        <p className="shrink-0 text-lg font-semibold text-text">
          {currencyFormatter.format(row.estimate.totalCost)}
        </p>
      </div>
      <div className="mt-4">
        <RelativeCostBar
          percent={row.costPercent}
          label={`${row.relativeCost.toFixed(1)}× vs cheapest`}
        />
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <TierBadge tier={row.tier} />
        <span className="text-sm text-text/60">{row.note}</span>
      </div>
    </article>
  )
}

export function ModelsTab({
  estimates,
  providerFilter,
  providers,
  selectedMode,
  sortBy,
  onFilterProvider,
  onSort,
}: ModelsTabProps) {
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
            Compare {selectedMode} generation estimates side-by-side by provider, cost, relative
            efficiency and quality tier.
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
                <svg
                  className="h-4 w-4 text-text/50"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={2}
                  viewBox="0 0 24 24"
                >
                  <path d="M19 9l-7 7-7-7" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
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
                <svg
                  className="h-4 w-4 text-text/50"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={2}
                  viewBox="0 0 24 24"
                >
                  <path d="M19 9l-7 7-7-7" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
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
              <tr
                className="transition hover:bg-card/20"
                key={`${row.estimate.provider}-${row.estimate.model}`}
              >
                <td className="px-4 py-3 font-medium text-text">
                  <span className="flex items-center gap-2">
                    {row.estimate.model}
                    <PrecisionBadge precision={row.estimate.precision ?? undefined} />
                  </span>
                </td>
                <td className="px-4 py-3 capitalize text-text/80">{row.estimate.provider}</td>
                <td className="px-4 py-3 text-right font-medium text-text">
                  {currencyFormatter.format(row.estimate.totalCost)}
                </td>
                <td className="px-4 py-3">
                  <RelativeCostBar
                    percent={row.costPercent}
                    label={`${row.relativeCost.toFixed(1)}×`}
                  />
                </td>
                <td className="px-4 py-3">
                  <TierBadge tier={row.tier} />
                </td>
                <td className="px-4 py-3 text-text/60">{row.note}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
