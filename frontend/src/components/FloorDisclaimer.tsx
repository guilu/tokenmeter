export function FloorDisclaimer() {
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
        <strong>This cost is a floor, not a ceiling.</strong> It represents{' '}
        token count &times; multiplier &times; price per million tokens — the
        minimum spend to generate the repository output in one clean pass.{' '}
        It does <em>not</em> include:{' '}
        <strong>input prompts</strong> sent to the AI,{' '}
        <strong>failed attempts</strong> or retried generations,{' '}
        reasoning tokens beyond the defined multipliers, or{' '}
        <strong>negotiated enterprise rates</strong> (list prices are used).
      </span>
    </aside>
  )
}
