import type { RepositoryAnalysisCostEstimateResponse, RepositoryAnalysisResponse } from '../types/api'
import type { LanguageBreakdownItem } from './formatters'

// ---------------------------------------------------------------------------
// Type exports
// ---------------------------------------------------------------------------

export type ModelComparisonRow = {
  estimate: RepositoryAnalysisCostEstimateResponse
  relativeCost: number
  costPercent: number
  efficiencyScore: number
  tier: string
  note: string
}

// ---------------------------------------------------------------------------
// Helper functions moved from DashboardPage.tsx to avoid import cycles.
// These are pure functions: domain helpers imported by tab components and
// by DashboardPage itself.
// ---------------------------------------------------------------------------

export function cheapest(
  estimates: RepositoryAnalysisCostEstimateResponse[],
): RepositoryAnalysisCostEstimateResponse | null {
  return estimates.reduce<RepositoryAnalysisCostEstimateResponse | null>((best, estimate) => {
    if (best === null) return estimate
    return estimate.totalCost < best.totalCost ? estimate : best
  }, null)
}

export function highest(
  estimates: RepositoryAnalysisCostEstimateResponse[],
): RepositoryAnalysisCostEstimateResponse | null {
  return estimates.reduce<RepositoryAnalysisCostEstimateResponse | null>((best, estimate) => {
    if (best === null) return estimate
    return estimate.totalCost > best.totalCost ? estimate : best
  }, null)
}

export function average(values: number[]): number {
  if (values.length === 0) return 0
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

export function percentOf(value: number, total: number): number {
  if (total <= 0) return 0
  return (value / total) * 100
}

export function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1)
}

export function uniqueProviders(estimates: RepositoryAnalysisCostEstimateResponse[]): string[] {
  return Array.from(new Set(estimates.map((estimate) => estimate.provider))).sort((left, right) =>
    left.localeCompare(right),
  )
}

export function modelTier(
  estimate: RepositoryAnalysisCostEstimateResponse,
  baselineCost: number,
  maxCost: number,
): string {
  const model = estimate.model.toLowerCase()
  const provider = estimate.provider.toLowerCase()
  const relativeCost = baselineCost > 0 ? estimate.totalCost / baselineCost : 1
  const premiumThreshold = maxCost * 0.82

  if (relativeCost <= 1.05) return 'Cheapest'
  if (
    model.includes('reason') ||
    model.includes('opus') ||
    model.includes('o1') ||
    model.includes('o3')
  )
    return 'High reasoning'
  if (
    model.includes('preview') ||
    model.includes('experimental') ||
    provider.includes('xai')
  )
    return 'Experimental'
  if (estimate.totalCost >= premiumThreshold) return 'Premium'
  return 'Balanced'
}

export function modelComparisonNote(
  estimate: RepositoryAnalysisCostEstimateResponse,
  baselineCost: number,
  maxCost: number,
): string {
  const tier = modelTier(estimate, baselineCost, maxCost)
  if (tier === 'Cheapest') return 'Lowest simulated cost for this workflow.'
  if (tier === 'High reasoning') return 'Higher reasoning profile; useful for complex repositories.'
  if (tier === 'Premium') return 'Higher cost option, likely best reserved for quality-sensitive work.'
  if (tier === 'Experimental') return 'Useful benchmark candidate; validate quality before relying on it.'
  return 'Middle-ground cost profile for routine generation workflows.'
}

export function formatDecimal(value: number, maximumFractionDigits: number): string {
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits,
    minimumFractionDigits: value % 1 === 0 ? 0 : Math.min(1, maximumFractionDigits),
  }).format(value)
}

export function languageBreakdown(analysis: RepositoryAnalysisResponse): LanguageBreakdownItem[] {
  return Object.values(analysis.metrics.languages).sort((left, right) => right.tokens - left.tokens)
}
