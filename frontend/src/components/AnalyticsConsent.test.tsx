/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { ANALYTICS_CONSENT_KEY, AnalyticsConsent } from './AnalyticsConsent'

function gtagScripts(): NodeListOf<HTMLScriptElement> {
  return document.querySelectorAll<HTMLScriptElement>(
    'script[src*="googletagmanager.com/gtag"]',
  )
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllEnvs()
  delete window.gtag
  window.dataLayer = undefined
  gtagScripts().forEach((script) => script.remove())
})

describe('AnalyticsConsent', () => {
  it('asks before loading Google Analytics and accepts explicitly', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123')

    render(<AnalyticsConsent />)

    expect(
      screen.getByRole('dialog', { name: 'Analytics consent' }),
    ).toBeInTheDocument()
    expect(gtagScripts()).toHaveLength(0)

    fireEvent.click(screen.getByRole('button', { name: 'Accept analytics' }))

    expect(localStorage.getItem(ANALYTICS_CONSENT_KEY)).toBe('granted')
    expect(
      screen.queryByRole('dialog', { name: 'Analytics consent' }),
    ).not.toBeInTheDocument()
    expect(gtagScripts()).toHaveLength(1)
  })

  it('remembers rejection without loading Google Analytics', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123')

    render(<AnalyticsConsent />)
    fireEvent.click(screen.getByRole('button', { name: 'Reject analytics' }))

    expect(localStorage.getItem(ANALYTICS_CONSENT_KEY)).toBe('denied')
    expect(gtagScripts()).toHaveLength(0)
  })

  it('lets a user reopen settings and withdraw consent', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123')
    localStorage.setItem(ANALYTICS_CONSENT_KEY, 'granted')
    document.cookie = '_ga=test; path=/'
    document.cookie = '_ga_TEST123=session; path=/'

    render(<AnalyticsConsent />)
    fireEvent.click(screen.getByRole('button', { name: 'Analytics settings' }))
    fireEvent.click(
      screen.getByRole('button', { name: 'Withdraw analytics consent' }),
    )

    expect(localStorage.getItem(ANALYTICS_CONSENT_KEY)).toBe('denied')
    expect(document.cookie).not.toContain('_ga=')
    expect(document.cookie).not.toContain('_ga_TEST123=')
    expect(window.gtag).toBeUndefined()
    expect(gtagScripts()).toHaveLength(0)
  })

  it('stays hidden when analytics is not configured', () => {
    render(<AnalyticsConsent />)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Analytics settings' }),
    ).not.toBeInTheDocument()
  })
})
