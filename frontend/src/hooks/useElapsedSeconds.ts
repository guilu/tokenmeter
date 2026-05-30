import { useEffect, useState } from 'react'

export function useElapsedSeconds(startedAt: string | null): number | null {
  const [elapsed, setElapsed] = useState<number | null>(null)

  useEffect(() => {
    if (startedAt === null) {
      setElapsed(null)
      return
    }

    const compute = () => {
      return Math.max(0, Math.floor((Date.now() - Date.parse(startedAt)) / 1000))
    }

    setElapsed(compute())

    const interval = setInterval(() => {
      setElapsed(compute())
    }, 1000)

    return () => {
      clearInterval(interval)
    }
  }, [startedAt])

  return elapsed
}
