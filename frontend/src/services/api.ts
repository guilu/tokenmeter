import type {
  AnalysisJobAcceptedResponse,
  AnalysisJobStatusResponse,
  AnalyzeRepositoryRequest,
  HealthResponse,
  LeaderboardLanguagesResponse,
  LeaderboardOverviewResponse,
  LeaderboardPageResponse,
  PricingRefreshResult,
  PricingResponse,
  RepositoryAnalysisResponse,
  TrendingRepositoriesResponse,
} from '../types/api'

export const DEFAULT_REPOSITORY_URL = 'https://github.com/guilu/tokenmeter'

export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export async function getHealth(): Promise<HealthResponse> {
  const response = await fetch('/api/health')

  if (!response.ok) {
    throw await toApiError(response, 'Health check failed')
  }

  return response.json() as Promise<HealthResponse>
}

export async function submitAnalysis(
  repositoryUrl = DEFAULT_REPOSITORY_URL ?? 'https://github.com/user/repo',
): Promise<AnalysisJobAcceptedResponse> {
  const request: AnalyzeRepositoryRequest = { repositoryUrl }
  const response = await fetch('/api/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw await toApiError(response, 'Repository analysis failed')
  }

  return response.json() as Promise<AnalysisJobAcceptedResponse>
}

export async function fetchAnalysisJobStatus(
  jobId: string,
  signal?: AbortSignal,
): Promise<AnalysisJobStatusResponse> {
  const response = await fetch(`/api/analyze/jobs/${encodeURIComponent(jobId)}`, { signal })

  if (!response.ok) {
    throw await toApiError(response, 'Job status request failed')
  }

  return response.json() as Promise<AnalysisJobStatusResponse>
}

export async function getAnalysis(analysisId: string): Promise<RepositoryAnalysisResponse> {
  const response = await fetch(`/api/analyze/${encodeURIComponent(analysisId)}`)

  if (!response.ok) {
    throw await toApiError(response, 'Analysis request failed')
  }

  return response.json() as Promise<RepositoryAnalysisResponse>
}

export async function getPricing(): Promise<PricingResponse> {
  const response = await fetch('/api/pricing')

  if (!response.ok) {
    throw await toApiError(response, 'Pricing request failed')
  }

  return response.json() as Promise<PricingResponse>
}

export async function refreshPricing(): Promise<PricingRefreshResult> {
  const response = await fetch('/api/admin/pricing/refresh', { method: 'POST' })

  if (!response.ok) {
    throw await toApiError(response, 'Could not refresh prices')
  }

  return response.json() as Promise<PricingRefreshResult>
}

export async function getLeaderboard(params: {
  category: string
  page?: number
  size?: number
  mode?: string
  provider?: string
  model?: string
}): Promise<LeaderboardPageResponse> {
  const searchParams = new URLSearchParams({
    category: params.category,
    page: String(params.page ?? 0),
    size: String(params.size ?? 12),
  })
  if (params.mode) searchParams.set('mode', params.mode)
  if (params.provider) searchParams.set('provider', params.provider)
  if (params.model) searchParams.set('model', params.model)

  const response = await fetch(`/api/leaderboards?${searchParams.toString()}`)

  if (!response.ok) {
    throw await toApiError(response, 'Leaderboard request failed')
  }

  return response.json() as Promise<LeaderboardPageResponse>
}

export async function getLeaderboardOverview(params: {
  mode?: string
  provider?: string
  model?: string
}): Promise<LeaderboardOverviewResponse> {
  const searchParams = new URLSearchParams()
  if (params.mode) searchParams.set('mode', params.mode)
  if (params.provider) searchParams.set('provider', params.provider)
  if (params.model) searchParams.set('model', params.model)

  const query = searchParams.toString()
  const response = await fetch(`/api/leaderboards/insights/overview${query ? `?${query}` : ''}`)

  if (!response.ok) {
    throw await toApiError(response, 'Leaderboard overview request failed')
  }

  return response.json() as Promise<LeaderboardOverviewResponse>
}

export async function getLeaderboardLanguages(params: {
  mode?: string
  provider?: string
  model?: string
}): Promise<LeaderboardLanguagesResponse> {
  const searchParams = new URLSearchParams()
  if (params.mode) searchParams.set('mode', params.mode)
  if (params.provider) searchParams.set('provider', params.provider)
  if (params.model) searchParams.set('model', params.model)

  const query = searchParams.toString()
  const response = await fetch(`/api/leaderboards/insights/languages${query ? `?${query}` : ''}`)

  if (!response.ok) {
    throw await toApiError(response, 'Leaderboard languages request failed')
  }

  return response.json() as Promise<LeaderboardLanguagesResponse>
}

export async function getTrendingRepositories(params?: {
  since?: string
  limit?: number
  language?: string
}): Promise<TrendingRepositoriesResponse> {
  const searchParams = new URLSearchParams()
  if (params?.since) searchParams.set('since', params.since)
  if (params?.limit != null) searchParams.set('limit', String(params.limit))
  if (params?.language) searchParams.set('language', params.language)

  const query = searchParams.toString()
  const response = await fetch(`/api/repositories/trending${query ? `?${query}` : ''}`)

  if (!response.ok) {
    throw await toApiError(response, 'Trending repositories request failed')
  }

  return response.json() as Promise<TrendingRepositoriesResponse>
}

async function toApiError(response: Response, fallbackMessage: string) {
  try {
    const body = (await response.json()) as { message?: string; code?: string }
    return new ApiError(body.message ?? fallbackMessage, response.status, body.code)
  } catch {
    return new ApiError(`${fallbackMessage} with status ${response.status}`, response.status)
  }
}
