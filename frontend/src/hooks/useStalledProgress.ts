import { useEffect, useRef, useState } from 'react'

export function useStalledProgress(progressPercent: number, thresholdMs = 15000): boolean {
  const lastValueRef = useRef<number>(progressPercent)
  const lastChangedAtRef = useRef<number>(Date.now())
  const [stalled, setStalled] = useState<boolean>(false)

  useEffect(() => {
    if (progressPercent !== lastValueRef.current) {
      lastValueRef.current = progressPercent
      lastChangedAtRef.current = Date.now()
      setStalled(false)
    }

    const timer = setTimeout(() => {
      setStalled(true)
    }, thresholdMs)

    return () => {
      clearTimeout(timer)
    }
  }, [progressPercent, thresholdMs])

  return stalled
}
