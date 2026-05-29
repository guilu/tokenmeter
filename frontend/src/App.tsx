import { useEffect } from 'react'

import { AppShell } from './components/AppShell'
import { DashboardPage } from './pages/DashboardPage'
import { LeaderboardsPage } from './pages/LeaderboardsPage'
import { ModelsPage } from './pages/ModelsPage'

export default function App() {
  const path = window.location.pathname
  const search = new URLSearchParams(window.location.search)
  const isLeaderboardsPage = path === '/leaderboards' || search.has('leaderboards')
  const isModelsPage = path === '/models'

  useEffect(() => {
    if (new URLSearchParams(window.location.search).has('leaderboards')) {
      window.history.replaceState(null, '', '/leaderboards')
    }
  }, [])

  return (
    <AppShell>
      {isModelsPage ? <ModelsPage /> : isLeaderboardsPage ? <LeaderboardsPage /> : <DashboardPage />}
    </AppShell>
  )
}
