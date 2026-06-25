/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import { FloorDisclaimer } from '../FloorDisclaimer'

describe('FloorDisclaimer', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders with role="note"', () => {
    render(<FloorDisclaimer />)

    expect(screen.getByRole('note')).toBeInTheDocument()
  })

  it('contains text indicating cost excludes input prompts', () => {
    render(<FloorDisclaimer />)

    const note = screen.getByRole('note')
    expect(note.textContent).toMatch(/input prompt/i)
  })

  it('contains text indicating cost excludes failed attempts', () => {
    render(<FloorDisclaimer />)

    const note = screen.getByRole('note')
    expect(note.textContent).toMatch(/failed attempt/i)
  })

  it('contains text indicating cost excludes negotiated rates', () => {
    render(<FloorDisclaimer />)

    const note = screen.getByRole('note')
    expect(note.textContent).toMatch(/negotiated.*rate/i)
  })

  it('is NOT marked as print:hidden — it must appear in PDF export', () => {
    const { container } = render(<FloorDisclaimer />)

    // The root element (aside) must NOT have print:hidden anywhere in its class
    const root = container.firstElementChild
    expect(root).not.toBeNull()
    expect(root?.className).not.toContain('print:hidden')
  })

  it('uses amber styling matching the HeuristicDisclaimer pattern', () => {
    render(<FloorDisclaimer />)

    const note = screen.getByRole('note')
    expect(note.className).toMatch(/amber/)
  })
})
