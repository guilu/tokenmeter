export const numberFormatter = new Intl.NumberFormat('en-US')
export const compactNumberFormatter = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })
export const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export const costModes = ['raw', 'assisted', 'agentic'] as const
export type CostMode = (typeof costModes)[number]

export interface LanguageBreakdownItem {
  language: string
  files: number
  lines: number
  bytes: number
  tokens: number
}
