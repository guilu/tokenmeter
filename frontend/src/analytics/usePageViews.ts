import { useEffect } from 'react'

import { initAnalytics, trackPageView } from './analytics'

const LOCATION_CHANGE_EVENT = 'tm:locationchange'

function currentPath(): string {
  return window.location.pathname
}

let historyPatched = false

/**
 * Patches {@code history.pushState} once so client-side navigations (e.g. the analysis result view)
 * emit a synthetic event we can listen to — the History API fires {@code popstate} only for
 * back/forward, not for {@code pushState}.
 */
function installHistoryEvents(): void {
  if (historyPatched || typeof history === 'undefined') {
    return
  }
  historyPatched = true
  const originalPushState = history.pushState.bind(history)
  history.pushState = (...args: Parameters<typeof history.pushState>) => {
    const result = originalPushState(...args)
    window.dispatchEvent(new Event(LOCATION_CHANGE_EVENT))
    return result
  }
}

/**
 * Initializes GA (no-op without a Measurement ID) and reports a page view on mount and on every
 * subsequent SPA navigation. Full-page navigations between routes are covered by the mount path on
 * the freshly loaded document.
 */
export function usePageViews(): void {
  useEffect(() => {
    initAnalytics()
    installHistoryEvents()
    trackPageView(currentPath())

    const handler = () => trackPageView(currentPath())
    window.addEventListener('popstate', handler)
    window.addEventListener(LOCATION_CHANGE_EVENT, handler)
    return () => {
      window.removeEventListener('popstate', handler)
      window.removeEventListener(LOCATION_CHANGE_EVENT, handler)
    }
  }, [])
}
