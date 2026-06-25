import { useMemo, useState } from 'react'

import type { RepositoryAnalysisCostEstimateResponse } from '../types/api'
import type { CostMode } from '../utils/formatters'
import { pricingKey, recalcCost } from '../utils/whatIfCost'
import type { PricingMap } from '../utils/whatIfCost'

// Canonical default multipliers per mode (matching CostEstimationMode domain constants)
const DEFAULT_OUTPUT_MULT: Record<CostMode, number> = {
  raw: 1,
  assisted: 5,
  agentic: 20,
}

const DEFAULT_INPUT_MULT: Record<CostMode, number> = {
  raw: 0,
  assisted: 1,
  agentic: 4,
}

// Max slider values (non-binding UI caps; number inputs accept any non-negative)
const OUTPUT_SLIDER_MAX = 40
const INPUT_SLIDER_MAX = 10

const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

interface WhatIfPanelProps {
  estimates: RepositoryAnalysisCostEstimateResponse[]
  selectedMode: CostMode
  pricingMap: PricingMap
}

export function WhatIfPanel({ estimates, selectedMode, pricingMap }: WhatIfPanelProps) {
  const [outputMult, setOutputMult] = useState(() => DEFAULT_OUTPUT_MULT[selectedMode])
  const [inputMult, setInputMult] = useState(() => DEFAULT_INPUT_MULT[selectedMode])

  function handleReset() {
    setOutputMult(DEFAULT_OUTPUT_MULT[selectedMode])
    setInputMult(DEFAULT_INPUT_MULT[selectedMode])
  }

  const rows = useMemo(() => {
    return estimates.map((estimate) => {
      const key = pricingKey(estimate.provider, estimate.model)
      const pricing = pricingMap.get(key) ?? null
      const recalculated = pricing
        ? recalcCost(estimate.baseTokens, outputMult, inputMult, pricing)
        : null
      return { estimate, pricing, recalculated }
    })
  }, [estimates, outputMult, inputMult, pricingMap])

  const cheapestRecalc = useMemo(() => {
    const withPricing = rows.filter((row) => row.recalculated !== null)
    if (withPricing.length === 0) return null
    return withPricing.reduce((best, row) =>
      (row.recalculated as number) < (best.recalculated as number) ? row : best,
    )
  }, [rows])

  const allUnavailable = rows.every((row) => row.pricing === null)

  return (
    <section className="print:hidden mt-8 rounded-3xl border border-primary/20 bg-card/20 p-5 shadow-2xl shadow-bg/20 sm:p-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <p className="text-sm text-text/60">Interactive cost explorer</p>
          <h2 className="mt-1 text-2xl font-semibold text-text">What-If Multiplier Panel</h2>
          <p className="mt-2 text-sm leading-6 text-text/60">
            Adjust the output and input multipliers to explore how workflow changes affect cost.
            Uses exact server-side pricing — no backend calls on slider change.
          </p>
        </div>
        <button
          className="shrink-0 rounded-2xl border border-text/10 bg-card/20 px-4 py-2 text-sm text-text/80 transition hover:bg-card/40"
          onClick={handleReset}
          type="button"
        >
          Reset
        </button>
      </div>

      <div className="mt-6 grid gap-6 sm:grid-cols-2">
        {/* Output multiplier */}
        <div className="space-y-3">
          <label className="block text-sm font-medium text-text" htmlFor="what-if-output-mult">
            Output multiplier
            <span className="ml-2 text-xs text-text/50">({selectedMode} default: {DEFAULT_OUTPUT_MULT[selectedMode]}×)</span>
          </label>
          <div className="flex items-center gap-3">
            <input
              aria-label="Output multiplier slider"
              className="flex-1 accent-primary"
              id="what-if-output-slider"
              max={OUTPUT_SLIDER_MAX}
              min={0}
              onChange={(e) => setOutputMult(Number(e.target.value))}
              step={0.5}
              type="range"
              value={outputMult}
            />
            <input
              aria-label="Output multiplier"
              className="w-20 rounded-xl border border-text/10 bg-bg px-2 py-1 text-right text-sm text-text outline-none transition focus:border-primary/60"
              id="what-if-output-mult"
              min={0}
              onChange={(e) => setOutputMult(Number(e.target.value))}
              step={0.5}
              type="number"
              value={outputMult}
            />
          </div>
        </div>

        {/* Input multiplier */}
        <div className="space-y-3">
          <label className="block text-sm font-medium text-text" htmlFor="what-if-input-mult">
            Input multiplier
            <span className="ml-2 text-xs text-text/50">({selectedMode} default: {DEFAULT_INPUT_MULT[selectedMode]}×)</span>
          </label>
          <div className="flex items-center gap-3">
            <input
              aria-label="Input multiplier slider"
              className="flex-1 accent-primary"
              id="what-if-input-slider"
              max={INPUT_SLIDER_MAX}
              min={0}
              onChange={(e) => setInputMult(Number(e.target.value))}
              step={0.5}
              type="range"
              value={inputMult}
            />
            <input
              aria-label="Input multiplier"
              className="w-20 rounded-xl border border-text/10 bg-bg px-2 py-1 text-right text-sm text-text outline-none transition focus:border-primary/60"
              id="what-if-input-mult"
              min={0}
              onChange={(e) => setInputMult(Number(e.target.value))}
              step={0.5}
              type="number"
              value={inputMult}
            />
          </div>
        </div>
      </div>

      {allUnavailable ? (
        <div className="mt-6 rounded-2xl border border-dashed border-text/10 p-6 text-center text-sm text-text/60">
          What-If unavailable — no server-side pricing data for the selected models.
        </div>
      ) : (
        <div className="mt-6 overflow-hidden rounded-2xl">
          <table className="min-w-full divide-y divide-text/10 text-sm">
            <thead className="bg-card/20 text-left text-text/60">
              <tr>
                <th className="px-4 py-3 font-medium">Model</th>
                <th className="px-4 py-3 font-medium">Provider</th>
                <th className="px-4 py-3 text-right font-medium">Recalculated cost</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-text/10 text-text/80">
              {rows.map((row) => {
                const isCheapest =
                  cheapestRecalc !== null &&
                  row.estimate.provider === cheapestRecalc.estimate.provider &&
                  row.estimate.model === cheapestRecalc.estimate.model

                return (
                  <tr
                    className={`transition hover:bg-card/20 ${isCheapest ? 'bg-primary/5' : ''}`}
                    key={`${row.estimate.provider}-${row.estimate.model}`}
                  >
                    <td className="px-4 py-3 font-medium text-text">
                      {row.estimate.model}
                      {isCheapest ? (
                        <span className="ml-2 rounded-full border border-secondary/20 bg-secondary/10 px-2 py-0.5 text-xs text-secondary">
                          Cheapest
                        </span>
                      ) : null}
                    </td>
                    <td className="px-4 py-3 capitalize text-text/60">{row.estimate.provider}</td>
                    <td className="px-4 py-3 text-right font-medium text-text">
                      {row.recalculated !== null ? (
                        currencyFormatter.format(row.recalculated)
                      ) : (
                        <span className="text-text/40">Unavailable</span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
