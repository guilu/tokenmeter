import type {
  AnalyzeRepositoryRequest,
  HealthResponse,
  PricingResponse,
  RepositoryAnalysisResponse,
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

export async function analyzeRepository(
  repositoryUrl = DEFAULT_REPOSITORY_URL,
): Promise<RepositoryAnalysisResponse> {
  const request: AnalyzeRepositoryRequest = { repositoryUrl }
  const response = await fetch('/api/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw await toApiError(response, 'Repository analysis failed')
  }

  return response.json() as Promise<RepositoryAnalysisResponse>
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

async function toApiError(response: Response, fallbackMessage: string) {
  try {
    const body = (await response.json()) as { message?: string; code?: string }
    return new ApiError(body.message ?? fallbackMessage, response.status, body.code)
  } catch {
    return new ApiError(`${fallbackMessage} with status ${response.status}`, response.status)
  }
}
