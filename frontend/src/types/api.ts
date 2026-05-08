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
  costEstimates: RepositoryAnalysisCostEstimateResponse[]
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

export interface RepositoryAnalysisCostEstimateResponse {
  provider: string
  model: string
  mode: 'raw' | 'assisted' | 'agentic'
  baseTokens: number
  estimatedInputTokens: number
  estimatedOutputTokens: number
  inputCost: number
  outputCost: number
  totalCost: number
  formula: string
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
