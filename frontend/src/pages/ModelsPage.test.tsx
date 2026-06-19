/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { PricingResponse } from '../types/api'
import { ModelsPage } from './ModelsPage'

// ProviderIcon pulls @lobehub/icons → @lobehub/ui, whose ESM resolution breaks
// under vitest. The collapse behaviour under test does not need real icons.
vi.mock('../components/ProviderIcon', () => ({ ProviderIcon: () => null }))

function pricing(lastRefreshedAt: string): PricingResponse {
  return {
    lastRefreshedAt,
    primarySource: 'litellm',
    models: [
      {
        provider: 'openai',
        model: 'gpt-test',
        inputTokenPricePerMillion: 1,
        outputTokenPricePerMillion: 2,
        source: 'REMOTE',
        fetchedAt: lastRefreshedAt,
      },
    ],
  }
}

function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  } as unknown as Response
}

function emptyResponse(status: number): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.reject(new Error('no body')),
  } as unknown as Response
}

describe('ModelsPage — Generation Economics modes collapsible', () => {
  beforeEach(() => {
    // Pricing fetch never resolves — the economics section is static and
    // independent of the table data, so this keeps the test focused.
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('renders the mode cards expanded by default', () => {
    render(<ModelsPage />)
    const toggle = screen.getByRole('button', { name: /Generation Economics modes/i })
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText(/Direct regeneration/i)).toBeInTheDocument()
  })

  it('hides the mode cards when the heading is clicked and shows them again on a second click', () => {
    render(<ModelsPage />)
    const toggle = screen.getByRole('button', { name: /Generation Economics modes/i })

    fireEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText(/Direct regeneration/i)).not.toBeInTheDocument()

    fireEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText(/Direct regeneration/i)).toBeInTheDocument()
  })
})

describe('ModelsPage — on-demand pricing refresh (TKM-62)', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  function refreshButton() {
    return screen.getByRole('button', { name: /refresh prices now/i })
  }

  it('refreshes prices, reloads pricing and shows success counts', async () => {
    let pricingCalls = 0
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.includes('/api/admin/pricing/refresh')) {
        expect(init?.method).toBe('POST')
        return Promise.resolve(
          jsonResponse(202, { fetchedAt: '2026-06-19T10:00:00Z', updated: 12, skipped: 3, failed: 0 }),
        )
      }
      if (url.includes('/api/pricing')) {
        pricingCalls += 1
        return Promise.resolve(
          jsonResponse(200, pricing(pricingCalls === 1 ? '2026-06-01T00:00:00Z' : '2026-06-19T10:00:00Z')),
        )
      }
      throw new Error(`unexpected fetch ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<ModelsPage />)
    expect(await screen.findByText('gpt-test')).toBeInTheDocument()

    fireEvent.click(refreshButton())

    expect(await screen.findByText(/12 updated/i)).toBeInTheDocument()
    expect(screen.getByText(/3 skipped/i)).toBeInTheDocument()
    // pricing was reloaded after the refresh (mount + post-refresh)
    expect(pricingCalls).toBe(2)
    expect(refreshButton()).not.toBeDisabled()
  })

  it('shows an error and keeps the table when the refresh fails', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/api/admin/pricing/refresh')) {
        return Promise.resolve(emptyResponse(503))
      }
      if (url.includes('/api/pricing')) {
        return Promise.resolve(jsonResponse(200, pricing('2026-06-01T00:00:00Z')))
      }
      throw new Error(`unexpected fetch ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<ModelsPage />)
    expect(await screen.findByText('gpt-test')).toBeInTheDocument()

    fireEvent.click(refreshButton())

    expect(await screen.findByText(/could not refresh prices/i)).toBeInTheDocument()
    // existing table survives the failure
    expect(screen.getByText('gpt-test')).toBeInTheDocument()
    expect(refreshButton()).not.toBeDisabled()
  })

  it('disables the button while a refresh is in flight (no concurrent refreshes)', async () => {
    let refreshCalls = 0
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/api/admin/pricing/refresh')) {
        refreshCalls += 1
        return new Promise<Response>(() => {}) // never resolves
      }
      if (url.includes('/api/pricing')) {
        return Promise.resolve(jsonResponse(200, pricing('2026-06-01T00:00:00Z')))
      }
      throw new Error(`unexpected fetch ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<ModelsPage />)
    expect(await screen.findByText('gpt-test')).toBeInTheDocument()

    fireEvent.click(refreshButton())
    await waitFor(() => expect(refreshButton()).toBeDisabled())

    fireEvent.click(refreshButton())
    expect(refreshCalls).toBe(1)
  })
})

describe('ModelsPage — sortable price columns (TKM-67)', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  function pricingMulti(): PricingResponse {
    return {
      lastRefreshedAt: '2026-06-01T00:00:00Z',
      primarySource: 'litellm',
      models: [
        row('zeta', 3, 1),
        row('alpha', 1, 3),
        row('mid', 2, 2),
      ],
    }
  }

  function row(name: string, input: number, output: number) {
    return {
      provider: 'openai',
      model: name,
      inputTokenPricePerMillion: input,
      outputTokenPricePerMillion: output,
      source: 'REMOTE' as const,
      fetchedAt: '2026-06-01T00:00:00Z',
    }
  }

  function renderedOrder(): string[] {
    return screen.getAllByText(/^(zeta|alpha|mid)$/).map((el) => el.textContent ?? '')
  }

  function stubPricing() {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(jsonResponse(200, pricingMulti()))),
    )
  }

  it('sorts by input price ascending then descending on successive header clicks', async () => {
    stubPricing()
    render(<ModelsPage />)
    expect(await screen.findByText('zeta')).toBeInTheDocument()
    // Unsorted: original payload order.
    expect(renderedOrder()).toEqual(['zeta', 'alpha', 'mid'])

    const inputHeader = screen.getByRole('button', { name: /sort by input/i })
    fireEvent.click(inputHeader)
    expect(renderedOrder()).toEqual(['alpha', 'mid', 'zeta'])
    expect(inputHeader).toHaveAttribute('aria-sort', 'ascending')

    fireEvent.click(inputHeader)
    expect(renderedOrder()).toEqual(['zeta', 'mid', 'alpha'])
    expect(inputHeader).toHaveAttribute('aria-sort', 'descending')
  })

  it('sorts by output price independently of the input column', async () => {
    stubPricing()
    render(<ModelsPage />)
    expect(await screen.findByText('zeta')).toBeInTheDocument()

    const outputHeader = screen.getByRole('button', { name: /sort by output/i })
    fireEvent.click(outputHeader)
    // output asc: zeta(1), mid(2), alpha(3)
    expect(renderedOrder()).toEqual(['zeta', 'mid', 'alpha'])
    expect(outputHeader).toHaveAttribute('aria-sort', 'ascending')
    expect(screen.getByRole('button', { name: /sort by input/i })).toHaveAttribute(
      'aria-sort',
      'none',
    )
  })
})
