import { useEffect, useRef, useState } from 'react'

export function useElapsedSeconds(startedAt: string | null): number | null {
  const [elapsed, setElapsed] = useState<number | null>(() => {
    if (startedAt === null) return null
    return Math.max(0, Math.floor((Date.now() - Date.parse(startedAt)) / 1000))
  })

  const prevStartedAt = useRef<string | null>(startedAt)

  useEffect(() => {
    if (prevStartedAt.current !== startedAt) {
      prevStartedAt.current = startedAt
    }

    if (startedAt === null) {
      return
    }

    const compute = () => Math.max(0, Math.floor((Date.now() - Date.parse(startedAt)) / 1000))

    const interval = setInterval(() => {
      setElapsed(compute())
    }, 1000)

    return () => {
      clearInterval(interval)
    }
  }, [startedAt])

  // Sync with null when startedAt becomes null without causing setState in effect body
  if (startedAt === null && elapsed !== null) {
    return null
  }

  return elapsed
}
