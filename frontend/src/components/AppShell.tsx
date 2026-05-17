import type { PropsWithChildren } from 'react'
import { useState } from 'react'

import { Footer } from './Footer'
import { ThemeToggle } from './ThemeToggle'
import { useTheme } from '../hooks/useTheme'

export function AppShell({ children }: PropsWithChildren) {
  const { isDark, toggle } = useTheme()
  const [menuOpen, setMenuOpen] = useState(false)
  const path = window.location.pathname
  const searchParams = new URLSearchParams(window.location.search)
  const effectivePath =
    path === '/leaderboards' || searchParams.has('leaderboards') ? '/leaderboards' : path

  const isActive = (href: string) =>
    effectivePath === href || effectivePath.startsWith(href + '/')

  const linkCls = (href: string) =>
    `flex items-center gap-2 px-3 py-2 rounded-xl text-sm font-medium transition-colors ${
      isActive(href)
        ? 'bg-primary/10 text-primary'
        : 'text-text/65 hover:text-text hover:bg-secondary/10'
    }`

  const mobileLinkCls = (href: string) =>
    `w-full text-left flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-colors ${
      isActive(href)
        ? 'bg-primary/10 text-primary'
        : 'text-text/65 hover:text-text hover:bg-secondary/10'
    }`

  return (
    <div className="min-h-screen bg-bg text-text">
      <nav className="fixed left-0 top-0 z-50 w-full border-b border-secondary/20 bg-bg/90 backdrop-blur-md">

        {/* Main bar */}
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-5">

          {/* Logo */}
          <a className="flex items-center gap-2.5 text-lg font-extrabold tracking-tight" href="/">
            <span className="flex h-8 w-8 items-center justify-center">
              <img alt="TokenMeter logo" className="h-full w-full object-contain" src={isDark ? '/logo-dark.png' : '/logo-light.png'} />
            </span>
            <span className="flex flex-col leading-tight">
              <span className={isDark ? 'text-white' : 'text-primary'}>tokenmeter</span>
              <span className="text-[10px] font-normal text-text/50">by diegobarrioh</span>
            </span>
          </a>

          {/* Desktop links */}
          <div className="hidden items-center gap-1 md:flex">
            <a className={linkCls('/')} href="/">Overview</a>
            <a className={linkCls('/models')} href="/models">Models</a>
            <a className={linkCls('/leaderboards')} href="/leaderboards">Leaderboards</a>
          </div>

          {/* Right: theme toggle + hamburger */}
          <div className="flex items-center gap-2">
            <ThemeToggle isDark={isDark} toggle={toggle} />

            <button
              aria-expanded={menuOpen}
              aria-label={menuOpen ? 'Cerrar menú' : 'Abrir menú'}
              className="flex h-9 w-9 items-center justify-center rounded-xl border border-secondary/25 bg-secondary/5 transition-colors hover:bg-secondary/10 md:hidden"
              onClick={() => setMenuOpen(o => !o)}
              type="button"
            >
              <div className="relative h-4 w-5">
                <span className={`absolute left-0 block h-0.5 w-5 rounded-full bg-current transition-all duration-300 origin-center ${menuOpen ? 'top-[7px] rotate-45' : 'top-0'}`} />
                <span className={`absolute left-0 block h-0.5 rounded-full bg-current transition-all duration-200 ${menuOpen ? 'left-1/2 top-[7px] w-0 opacity-0' : 'top-[7px] w-5 opacity-100'}`} />
                <span className={`absolute left-0 block h-0.5 w-5 rounded-full bg-current transition-all duration-300 origin-center ${menuOpen ? 'top-[7px] -rotate-45' : 'top-[14px]'}`} />
              </div>
            </button>
          </div>
        </div>

        {/* Mobile menu */}
        <div
          className={`transition-all duration-300 ease-in-out md:hidden ${
            menuOpen ? 'max-h-[calc(100vh-4rem)] overflow-y-auto opacity-100' : 'max-h-0 overflow-hidden opacity-0'
          }`}
        >
          <div className="space-y-1 border-t border-secondary/15 bg-bg/95 px-4 pb-4 pt-2 backdrop-blur-md">
            <a className={mobileLinkCls('/')} href="/" onClick={() => setMenuOpen(false)}>Overview</a>
            <a className={mobileLinkCls('/models')} href="/models" onClick={() => setMenuOpen(false)}>Models</a>
            <a className={mobileLinkCls('/leaderboards')} href="/leaderboards" onClick={() => setMenuOpen(false)}>Leaderboards</a>
          </div>
        </div>
      </nav>

      <main className="pt-16">{children}</main>
      <Footer />
    </div>
  )
}
