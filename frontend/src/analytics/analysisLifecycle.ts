import { trackEvent } from './analytics'

type EventTracker = typeof trackEvent

const FAILURE_TYPES: Record<string, string> = {
  ANALYSIS_FAILED: 'analysis_failed',
  CLONE_TIMEOUT: 'clone_timeout',
  INVALID_URL: 'invalid_url',
  JOB_INTERRUPTED: 'job_interrupted',
  RATE_LIMITED: 'rate_limited',
  REPOSITORY_TOO_LARGE: 'repository_too_large',
}

/**
 * The current submission contract always creates a fresh job. `analysis_mode` therefore remains
 * `new` until the API exposes whether an accepted request was served from cache or joined to an
 * existing job. No job/analysis UUID or repository path is exported.
 */
export function trackAnalysisStart(repositoryUrl: string, send: EventTracker = trackEvent): void {
  send('analysis_start', {
    analysis_mode: 'new',
    repository_host: safeRepositoryHost(repositoryUrl),
    repository_visibility: 'public',
  })
}

export function trackAnalysisComplete(durationMs: number, send: EventTracker = trackEvent): void {
  send('analysis_complete', {
    analysis_mode: 'new',
    duration_bucket: durationBucket(durationMs),
    result: 'success',
  })
}

export function trackAnalysisFailed(
  durationMs: number,
  failureCode?: string,
  send: EventTracker = trackEvent,
): void {
  send('analysis_failed', {
    analysis_mode: 'new',
    duration_bucket: durationBucket(durationMs),
    result: 'failure',
    failure_type: failureCode ? (FAILURE_TYPES[failureCode] ?? 'unknown') : 'unknown',
  })
}

function safeRepositoryHost(repositoryUrl: string): string {
  try {
    return new URL(repositoryUrl).hostname.toLowerCase()
  } catch {
    return 'unknown'
  }
}

function durationBucket(durationMs: number): string {
  if (durationMs < 5_000) return '<5s'
  if (durationMs < 30_000) return '5-29s'
  if (durationMs < 60_000) return '30-59s'
  if (durationMs < 180_000) return '1-2m'
  if (durationMs < 600_000) return '3-9m'
  return '10m+'
}
