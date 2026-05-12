import type { PropsWithChildren } from 'react'

export function AppShell({ children }: PropsWithChildren) {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <header className="border-b border-white/10 bg-slate-950/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl border border-cyan-400/30 bg-cyan-400/10 text-cyan-300">
              TM
            </div>
            <div>
              <a className="text-sm font-semibold tracking-tight transition hover:text-cyan-200" href="/">
                TokenMeter
              </a>
              <p className="text-xs text-slate-400">Repository generation economics</p>
            </div>
          </div>
          <nav className="hidden items-center gap-6 text-sm text-slate-400 sm:flex">
            <a className="transition hover:text-white" href="#overview">
              Overview
            </a>
            <a className="transition hover:text-white" href="#metrics">
              Metrics
            </a>
            <a className="transition hover:text-white" href="#settings">
              Settings
            </a>
          </nav>
        </div>
      </header>
      <main>{children}</main>
    </div>
  )
}
