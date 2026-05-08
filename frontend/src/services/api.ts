import type {
  AnalyzeRepositoryRequest,
  HealthResponse,
  PricingResponse,
  RepositoryAnalysisResponse,
} from '../types/api'

export const DEFAULT_REPOSITORY_URL = 'https://github.com/guilu/tokenmeter'

export async function getHealth(): Promise<HealthResponse> {
  const response = await fetch('/api/health')

  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`)
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
    throw new Error(`Repository analysis failed with status ${response.status}`)
  }

  return response.json() as Promise<RepositoryAnalysisResponse>
}

export async function getPricing(): Promise<PricingResponse> {
  const response = await fetch('/api/pricing')

  if (!response.ok) {
    throw new Error(`Pricing request failed with status ${response.status}`)
  }

  return response.json() as Promise<PricingResponse>
}
