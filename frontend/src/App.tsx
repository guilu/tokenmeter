import { useEffect } from 'react'

import { AppShell } from './components/AppShell'
import { DashboardPage } from './pages/DashboardPage'
import { LeaderboardsPage } from './pages/LeaderboardsPage'
import { ModelsPage } from './pages/ModelsPage'

export default function App() {
  const path = window.location.pathname
  const search = new URLSearchParams(window.location.search)
  const hasLeaderboardsQuery = search.has('leaderboards')
  const isLeaderboardsPage = path === '/leaderboards' || hasLeaderboardsQuery
  const isModelsPage = path === '/models'

  useEffect(() => {
    if (hasLeaderboardsQuery) {
      window.history.replaceState(null, '', '/leaderboards')
    }
  }, [hasLeaderboardsQuery])

  return (
    <AppShell>
      {isModelsPage ? <ModelsPage /> : isLeaderboardsPage ? <LeaderboardsPage /> : <DashboardPage />}
    </AppShell>
  )
}
