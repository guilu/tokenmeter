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

export interface LeaderboardPageResponse {
  category: string
  page: number
  size: number
  totalElements: number
  totalPages: number
  filters: Record<string, string>
  entries: LeaderboardEntryResponse[]
}

export interface LeaderboardEntryResponse {
  rank: number
  analysisId: string
  repositoryUrl: string
  owner: string
  name: string
  analyzedAt: string
  totalFiles: number
  totalLines: number
  totalBytes: number
  totalTokens: number
  analysisCount: number
  provider?: string | null
  model?: string | null
  mode?: 'raw' | 'assisted' | 'agentic' | null
  totalCost?: number | null
  costPerMillionTokens?: number | null
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
