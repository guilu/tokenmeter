import type { ReactNode } from 'react'

import { numberFormatter } from '../../utils/formatters'
import type { LanguageBreakdownItem } from '../../utils/formatters'
import { percentOf } from '../../utils/resultsCost'

export interface LanguagesTabProps {
  languages: LanguageBreakdownItem[]
  totalTokens: number
}

function Panel({ eyebrow, title, children }: { eyebrow: string; title: string; children: ReactNode }) {
  return (
    <section className="rounded-3xl bg-card/20 p-5 shadow-2xl shadow-bg/20 sm:p-6">
      <p className="text-sm text-text/60">{eyebrow}</p>
      <h2 className="mt-1 text-2xl font-semibold text-text">{title}</h2>
      <div className="mt-6">{children}</div>
    </section>
  )
}

function BarList({
  emptyLabel,
  items,
  valueFormatter,
}: {
  emptyLabel: string
  items: Array<{ label: string; value: number; helper: string; percent: number }>
  valueFormatter: (value: number) => string
}) {
  if (items.length === 0) {
    return (
      <p className="rounded-2xl border border-dashed border-text/10 p-6 text-sm text-text/60">
        {emptyLabel}
      </p>
    )
  }

  return (
    <div className="space-y-4">
      {items.map((item) => (
        <div key={`${item.label}-${item.helper}`}>
          <div className="mb-2 flex items-start justify-between gap-3 text-sm">
            <div className="min-w-0">
              <p className="truncate font-medium text-text" title={item.label}>
                {item.label}
              </p>
              <p className="truncate text-text/50" title={item.helper}>
                {item.helper}
              </p>
            </div>
            <p className="shrink-0 text-right font-medium text-primary">
              {valueFormatter(item.value)}
            </p>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-text/10">
            <div
              className="h-full rounded-full bg-gradient-to-r from-primary to-secondary"
              style={{ width: `${Math.max(2, Math.min(100, item.percent))}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  )
}

export function LanguagesTab({ languages, totalTokens }: LanguagesTabProps) {
  return (
    <Panel eyebrow="Language breakdown" title="Repository composition">
      <BarList
        emptyLabel="No language metrics available."
        items={languages.slice(0, 8).map((language) => ({
          label: language.language,
          value: language.tokens,
          helper: `${numberFormatter.format(language.files)} files · ${numberFormatter.format(language.lines)} lines`,
          percent: percentOf(language.tokens, totalTokens),
        }))}
        valueFormatter={(value) => `${new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(value)} tokens`}
      />
    </Panel>
  )
}
