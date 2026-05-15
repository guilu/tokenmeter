import type { PropsWithChildren } from 'react'

import { ThemeToggle } from './ThemeToggle'
import { useTheme } from '../hooks/useTheme'

export function AppShell({ children }: PropsWithChildren) {
  useTheme()

  return (
    <div className="min-h-screen bg-bg text-text">
      <header className="border-b border-text/10 bg-bg/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <a className="group flex items-center gap-3" href="/">
            <span className="flex h-9 w-9 items-center justify-center">
              <img alt="TokenMeter logo" className="h-full w-full object-contain" src="/logo-white.png" />
            </span>
            <span>
              <span className="block text-sm font-semibold tracking-tight transition group-hover:text-primary/80">TokenMeter</span>
              <span className="block text-xs text-text/60">Repository generation economics</span>
            </span>
          </a>
          <nav className="hidden items-center gap-6 text-sm text-text/60 sm:flex">
            <a className="transition hover:text-text" href="/#overview">
              Overview
            </a>
            <a className="transition hover:text-text" href="/leaderboards">
              Leaderboards
            </a>
          </nav>
          <ThemeToggle />
        </div>
      </header>
      <main>{children}</main>
    </div>
  )
}
