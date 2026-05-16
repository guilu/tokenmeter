/**
 * Formats an ISO-8601 timestamp as a localized relative time string,
 * picking the largest sensible unit (minute / hour / day / week).
 *
 * Returns an empty string when the input is null so callers can decide
 * what to render. Uses the platform's `Intl.RelativeTimeFormat` (no deps).
 */
export function formatRelativeTime(iso: string | null, locale?: string): string {
  if (!iso) {
    return ''
  }

  const parsed = Date.parse(iso)
  if (Number.isNaN(parsed)) {
    return ''
  }

  const resolvedLocale =
    locale ?? (typeof navigator !== 'undefined' ? navigator.language : undefined) ?? 'en'

  const diffSeconds = Math.round((parsed - Date.now()) / 1000)
  const absSeconds = Math.abs(diffSeconds)

  const units: Array<{ unit: Intl.RelativeTimeFormatUnit; seconds: number }> = [
    { unit: 'week', seconds: 60 * 60 * 24 * 7 },
    { unit: 'day', seconds: 60 * 60 * 24 },
    { unit: 'hour', seconds: 60 * 60 },
    { unit: 'minute', seconds: 60 },
  ]

  const formatter = new Intl.RelativeTimeFormat(resolvedLocale, { numeric: 'auto' })

  for (const { unit, seconds } of units) {
    if (absSeconds >= seconds) {
      const value = Math.round(diffSeconds / seconds)
      return formatter.format(value, unit)
    }
  }

  // Less than a minute — surface as 0 minutes ago for a stable label.
  return formatter.format(0, 'minute')
}
