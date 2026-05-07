import { useEffect, useState } from 'react'

export function useAsync<T>(factory: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true

    factory()
      .then((value) => {
        if (active) setData(value)
      })
      .catch((reason: unknown) => {
        if (active) setError(reason)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [factory])

  return { data, error, loading }
}
