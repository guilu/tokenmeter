/** @vitest-environment jsdom */

import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { Footer } from './Footer'

describe('Footer analytics settings', () => {
  it('renders Privacy as a normal footer control and opens analytics settings', () => {
    const onAnalyticsSettings = vi.fn()

    render(
      <Footer analyticsConfigured onAnalyticsSettings={onAnalyticsSettings} />,
    )

    const privacy = screen.getByRole('button', { name: 'Privacy' })
    expect(privacy.className).not.toContain('fixed')
    fireEvent.click(privacy)
    expect(onAnalyticsSettings).toHaveBeenCalledOnce()
  })

  it('omits Privacy when analytics is not configured', () => {
    render(<Footer analyticsConfigured={false} onAnalyticsSettings={vi.fn()} />)

    expect(
      screen.queryByRole('button', { name: 'Privacy' }),
    ).not.toBeInTheDocument()
  })
})
