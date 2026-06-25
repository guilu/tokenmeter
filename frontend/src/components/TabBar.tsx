export interface TabBarItem {
  key: string
  label: string
}

export interface TabBarProps {
  tabs: TabBarItem[]
  activeTab: string
  onSelect: (key: string) => void
  /** id prefix so multiple tablists never collide (default 'tab') */
  idBase?: string
  /** accessible name for the tablist */
  ariaLabel: string
}

function tabClassName(active: boolean): string {
  const base = 'rounded-xl px-4 py-3 text-base font-semibold transition'
  const activeStyle = 'bg-primary text-bg shadow-lg shadow-primary/20'
  const inactiveStyle = 'text-text/80 hover:bg-card/40 hover:text-text'
  return `${base} ${active ? activeStyle : inactiveStyle}`
}

export function TabBar({ tabs, activeTab, onSelect, idBase = 'tab', ariaLabel }: TabBarProps) {
  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      className="mt-8 rounded-2xl border border-text/10 bg-bg/60 p-1.5"
    >
      <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-4">
        {tabs.map((t) => {
          const active = t.key === activeTab
          return (
            <button
              key={t.key}
              type="button"
              role="tab"
              id={`${idBase}-${t.key}`}
              aria-selected={active}
              aria-controls={`${idBase}-panel-${t.key}`}
              tabIndex={active ? 0 : -1}
              onClick={() => onSelect(t.key)}
              className={tabClassName(active)}
            >
              {t.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}
