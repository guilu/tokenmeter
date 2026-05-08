import { useEffect, useMemo, useState } from 'react'

import { analyzeRepository, DEFAULT_REPOSITORY_URL, getPricing } from '../services/api'
import type { PricingModelResponse, RepositoryAnalysisResponse } from '../types/api'

interface DashboardData {
  analysis: RepositoryAnalysisResponse
  pricingModels: PricingModelResponse[]
}

let dashboardDataPromise: Promise<DashboardData> | null = null

function loadDashboardData() {
  dashboardDataPromise ??= Promise.all([analyzeRepository(DEFAULT_REPOSITORY_URL), getPricing()]).then(
    ([analysis, pricing]) => ({ analysis, pricingModels: pricing.models }),
  )
  return dashboardDataPromise
}

const numberFormatter = new Intl.NumberFormat('en-US')
const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 2,
})

export function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    let active = true

    loadDashboardData()
      .then((dashboardData) => {
        if (active) setData(dashboardData)
      })
      .catch((reason: unknown) => {
        if (active) setError(reason)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  const metrics = useMemo(() => {
    const totalTokens = data?.analysis.metrics.totalTokens ?? 0
    const outputPrices = data?.pricingModels.map((model) => model.outputTokenPricePerMillion) ?? []
    const cheapestOutputPrice = outputPrices.length > 0 ? Math.min(...outputPrices) : null
    const estimatedCost = cheapestOutputPrice === null ? 0 : (totalTokens * cheapestOutputPrice) / 1_000_000
    const providers = new Set(data?.pricingModels.map((model) => model.provider) ?? [])

    return [
      {
        label: 'Tokens tracked',
        value: loading ? 'Analyzing…' : numberFormatter.format(totalTokens),
        hint: `Default repo: ${DEFAULT_REPOSITORY_URL}`,
      },
      {
        label: 'Estimated cost',
        value: loading ? 'Calculating…' : currencyFormatter.format(estimatedCost),
        hint: cheapestOutputPrice === null ? 'Pricing unavailable' : 'Minimum output-token estimate',
      },
      {
        label: 'Providers',
        value: loading ? 'Loading…' : numberFormatter.format(providers.size),
        hint: providers.size === 0 ? 'Pricing unavailable' : 'Configured AI pricing providers',
      },
    ]
  }, [data, loading])

  return (
    <section className="mx-auto max-w-6xl px-6 py-16" id="overview">
      <div className="max-w-3xl">
        <p className="mb-4 inline-flex rounded-full border border-cyan-400/20 bg-cyan-400/10 px-3 py-1 text-sm text-cyan-200">
          Live repository analysis
        </p>
        <h1 className="text-4xl font-semibold tracking-tight text-white sm:text-6xl">
          Measure AI usage before the bill punches you in the face.
        </h1>
        <p className="mt-6 text-lg leading-8 text-slate-400">
          TokenMeter analyzes {DEFAULT_REPOSITORY_URL} on startup and estimates the minimum generation cost from configured AI pricing.
        </p>
        {error ? (
          <p className="mt-4 rounded-xl border border-red-400/20 bg-red-400/10 px-4 py-3 text-sm text-red-200">
            Could not analyze the default repository. Check the backend logs and GitHub connectivity.
          </p>
        ) : null}
      </div>

      <div className="mt-12 grid gap-4 md:grid-cols-3" id="metrics">
        {metrics.map((metric) => (
          <article
            className="rounded-2xl border border-white/10 bg-white/[0.03] p-6 shadow-2xl shadow-black/20"
            key={metric.label}
          >
            <p className="text-sm text-slate-400">{metric.label}</p>
            <p className="mt-3 text-3xl font-semibold text-white">{metric.value}</p>
            <p className="mt-2 text-sm text-slate-500">{metric.hint}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
