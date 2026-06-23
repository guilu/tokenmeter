import type { TokenizationPrecision } from '../types/api'

interface PrecisionBadgeProps {
  precision: TokenizationPrecision | null | undefined
}

const variantMap: Record<
  TokenizationPrecision,
  { label: string; className: string }
> = {
  EXACT_LOCAL: {
    label: 'exact',
    className:
      'border-green-500/30 bg-green-500/10 text-green-700 dark:text-green-400',
  },
  LOCAL_ESTIMATED: {
    label: 'approx',
    className:
      'border-yellow-500/30 bg-yellow-500/10 text-yellow-700 dark:text-yellow-400',
  },
  HEURISTIC: {
    label: 'estimate',
    className:
      'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400',
  },
}

export function PrecisionBadge({ precision }: PrecisionBadgeProps) {
  if (!precision) return null

  const variant = variantMap[precision]
  if (!variant) return null

  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${variant.className}`}
    >
      {variant.label}
    </span>
  )
}
