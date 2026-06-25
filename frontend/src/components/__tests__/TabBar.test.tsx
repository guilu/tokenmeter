/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { TabBar } from '../TabBar'

const TABS = [
  { key: 'models', label: 'Models' },
  { key: 'languages', label: 'Languages' },
  { key: 'workflow', label: 'Workflow & Effort' },
  { key: 'whatif', label: 'What-if' },
]

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('TabBar', () => {
  it('renders a tablist with the provided aria-label', () => {
    render(
      <TabBar
        tabs={TABS}
        activeTab="models"
        onSelect={vi.fn()}
        ariaLabel="Analysis sections"
      />,
    )
    expect(screen.getByRole('tablist', { name: /Analysis sections/i })).toBeInTheDocument()
  })

  it('renders one role=tab button per tab item', () => {
    render(
      <TabBar
        tabs={TABS}
        activeTab="models"
        onSelect={vi.fn()}
        ariaLabel="Analysis sections"
      />,
    )
    const tabs = screen.getAllByRole('tab')
    expect(tabs).toHaveLength(4)
  })

  it('active tab has aria-selected=true, tabIndex=0, id and aria-controls', () => {
    render(
      <TabBar
        tabs={TABS}
        activeTab="models"
        onSelect={vi.fn()}
        ariaLabel="Analysis sections"
      />,
    )
    const activeTab = screen.getByRole('tab', { name: /Models/i })
    expect(activeTab).toHaveAttribute('aria-selected', 'true')
    expect(activeTab).toHaveAttribute('tabindex', '0')
    expect(activeTab).toHaveAttribute('id', 'tab-models')
    expect(activeTab).toHaveAttribute('aria-controls', 'tab-panel-models')
  })

  it('inactive tabs have aria-selected=false and tabIndex=-1', () => {
    render(
      <TabBar
        tabs={TABS}
        activeTab="models"
        onSelect={vi.fn()}
        ariaLabel="Analysis sections"
      />,
    )
    const inactiveTab = screen.getByRole('tab', { name: /Languages/i })
    expect(inactiveTab).toHaveAttribute('aria-selected', 'false')
    expect(inactiveTab).toHaveAttribute('tabindex', '-1')
  })

  it('clicking an inactive tab calls onSelect with that tab key', () => {
    const onSelect = vi.fn()
    render(
      <TabBar
        tabs={TABS}
        activeTab="models"
        onSelect={onSelect}
        ariaLabel="Analysis sections"
      />,
    )
    fireEvent.click(screen.getByRole('tab', { name: /Languages/i }))
    expect(onSelect).toHaveBeenCalledWith('languages')
  })

  it('active tab has className containing bg-primary', () => {
    render(
      <TabBar
        tabs={TABS}
        activeTab="models"
        onSelect={vi.fn()}
        ariaLabel="Analysis sections"
      />,
    )
    const activeTab = screen.getByRole('tab', { name: /Models/i })
    expect(activeTab.className).toContain('bg-primary')
  })

  it('supports custom idBase prefix', () => {
    render(
      <TabBar
        tabs={TABS}
        activeTab="models"
        onSelect={vi.fn()}
        ariaLabel="Custom sections"
        idBase="my-tab"
      />,
    )
    const activeTab = screen.getByRole('tab', { name: /Models/i })
    expect(activeTab).toHaveAttribute('id', 'my-tab-models')
    expect(activeTab).toHaveAttribute('aria-controls', 'my-tab-panel-models')
  })
})
