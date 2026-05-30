/** @vitest-environment jsdom */

import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useElapsedSeconds } from './useElapsedSeconds'

describe('useElapsedSeconds', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns null when startedAt is null', () => {
    const { result } = renderHook(() => useElapsedSeconds(null))

    expect(result.current).toBeNull()
  })

  it('returns elapsed seconds and increments every 1s', () => {
    const startTime = new Date('2025-01-01T00:00:00.000Z')
    vi.setSystemTime(startTime)

    const { result } = renderHook(() => useElapsedSeconds('2025-01-01T00:00:00.000Z'))

    // Initial render
    expect(result.current).toBe(0)

    // Advance 7 seconds
    act(() => {
      vi.advanceTimersByTime(7000)
    })

    expect(result.current).toBeGreaterThanOrEqual(7)
  })
})
