/** @vitest-environment jsdom */

import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useStalledProgress } from './useStalledProgress'

describe('useStalledProgress', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns false before threshold expires', () => {
    const { result } = renderHook(() => useStalledProgress(45))

    act(() => {
      vi.advanceTimersByTime(14999)
    })

    expect(result.current).toBe(false)
  })

  it('returns true after thresholdMs without value change', () => {
    const { result } = renderHook(() => useStalledProgress(45))

    act(() => {
      vi.advanceTimersByTime(15000)
    })

    expect(result.current).toBe(true)
  })

  it('resets to false when value changes before new threshold', () => {
    const { result, rerender } = renderHook(({ value }: { value: number }) => useStalledProgress(value), {
      initialProps: { value: 45 },
    })

    // Let it stall
    act(() => {
      vi.advanceTimersByTime(15000)
    })
    expect(result.current).toBe(true)

    // Change value — should reset
    rerender({ value: 60 })

    act(() => {
      vi.advanceTimersByTime(14999)
    })

    expect(result.current).toBe(false)
  })
})
