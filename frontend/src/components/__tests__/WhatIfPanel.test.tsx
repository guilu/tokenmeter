/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import type { RepositoryAnalysisCostEstimateResponse } from '../../types/api'
import type { PricingMap } from '../../utils/whatIfCost'
import { WhatIfPanel } from '../WhatIfPanel'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeEstimate(
  override: Partial<RepositoryAnalysisCostEstimateResponse>,
): RepositoryAnalysisCostEstimateResponse {
  return {
    provider: 'openai',
    model: 'gpt-4o',
    mode: 'assisted',
    baseTokens: 1_000_000,
    estimatedInputTokens: 1_000_000,
    estimatedOutputTokens: 5_000_000,
    inputCost: 2.5,
    outputCost: 50.0,
    totalCost: 52.5,
    formula: 'assisted formula',
    engineeringEffort: {
      seniorEngineerHours: 10,
      engineeringDays: 1.25,
      manualImplementationEffort: '1.25 days',
      summary: '1.25 days',
      formula: 'effort formula',
      assumptions: {
        tokensPerSeniorEngineerHour: 100_000,
        hoursPerEngineeringDay: 8,
        modeComplexityMultiplier: 5,
      },
    },
    ...override,
  }
}

function makePricingMap(entries: Array<{ provider: string; model: string; inputPerMillion: number; outputPerMillion: number }>): PricingMap {
  const map: PricingMap = new Map()
  for (const entry of entries) {
    map.set(`${entry.provider}:${entry.model}`, {
      inputPerMillion: entry.inputPerMillion,
      outputPerMillion: entry.outputPerMillion,
    })
  }
  return map
}

// ---------------------------------------------------------------------------
// Default multipliers per mode
// ---------------------------------------------------------------------------

describe('WhatIfPanel — default multipliers per mode', () => {
  afterEach(() => {
    cleanup()
  })

  it('seeds outputMult=5 and inputMult=1 by default for ASSISTED mode', () => {
    const estimates = [makeEstimate({ mode: 'assisted' })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    const outputInputs = screen.getAllByRole('spinbutton').filter((el) => {
      const label = el.getAttribute('aria-label') ?? el.id ?? ''
      return /output/i.test(label)
    })
    const inputInputs = screen.getAllByRole('spinbutton').filter((el) => {
      const label = el.getAttribute('aria-label') ?? el.id ?? ''
      return /input/i.test(label)
    })

    expect(outputInputs[0]).toHaveValue(5)
    expect(inputInputs[0]).toHaveValue(1)
  })

  it('seeds outputMult=1 and inputMult=0 by default for RAW mode', () => {
    const estimates = [makeEstimate({ mode: 'raw' })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="raw" pricingMap={pricingMap} />)

    const outputInputs = screen.getAllByRole('spinbutton').filter((el) => {
      const label = el.getAttribute('aria-label') ?? el.id ?? ''
      return /output/i.test(label)
    })
    const inputInputs = screen.getAllByRole('spinbutton').filter((el) => {
      const label = el.getAttribute('aria-label') ?? el.id ?? ''
      return /input/i.test(label)
    })

    expect(outputInputs[0]).toHaveValue(1)
    expect(inputInputs[0]).toHaveValue(0)
  })

  it('seeds outputMult=20 and inputMult=4 by default for AGENTIC mode', () => {
    const estimates = [makeEstimate({ mode: 'agentic' })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="agentic" pricingMap={pricingMap} />)

    const outputInputs = screen.getAllByRole('spinbutton').filter((el) => {
      const label = el.getAttribute('aria-label') ?? el.id ?? ''
      return /output/i.test(label)
    })
    const inputInputs = screen.getAllByRole('spinbutton').filter((el) => {
      const label = el.getAttribute('aria-label') ?? el.id ?? ''
      return /input/i.test(label)
    })

    expect(outputInputs[0]).toHaveValue(20)
    expect(inputInputs[0]).toHaveValue(4)
  })
})

// ---------------------------------------------------------------------------
// Live recalculation
// ---------------------------------------------------------------------------

describe('WhatIfPanel — live recalculation on slider/input change', () => {
  afterEach(() => {
    cleanup()
  })

  it('updates the displayed cost when the output multiplier number input changes', () => {
    // baseTokens=1_000_000, outputPerMillion=10, inputPerMillion=2.5
    // default assisted: outputMult=5, inputMult=1 → cost = (5*10 + 1*2.5) = 52.5
    // change outputMult to 3 → (3*10 + 1*2.5) = 32.5
    const estimates = [makeEstimate({ mode: 'assisted', baseTokens: 1_000_000 })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    const outputNumberInput = screen.getAllByRole('spinbutton').find((el) => {
      const label = el.getAttribute('aria-label') ?? ''
      return /output/i.test(label)
    })!

    fireEvent.change(outputNumberInput, { target: { value: '3' } })

    // Cost = (3*10 + 1*2.5) * 1_000_000 / 1_000_000 = 32.50
    expect(screen.getByText(/\$32\.50/)).toBeInTheDocument()
  })

  it('updates the displayed cost when the input multiplier slider changes', () => {
    // default assisted: outputMult=5, inputMult=1 → 52.5
    // change inputMult to 0 → (5*10 + 0*2.5) = 50.0
    const estimates = [makeEstimate({ mode: 'assisted', baseTokens: 1_000_000 })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    const inputSlider = screen.getAllByRole('slider').find((el) => {
      const label = el.getAttribute('aria-label') ?? ''
      return /input/i.test(label)
    })!

    fireEvent.change(inputSlider, { target: { value: '0' } })

    // Cost = (5*10 + 0*2.5) * 1_000_000 / 1_000_000 = 50.00
    expect(screen.getByText(/\$50\.00/)).toBeInTheDocument()
  })

  it('displays the spec example: outputMult=3, inputMult=1, out=$1/M, in=$0.50/M → $3.50', () => {
    // baseTokens=1_000_000, outputPerMillion=1.0, inputPerMillion=0.5
    // start default assisted (outputMult=5, inputMult=1) then change outputMult to 3
    const estimates = [makeEstimate({ mode: 'assisted', baseTokens: 1_000_000 })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 0.5, outputPerMillion: 1.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    const outputNumberInput = screen.getAllByRole('spinbutton').find((el) => {
      const label = el.getAttribute('aria-label') ?? ''
      return /output/i.test(label)
    })!

    fireEvent.change(outputNumberInput, { target: { value: '3' } })

    expect(screen.getByText(/\$3\.50/)).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Reset button
// ---------------------------------------------------------------------------

describe('WhatIfPanel — Reset restores defaults', () => {
  afterEach(() => {
    cleanup()
  })

  it('resets outputMult to 5 after change, for ASSISTED mode', () => {
    const estimates = [makeEstimate({ mode: 'assisted' })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    const outputNumberInput = screen.getAllByRole('spinbutton').find((el) => {
      const label = el.getAttribute('aria-label') ?? ''
      return /output/i.test(label)
    })!

    fireEvent.change(outputNumberInput, { target: { value: '10' } })
    expect(outputNumberInput).toHaveValue(10)

    const resetButton = screen.getByRole('button', { name: /reset/i })
    fireEvent.click(resetButton)

    expect(outputNumberInput).toHaveValue(5)
  })

  it('resets inputMult to 1 after change, for ASSISTED mode', () => {
    const estimates = [makeEstimate({ mode: 'assisted' })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    const inputNumberInput = screen.getAllByRole('spinbutton').find((el) => {
      const label = el.getAttribute('aria-label') ?? ''
      return /input/i.test(label)
    })!

    fireEvent.change(inputNumberInput, { target: { value: '8' } })

    const resetButton = screen.getByRole('button', { name: /reset/i })
    fireEvent.click(resetButton)

    expect(inputNumberInput).toHaveValue(1)
  })
})

// ---------------------------------------------------------------------------
// print:hidden
// ---------------------------------------------------------------------------

describe('WhatIfPanel — print:hidden on root element', () => {
  afterEach(() => {
    cleanup()
  })

  it('root element carries print:hidden class', () => {
    const estimates = [makeEstimate({ mode: 'assisted' })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    const { container } = render(
      <WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />,
    )

    const root = container.firstElementChild
    expect(root).not.toBeNull()
    expect(root?.className).toContain('print:hidden')
  })
})

// ---------------------------------------------------------------------------
// Model with no pricing — "unavailable" row
// ---------------------------------------------------------------------------

describe('WhatIfPanel — unavailable model', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders "unavailable" indicator for a model absent from pricingMap', () => {
    const estimates = [makeEstimate({ provider: 'deepseek', model: 'deepseek-r1', mode: 'assisted' })]
    const emptyMap: PricingMap = new Map()

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={emptyMap} />)

    expect(screen.getByText(/unavailable/i)).toBeInTheDocument()
  })

  it('renders available model normally alongside unavailable one', () => {
    const estimates = [
      makeEstimate({ provider: 'openai', model: 'gpt-4o', mode: 'assisted', baseTokens: 1_000_000 }),
      makeEstimate({ provider: 'deepseek', model: 'deepseek-r1', mode: 'assisted' }),
    ]
    const pricingMap = makePricingMap([
      { provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 },
    ])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    // deepseek-r1 should show unavailable
    expect(screen.getByText(/unavailable/i)).toBeInTheDocument()
    // gpt-4o should show a dollar cost, not unavailable text for that row
    // The recalculated cost for gpt-4o with default assisted (5/1): (5*10 + 1*2.5) = 52.5
    expect(screen.getByText(/\$52\.50/)).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// All models unavailable — empty state
// ---------------------------------------------------------------------------

describe('WhatIfPanel — all models unavailable empty state', () => {
  afterEach(() => {
    cleanup()
  })

  it('shows "what-if unavailable" state when pricingMap is empty', () => {
    const estimates = [makeEstimate({ mode: 'assisted' })]
    const emptyMap: PricingMap = new Map()

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={emptyMap} />)

    expect(screen.getByText(/what.if unavailable/i)).toBeInTheDocument()
  })

  it('shows "what-if unavailable" state when no estimate has matching pricing', () => {
    const estimates = [
      makeEstimate({ provider: 'unknown-provider', model: 'unknown-model', mode: 'assisted' }),
    ]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    expect(screen.getByText(/what.if unavailable/i)).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// step=0.5 on inputs
// ---------------------------------------------------------------------------

describe('WhatIfPanel — step=0.5 inputs', () => {
  afterEach(() => {
    cleanup()
  })

  it('output number input has step=0.5', () => {
    const estimates = [makeEstimate({ mode: 'assisted' })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    const outputNumberInput = screen.getAllByRole('spinbutton').find((el) => {
      const label = el.getAttribute('aria-label') ?? ''
      return /output/i.test(label)
    })!

    expect(outputNumberInput).toHaveAttribute('step', '0.5')
  })

  it('input multiplier number input has step=0.5', () => {
    const estimates = [makeEstimate({ mode: 'assisted' })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    const inputNumberInput = screen.getAllByRole('spinbutton').find((el) => {
      const label = el.getAttribute('aria-label') ?? ''
      return /input/i.test(label)
    })!

    expect(inputNumberInput).toHaveAttribute('step', '0.5')
  })

  it('output slider has step=0.5', () => {
    const estimates = [makeEstimate({ mode: 'assisted' })]
    const pricingMap = makePricingMap([{ provider: 'openai', model: 'gpt-4o', inputPerMillion: 2.5, outputPerMillion: 10.0 }])

    render(<WhatIfPanel estimates={estimates} selectedMode="assisted" pricingMap={pricingMap} />)

    const outputSlider = screen.getAllByRole('slider').find((el) => {
      const label = el.getAttribute('aria-label') ?? ''
      return /output/i.test(label)
    })!

    expect(outputSlider).toHaveAttribute('step', '0.5')
  })
})
