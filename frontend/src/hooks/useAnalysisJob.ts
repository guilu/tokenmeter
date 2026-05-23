import { useEffect, useRef, useState } from 'react'

import { ApiError, fetchAnalysisJobStatus } from '../services/api'
import type { AnalysisJobStatusResponse } from '../types/api'

export interface UseAnalysisJobResult {
  job: AnalysisJobStatusResponse | null
  isPolling: boolean
  error: ApiError | null
}

const MAX_RETRIES = 3

function isTerminal(status: AnalysisJobStatusResponse['status']): boolean {
  return status === 'SUCCESS' || status === 'FAILED'
}

/**
 * Polls `GET /api/analyze/jobs/{jobId}` every `intervalMs` ms (default 1500) using a chained
 * `setTimeout` (never `setInterval`) so requests cannot overlap. An `AbortController` cancels any
 * in-flight request on unmount or when `jobId` changes. Polling stops when the snapshot reaches a
 * terminal status (`SUCCESS` / `FAILED`) or when the server returns 404 (unknown job).
 *
 * Transient 5xx errors are retried up to {@link MAX_RETRIES} times with a linear backoff of
 * `intervalMs * (n + 1)` ms. `error` reports the most recent polling failure (network/HTTP), not
 * the job's own `error` payload — that one lives inside `job.error`.
 */
export function useAnalysisJob(
  jobId: string | null,
  intervalMs = 1500,
): UseAnalysisJobResult {
  const [job, setJob] = useState<AnalysisJobStatusResponse | null>(null)
  const [isPolling, setIsPolling] = useState<boolean>(Boolean(jobId))
  const [error, setError] = useState<ApiError | null>(null)
  const [previousJobId, setPreviousJobId] = useState<string | null>(jobId)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  // Reset state synchronously during render when jobId changes (React 19-recommended pattern).
  if (jobId !== previousJobId) {
    setPreviousJobId(jobId)
    setJob(null)
    setError(null)
    setIsPolling(Boolean(jobId))
  }

  useEffect(() => {
    if (!jobId) {
      return
    }

    let cancelled = false
    let retries = 0

    function clearScheduled(): void {
      if (timeoutRef.current !== null) {
        clearTimeout(timeoutRef.current)
        timeoutRef.current = null
      }
    }

    function scheduleNext(delay: number): void {
      clearScheduled()
      timeoutRef.current = setTimeout(() => {
        void runPoll()
      }, delay)
    }

    async function runPoll(): Promise<void> {
      if (cancelled || !jobId) return

      abortRef.current?.abort()
      const controller = new AbortController()
      abortRef.current = controller

      try {
        const snapshot = await fetchAnalysisJobStatus(jobId, controller.signal)
        if (cancelled) return
        retries = 0
        setError(null)
        setJob(snapshot)

        if (isTerminal(snapshot.status)) {
          setIsPolling(false)
          return
        }

        scheduleNext(intervalMs)
      } catch (reason: unknown) {
        if (cancelled) return
        if (reason instanceof DOMException && reason.name === 'AbortError') return

        if (reason instanceof ApiError) {
          if (reason.status === 404) {
            setError(reason)
            setIsPolling(false)
            return
          }
          if (reason.status >= 500 && retries < MAX_RETRIES) {
            retries += 1
            setError(reason)
            scheduleNext(intervalMs * (retries + 1))
            return
          }
          setError(reason)
          setIsPolling(false)
          return
        }

        setError(new ApiError('Unexpected polling error', 0))
        setIsPolling(false)
      }
    }

    void runPoll()

    return () => {
      cancelled = true
      clearScheduled()
      abortRef.current?.abort()
      abortRef.current = null
    }
  }, [jobId, intervalMs])

  return { job, isPolling, error }
}
