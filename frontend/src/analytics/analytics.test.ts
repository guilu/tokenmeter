/** @vitest-environment jsdom */

import { afterEach, describe, expect, it, vi } from 'vitest'

import { initAnalytics, isAnalyticsEnabled, trackEvent, trackPageView } from './analytics'

function gtagScripts(): NodeListOf<HTMLScriptElement> {
  return document.querySelectorAll<HTMLScriptElement>('script[src*="googletagmanager.com/gtag"]')
}

afterEach(() => {
  vi.unstubAllEnvs()
  delete window.gtag
  window.dataLayer = undefined
  gtagScripts().forEach((s) => s.remove())
})

describe('analytics — disabled without a Measurement ID', () => {
  it('reports disabled and never loads GA or throws', () => {
    expect(isAnalyticsEnabled()).toBe(false)

    initAnalytics()
    trackPageView('/models')
    trackEvent('analysis_submitted')

    expect(window.gtag).toBeUndefined()
    expect(gtagScripts()).toHaveLength(0)
  })
})

describe('analytics — enabled with a Measurement ID', () => {
  it('injects gtag.js once and configures GA with manual page views', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123')

    initAnalytics()
    initAnalytics() // idempotent

    expect(isAnalyticsEnabled()).toBe(true)
    expect(gtagScripts()).toHaveLength(1)
    expect(gtagScripts()[0].src).toContain('id=G-TEST123')
    expect(typeof window.gtag).toBe('function')

    const calls = window.dataLayer ?? []
    expect(calls).toContainEqual(['js', expect.any(Date)])
    expect(calls).toContainEqual(['config', 'G-TEST123', { send_page_view: false }])
  })

  it('sends a page_view event with the path', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123')
    initAnalytics()

    trackPageView('/analysis/abc')

    expect(window.dataLayer).toContainEqual([
      'event',
      'page_view',
      expect.objectContaining({ page_path: '/analysis/abc' }),
    ])
  })

  it('sends named product events', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123')
    initAnalytics()

    trackEvent('trending_repo_clicked', { repository: 'acme/widget' })

    expect(window.dataLayer).toContainEqual([
      'event',
      'trending_repo_clicked',
      { repository: 'acme/widget' },
    ])
  })
})
