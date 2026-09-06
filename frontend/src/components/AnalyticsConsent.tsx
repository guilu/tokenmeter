import { useState } from 'react'

import {
  ANALYTICS_CONSENT_KEY,
  initAnalytics,
  isAnalyticsConfigured,
  trackPageView,
} from '../analytics/analytics'

export { ANALYTICS_CONSENT_KEY }

type Consent = 'granted' | 'denied' | null

function storedConsent(): Consent {
  const value = localStorage.getItem(ANALYTICS_CONSENT_KEY)
  return value === 'granted' || value === 'denied' ? value : null
}

function deleteGoogleAnalyticsCookies(): void {
  const hostname = window.location.hostname
  const domains = ['', hostname, `.${hostname}`, '.backendtothefuture.com']
  document.cookie
    .split(';')
    .map((cookie) => cookie.split('=')[0].trim())
    .filter((name) => name === '_ga' || name.startsWith('_ga_'))
    .forEach((name) => {
      domains.forEach((domain) => {
        const domainPart = domain ? `; domain=${domain}` : ''
        document.cookie = `${name}=; Max-Age=0; path=/${domainPart}; SameSite=Lax`
      })
    })
}

function disableAnalytics(): void {
  window.gtag?.('consent', 'update', { analytics_storage: 'denied' })
  document
    .querySelectorAll<HTMLScriptElement>(
      'script[src*="googletagmanager.com/gtag"]',
    )
    .forEach((script) => script.remove())
  delete window.gtag
  window.dataLayer = undefined
  deleteGoogleAnalyticsCookies()
}

export function AnalyticsConsent() {
  const configured = isAnalyticsConfigured()
  const [consent, setConsent] = useState<Consent>(() =>
    configured ? storedConsent() : null,
  )
  const [settingsOpen, setSettingsOpen] = useState(false)
  const showDialog = configured && (consent === null || settingsOpen)

  if (!configured) return null

  function save(next: Exclude<Consent, null>): void {
    localStorage.setItem(ANALYTICS_CONSENT_KEY, next)
    setConsent(next)
    setSettingsOpen(false)
    if (next === 'granted') {
      initAnalytics()
      trackPageView(window.location.pathname)
    } else {
      disableAnalytics()
    }
  }

  return (
    <>
      {showDialog && (
        <section
          aria-labelledby="analytics-consent-title"
          aria-modal="false"
          className="fixed bottom-4 left-4 right-4 z-[60] mx-auto max-w-2xl rounded-2xl border border-secondary/30 bg-bg p-5 shadow-2xl"
          role="dialog"
        >
          <h2 className="font-bold text-text" id="analytics-consent-title">
            Analytics consent
          </h2>
          <p className="mt-2 text-sm text-text/70">
            Help improve TokenMeter with anonymous usage analytics. Google
            Analytics is not loaded unless you accept. No repository name, URL,
            account, or other personal data is sent.
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            <button
              className="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white"
              onClick={() => save('granted')}
              type="button"
            >
              Accept analytics
            </button>
            <button
              className="rounded-xl border border-secondary/30 px-4 py-2 text-sm font-semibold"
              onClick={() => save('denied')}
              type="button"
            >
              Reject analytics
            </button>
            {consent === 'granted' && (
              <button
                className="rounded-xl border border-red-500/30 px-4 py-2 text-sm font-semibold text-red-400"
                onClick={() => save('denied')}
                type="button"
              >
                Withdraw analytics consent
              </button>
            )}
          </div>
        </section>
      )}
      {!showDialog && (
        <button
          className="fixed bottom-3 right-3 z-50 rounded-lg border border-secondary/25 bg-bg/95 px-3 py-1.5 text-xs text-text/65 shadow-lg"
          onClick={() => setSettingsOpen(true)}
          type="button"
        >
          Analytics settings
        </button>
      )}
    </>
  )
}
