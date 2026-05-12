import type { PropsWithChildren } from 'react'

export function AppShell({ children }: PropsWithChildren) {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <header className="border-b border-white/10 bg-slate-950/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <a className="group flex items-center gap-3" href="/">
            <span className="flex h-9 w-9 items-center justify-center">
              <img alt="TokenMeter logo" className="h-full w-full object-contain" src="/tokenmeter-logo.png" />
            </span>
            <span>
              <span className="block text-sm font-semibold tracking-tight transition group-hover:text-cyan-200">TokenMeter</span>
              <span className="block text-xs text-slate-400">Repository generation economics</span>
            </span>
          </a>
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
