const modes = ['raw', 'assisted', 'agentic'] as const
const providers = ['openai', 'anthropic', 'google', 'deepseek'] as const

interface LeaderboardFilterBarProps {
  mode: string
  provider: string
  model: string
  onModeChange: (mode: string) => void
  onProviderChange: (provider: string) => void
  onModelChange: (model: string) => void
}

export function LeaderboardFilterBar({
  mode,
  provider,
  model,
  onModeChange,
  onProviderChange,
  onModelChange,
}: LeaderboardFilterBarProps) {
  return (
    <div className="sticky top-16 z-40 backdrop-blur bg-card/80 border-b border-text/10 px-4 py-3">
      <div className="mx-auto max-w-6xl grid gap-3 sm:grid-cols-3">
        <label className="text-xs font-semibold uppercase tracking-[0.2em] text-text/60">
          <span>Mode</span>
          <div className="relative mt-2">
            <select
              className="w-full appearance-none rounded-xl border border-text/10 bg-bg px-3 py-2 pr-8 text-sm text-text"
              value={mode}
              onChange={(event) => onModeChange(event.target.value)}
            >
              {modes.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
            <div className="pointer-events-none absolute inset-y-0 right-2.5 flex items-center">
              <svg
                className="h-4 w-4 text-text/50"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                viewBox="0 0 24 24"
              >
                <path d="M19 9l-7 7-7-7" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
          </div>
        </label>

        <label className="text-xs font-semibold uppercase tracking-[0.2em] text-text/60">
          <span>Provider</span>
          <div className="relative mt-2">
            <select
              className="w-full appearance-none rounded-xl border border-text/10 bg-bg px-3 py-2 pr-8 text-sm text-text"
              value={provider}
              onChange={(event) => onProviderChange(event.target.value)}
            >
              <option value="">All providers</option>
              {providers.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
            <div className="pointer-events-none absolute inset-y-0 right-2.5 flex items-center">
              <svg
                className="h-4 w-4 text-text/50"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                viewBox="0 0 24 24"
              >
                <path d="M19 9l-7 7-7-7" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
          </div>
        </label>

        <label className="text-xs font-semibold uppercase tracking-[0.2em] text-text/60">
          <span>Model</span>
          <input
            className="mt-2 w-full rounded-xl border border-text/10 bg-bg px-3 py-2 text-sm text-text placeholder:text-text/40"
            placeholder="e.g. gpt-4o"
            value={model}
            onChange={(event) => onModelChange(event.target.value)}
          />
        </label>
      </div>
    </div>
  )
}
