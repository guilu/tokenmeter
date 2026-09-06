import { describe, expect, it, vi } from 'vitest'

import {
  trackAnalysisComplete,
  trackAnalysisFailed,
  trackAnalysisStart,
} from './analysisLifecycle'

const trackEvent = vi.fn()

describe('analysis lifecycle analytics', () => {
  it('tracks an accepted analysis without repository-identifying data', () => {
    trackAnalysisStart('https://github.com/acme/private-widget', trackEvent)

    expect(trackEvent).toHaveBeenCalledWith('analysis_start', {
      analysis_mode: 'new',
      repository_host: 'github.com',
      repository_visibility: 'public',
    })
    expect(JSON.stringify(trackEvent.mock.calls)).not.toContain('acme')
    expect(JSON.stringify(trackEvent.mock.calls)).not.toContain('private-widget')
  })

  it.each([
    [4_999, '<5s'],
    [5_000, '5-29s'],
    [30_000, '30-59s'],
    [60_000, '1-2m'],
    [180_000, '3-9m'],
    [600_000, '10m+'],
  ])('tracks completion duration %i ms as %s', (durationMs, durationBucket) => {
    trackAnalysisComplete(durationMs, trackEvent)

    expect(trackEvent).toHaveBeenCalledWith('analysis_complete', {
      analysis_mode: 'new',
      duration_bucket: durationBucket,
      result: 'success',
    })
  })

  it('tracks backend failure codes without error messages', () => {
    trackAnalysisFailed(42_000, 'REPOSITORY_TOO_LARGE', trackEvent)

    expect(trackEvent).toHaveBeenCalledWith('analysis_failed', {
      analysis_mode: 'new',
      duration_bucket: '30-59s',
      result: 'failure',
      failure_type: 'repository_too_large',
    })
  })

  it('normalizes unknown failures to a bounded value', () => {
    trackAnalysisFailed(1_000, undefined, trackEvent)

    expect(trackEvent).toHaveBeenCalledWith('analysis_failed', {
      analysis_mode: 'new',
      duration_bucket: '<5s',
      result: 'failure',
      failure_type: 'unknown',
    })
  })
})
