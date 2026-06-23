/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import type { TokenizationPrecision } from '../../types/api'
import { PrecisionBadge } from '../PrecisionBadge'

describe('PrecisionBadge', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders "exact" label with green styling for EXACT_LOCAL', () => {
    render(<PrecisionBadge precision="EXACT_LOCAL" />)

    const badge = screen.getByText('exact')
    expect(badge).toBeInTheDocument()
    // Green variant — must contain a green class
    expect(badge.className).toMatch(/green/)
  })

  it('renders "estimate" label with amber styling for HEURISTIC', () => {
    render(<PrecisionBadge precision="HEURISTIC" />)

    const badge = screen.getByText('estimate')
    expect(badge).toBeInTheDocument()
    // Amber variant — must contain amber class
    expect(badge.className).toMatch(/amber/)
  })

  it('renders "approx" label with yellow styling for LOCAL_ESTIMATED', () => {
    render(<PrecisionBadge precision="LOCAL_ESTIMATED" />)

    const badge = screen.getByText('approx')
    expect(badge).toBeInTheDocument()
    // Yellow variant — must contain yellow class
    expect(badge.className).toMatch(/yellow/)
  })

  it('renders nothing when precision is undefined', () => {
    const { container } = render(<PrecisionBadge precision={undefined} />)

    expect(container.firstChild).toBeNull()
  })

  it('renders nothing when precision is null (legacy data)', () => {
    const precision = null as unknown as TokenizationPrecision
    const { container } = render(<PrecisionBadge precision={precision} />)

    expect(container.firstChild).toBeNull()
  })
})
