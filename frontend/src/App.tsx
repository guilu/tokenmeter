import { useEffect } from 'react'

import { AppShell } from './components/AppShell'
import { DashboardPage } from './pages/DashboardPage'
import { LeaderboardsPage } from './pages/LeaderboardsPage'

export default function App() {
  const isLeaderboardsPage =
    window.location.pathname === '/leaderboards' || new URLSearchParams(window.location.search).has('leaderboards')

  useEffect(() => {
    if (new URLSearchParams(window.location.search).has('leaderboards')) {
      window.history.replaceState(null, '', '/leaderboards')
    }
  }, [])

  return <AppShell>{isLeaderboardsPage ? <LeaderboardsPage /> : <DashboardPage />}</AppShell>
}
