/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ModelsPage } from './ModelsPage'

// ProviderIcon pulls @lobehub/icons → @lobehub/ui, whose ESM resolution breaks
// under vitest. The collapse behaviour under test does not need real icons.
vi.mock('../components/ProviderIcon', () => ({ ProviderIcon: () => null }))

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
