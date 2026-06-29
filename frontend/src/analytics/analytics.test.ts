/** @vitest-environment jsdom */

import { afterEach, describe, expect, it, vi } from 'vitest'

import { initAnalytics, isAnalyticsEnabled, trackEvent, trackPageView } from './analytics'

function gtagScripts(): NodeListOf<HTMLScriptElement> {
  return document.querySelectorAll<HTMLScriptElement>('script[src*="googletagmanager.com/gtag"]')
}

// gtag.js only processes the native `arguments` object pushed onto the dataLayer; a plain array is
// silently dropped (no `/collect` hit). Normalise the array-like entries before asserting.
function dataLayerCalls(): unknown[][] {
  return (window.dataLayer ?? []).map((entry) => Array.from(entry as ArrayLike<unknown>))
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

    // Entries must be `arguments` objects, never plain arrays (the bug that silenced GA).
    expect(window.dataLayer?.every((entry) => !Array.isArray(entry))).toBe(true)

    const calls = dataLayerCalls()
    expect(calls).toContainEqual(['js', expect.any(Date)])
    expect(calls).toContainEqual(['config', 'G-TEST123', { send_page_view: false }])
  })

  it('sends a page_view event with the path', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123')
    initAnalytics()

    trackPageView('/analysis/abc')

    expect(dataLayerCalls()).toContainEqual([
      'event',
      'page_view',
      expect.objectContaining({ page_path: '/analysis/abc' }),
    ])
  })

  it('sends named product events', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123')
    initAnalytics()

    trackEvent('trending_repo_clicked', { repository: 'acme/widget' })

    expect(dataLayerCalls()).toContainEqual([
      'event',
      'trending_repo_clicked',
      { repository: 'acme/widget' },
    ])
  })
})
