declare global {
  interface Window {
    dataLayer?: unknown[]
    gtag?: (...args: unknown[]) => void
  }
}

/**
 * Google Analytics 4 integration (TKM-64). All functions are safe no-ops when
 * {@link https://vitejs.dev | VITE_GA_MEASUREMENT_ID} is unset, so development/test builds never
 * load GA. The Measurement ID is public frontend configuration — no secret is involved.
 */
function measurementId(): string | undefined {
  return import.meta.env.VITE_GA_MEASUREMENT_ID
}

export function isAnalyticsEnabled(): boolean {
  return Boolean(measurementId())
}

/**
 * Injects gtag.js and configures GA exactly once. Idempotent: a present {@code window.gtag} short
 * circuits. Page views are sent manually ({@code send_page_view: false}) so SPA navigation is
 * tracked explicitly and the initial view isn't double-counted.
 */
export function initAnalytics(): void {
  const id = measurementId()
  if (!id || typeof window === 'undefined' || window.gtag) {
    return
  }

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${id}`
  document.head.appendChild(script)

  window.dataLayer = window.dataLayer ?? []
  const gtag: NonNullable<Window['gtag']> = (...args) => {
    window.dataLayer!.push(args)
  }
  window.gtag = gtag
  gtag('js', new Date())
  gtag('config', id, { send_page_view: false })
}

export function trackPageView(path: string): void {
  if (!measurementId() || !window.gtag) {
    return
  }
  window.gtag('event', 'page_view', {
    page_path: path,
    page_location: window.location.href,
    page_title: document.title,
  })
}

export function trackEvent(name: string, params: Record<string, unknown> = {}): void {
  if (!measurementId() || !window.gtag) {
    return
  }
  window.gtag('event', name, params)
}
