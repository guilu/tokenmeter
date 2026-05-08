export interface HealthResponse {
  status: string
  service: string
  timestamp?: string
}

export interface AnalyzeRepositoryRequest {
  repositoryUrl: string
}

export interface RepositoryAnalysisResponse {
  id: string
  createdAt: string
  repositoryUrl: string
  status: 'SUCCESS'
  metrics: RepositoryAnalysisMetricsResponse
}

export interface RepositoryAnalysisMetricsResponse {
  totalFiles: number
  totalLines: number
  totalBytes: number
  tokenEncoding: string
  totalTokens: number
  languages: Record<string, RepositoryAnalysisLanguageMetricsResponse>
}

export interface RepositoryAnalysisLanguageMetricsResponse {
  language: string
  files: number
  lines: number
  bytes: number
  tokens: number
}

export interface PricingResponse {
  models: PricingModelResponse[]
}

export interface PricingModelResponse {
  provider: string
  model: string
  inputTokenPricePerMillion: number
  outputTokenPricePerMillion: number
}
