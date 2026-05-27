/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { LeaderboardFilterBar } from '../LeaderboardFilterBar'

describe('LeaderboardFilterBar', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('renders mode, provider and model controls', () => {
    render(
      <LeaderboardFilterBar
        mode="raw"
        provider=""
        model=""
        onModeChange={vi.fn()}
        onProviderChange={vi.fn()}
        onModelChange={vi.fn()}
      />,
    )

    expect(screen.getByText('Mode')).toBeInTheDocument()
    expect(screen.getByText('Provider')).toBeInTheDocument()
    expect(screen.getByText('Model')).toBeInTheDocument()
  })

  it('applies sticky positioning classes on any viewport', () => {
    const { container } = render(
      <LeaderboardFilterBar
        mode="raw"
        provider=""
        model=""
        onModeChange={vi.fn()}
        onProviderChange={vi.fn()}
        onModelChange={vi.fn()}
      />,
    )

    const root = container.firstElementChild as HTMLElement
    expect(root.className).toContain('sticky')
    expect(root.className).toContain('top-0')
    expect(root.className).toContain('z-10')
  })

  it('calls onModeChange when mode select changes', () => {
    const onModeChange = vi.fn()
    render(
      <LeaderboardFilterBar
        mode="raw"
        provider=""
        model=""
        onModeChange={onModeChange}
        onProviderChange={vi.fn()}
        onModelChange={vi.fn()}
      />,
    )

    // First select is the mode select (raw/assisted/agentic options)
    const selects = screen.getAllByRole('combobox')
    fireEvent.change(selects[0], { target: { value: 'assisted' } })
    expect(onModeChange).toHaveBeenCalledWith('assisted')
  })

  it('calls onProviderChange when provider select changes', () => {
    const onProviderChange = vi.fn()
    render(
      <LeaderboardFilterBar
        mode="raw"
        provider=""
        model=""
        onModeChange={vi.fn()}
        onProviderChange={onProviderChange}
        onModelChange={vi.fn()}
      />,
    )

    // Second select is the provider select
    const selects = screen.getAllByRole('combobox')
    fireEvent.change(selects[1], { target: { value: 'openai' } })
    expect(onProviderChange).toHaveBeenCalledWith('openai')
  })

  it('calls onModelChange when model input changes', () => {
    const onModelChange = vi.fn()
    render(
      <LeaderboardFilterBar
        mode="raw"
        provider=""
        model=""
        onModeChange={vi.fn()}
        onProviderChange={vi.fn()}
        onModelChange={onModelChange}
      />,
    )

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'gpt-4o' } })
    expect(onModelChange).toHaveBeenCalledWith('gpt-4o')
  })
})
