import { useTheme } from '../hooks/useTheme'

export function ThemeToggle() {
  const { isDark, toggle } = useTheme()

  return (
    <button
      aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      className="rounded-full border border-text/20 px-3 py-1.5 text-sm text-text/60 transition hover:border-text/40 hover:text-text"
      onClick={toggle}
      type="button"
    >
      {isDark ? '☀️' : '🌙'}
    </button>
  )
}
