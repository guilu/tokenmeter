import type { RepositoryAnalysisCostEstimateResponse } from '../types/api'

interface HeuristicDisclaimerProps {
  estimates: RepositoryAnalysisCostEstimateResponse[]
}

export function HeuristicDisclaimer({ estimates }: HeuristicDisclaimerProps) {
  const hasHeuristic = estimates.some((e) => e.precision === 'HEURISTIC')

  if (!hasHeuristic) return null

  return (
    <aside
      className="flex items-start gap-3 rounded-2xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-800 dark:text-amber-200"
      role="note"
    >
      <svg
        aria-hidden="true"
        className="mt-0.5 h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        viewBox="0 0 24 24"
      >
        <path
          d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <span>
        Token counts for one or more non-OpenAI models are{' '}
        <strong>heuristic estimates</strong> — no official local tokenizer is
        available yet. Treat these figures as a floor, not a precise count.
      </span>
    </aside>
  )
}
