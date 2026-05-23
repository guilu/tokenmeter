import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AnalysisJobStatusResponse } from '../types/api'
import { useAnalysisJob } from './useAnalysisJob'

async function flushPending(): Promise<void> {
  // Let pending microtasks (fetch resolution) and any zero-delay timers settle.
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
    await vi.advanceTimersByTimeAsync(0)
  })
}

function snapshot(overrides: Partial<AnalysisJobStatusResponse> = {}): AnalysisJobStatusResponse {
  return {
    jobId: 'job-123',
    status: 'QUEUED',
    phase: 'QUEUED',
    phaseLabel: 'Queued',
    progressPercent: 0,
    message: null,
    analysisId: null,
    error: null,
    metrics: null,
    timestamps: {
      createdAt: '2025-01-01T00:00:00Z',
      startedAt: null,
      updatedAt: '2025-01-01T00:00:00Z',
      completedAt: null,
    },
    ...overrides,
  }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('useAnalysisJob', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('polls QUEUED → RUNNING → SUCCESS and stops on terminal status', async () => {
    const responses = [
      snapshot({ status: 'QUEUED', phase: 'QUEUED', progressPercent: 0 }),
      snapshot({
        status: 'RUNNING',
        phase: 'COUNTING_TOKENS',
        phaseLabel: 'Counting tokens',
        progressPercent: 60,
      }),
      snapshot({
        status: 'SUCCESS',
        phase: 'COMPLETED',
        phaseLabel: 'Completed',
        progressPercent: 100,
        analysisId: 'analysis-xyz',
      }),
    ]

    const fetchMock = vi.fn(async () => jsonResponse(responses.shift() ?? snapshot()))
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => useAnalysisJob('job-123', 1500))

    await flushPending()
    expect(result.current.job?.status).toBe('QUEUED')
    expect(result.current.isPolling).toBe(true)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1500)
    })
    await flushPending()
    expect(result.current.job?.status).toBe('RUNNING')

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1500)
    })
    await flushPending()
    expect(result.current.job?.status).toBe('SUCCESS')
    expect(result.current.isPolling).toBe(false)
    expect(result.current.job?.analysisId).toBe('analysis-xyz')

    const callsBefore = fetchMock.mock.calls.length
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    expect(fetchMock.mock.calls.length).toBe(callsBefore)
  })

  it('exposes the job.error payload and stops polling on FAILED', async () => {
    const responses = [
      snapshot({ status: 'RUNNING', phase: 'CLONING_REPOSITORY', progressPercent: 10 }),
      snapshot({
        status: 'FAILED',
        phase: 'FAILED',
        phaseLabel: 'Failed',
        progressPercent: 10,
        error: { code: 'CLONE_TIMEOUT', message: 'Clone exceeded timeout' },
      }),
    ]

    const fetchMock = vi.fn(async () => jsonResponse(responses.shift() ?? snapshot()))
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => useAnalysisJob('job-123', 1500))

    await flushPending()
    expect(result.current.job?.status).toBe('RUNNING')

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1500)
    })
    await flushPending()
    expect(result.current.job?.status).toBe('FAILED')
    expect(result.current.isPolling).toBe(false)
    expect(result.current.job?.error).toEqual({
      code: 'CLONE_TIMEOUT',
      message: 'Clone exceeded timeout',
    })

    // The hook's own `error` reports polling-level errors only, not the job's error payload.
    expect(result.current.error).toBeNull()

    const callsBefore = fetchMock.mock.calls.length
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    expect(fetchMock.mock.calls.length).toBe(callsBefore)
  })

  it('stops polling and exposes a 404 ApiError when the job is unknown', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse({ code: 'JOB_NOT_FOUND', message: 'unknown' }, 404),
    )
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => useAnalysisJob('job-missing', 1500))

    await flushPending()
    expect(result.current.error?.status).toBe(404)
    expect(result.current.isPolling).toBe(false)
    expect(result.current.job).toBeNull()
  })
})
