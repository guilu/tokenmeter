export interface HealthResponse {
  status: string
  service: string
  timestamp?: string
}

export interface AnalyzeRepositoryRequest {
  repositoryUrl: string
}

export type JobStatus = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export type JobPhase =
  | 'QUEUED'
  | 'CHECKING_CACHE'
  | 'CLONING_REPOSITORY'
  | 'SCANNING_FILES'
  | 'FILTERING_FILES'
  | 'COUNTING_TOKENS'
  | 'CALCULATING_COSTS'
  | 'SAVING_REPORT'
  | 'COMPLETED'
  | 'FAILED'

export interface AnalysisJobAcceptedResponse {
  jobId: string
  status: JobStatus
  statusUrl: string
  analysisId: string | null
}

export interface JobError {
  code: string
  message: string
}

export interface JobMetrics {
  filesDiscovered: number | null
  filesProcessed: number | null
  filesSkipped: number | null
  tokensCounted: number | null
  contextWindows: number | null
  pricingModelsProcessed: number | null
}

export interface JobTimestamps {
  createdAt: string
  startedAt: string | null
  updatedAt: string
  completedAt: string | null
}

export interface AnalysisJobQueueStateResponse {
  runningCount: number
  maxConcurrency: number
  queuePosition?: number | null
}

export interface PricingMetadataResponse {
  snapshotId: string
  primarySource: 'REMOTE' | 'FALLBACK' | 'OVERRIDE'
  capturedAt: string
}

export interface AnalysisJobStatusResponse {
  jobId: string
  status: JobStatus
  phase: JobPhase
  phaseLabel: string
  progressPercent: number
  message: string | null
  analysisId: string | null
  error: JobError | null
  metrics: JobMetrics | null
  timestamps: JobTimestamps
  queueState?: AnalysisJobQueueStateResponse | null
  pricing?: PricingMetadataResponse
}

export interface RepositoryAnalysisResponse {
  id: string
  createdAt: string
  repositoryUrl: string
  status: 'SUCCESS'
  metrics: RepositoryAnalysisMetricsResponse
  costEstimates: RepositoryAnalysisCostEstimateResponse[]
  pricing?: PricingMetadataResponse
}

export interface CostBreakdownResponse {
  analysisId: string
  createdAt: string
  repositoryUrl: string
  summary: CostBreakdownSummaryResponse
  models: CostBreakdownModelResponse[]
  pricing?: PricingMetadataResponse
}

export interface CostBreakdownSummaryResponse {
  totalTokens: number
  totalModels: number
  totalModes: number
}

export interface CostBreakdownModelResponse {
  provider: string
  model: string
  pricing: CostBreakdownPricingResponse | null
  modes: CostBreakdownModeResponse[]
}

export interface CostBreakdownPricingResponse {
  inputTokenPricePerMillion: number
  outputTokenPricePerMillion: number
}

export interface CostBreakdownModeResponse {
  mode: 'raw' | 'assisted' | 'agentic'
  baseTokens: number
  estimatedInputTokens: number
  estimatedOutputTokens: number
  inputCost: number
  outputCost: number
  totalCost: number
  formula: string
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
  engineeringEffort: EngineeringEffortEstimateResponse
}

export interface EngineeringEffortEstimateResponse {
  seniorEngineerHours: number
  engineeringDays: number
  manualImplementationEffort: string
  summary: string
  formula: string
  assumptions: EngineeringEffortAssumptionsResponse
}

export interface EngineeringEffortAssumptionsResponse {
  tokensPerSeniorEngineerHour: number
  hoursPerEngineeringDay: number
  modeComplexityMultiplier: number
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
  pricing?: PricingMetadataResponse
  dominantLanguage?: string | null
}

export interface CostByModeEntry {
  mode: 'raw' | 'assisted' | 'agentic'
  totalCost: string
  analysisCount: number
}

export interface LeaderboardOverviewResponse {
  totalRepos: number
  totalAnalyses: number
  totalTokens: number
  totalBytes: number
  costsByMode: CostByModeEntry[]
  filters?: Record<string, string>
}

export interface LanguageInsightEntry {
  language: string
  totalTokens: number
  repoCount: number
  sharePercent: string
}

export interface LeaderboardLanguagesResponse {
  languages: LanguageInsightEntry[]
  totalTokensAllLanguages: number
  filters?: Record<string, string>
}

export interface TrendingRepositoryResponse {
  fullName: string
  repositoryUrl: string
  description?: string | null
  language?: string | null
  stars: number
  forks: number
  sizeKb?: number | null
  createdAt: string
  updatedAt: string
}

export interface TrendingRepositoriesResponse {
  fetchedAt: string
  since: string
  language?: string | null
  items: TrendingRepositoryResponse[]
}

export interface PricingResponse {
  lastRefreshedAt: string | null
  primarySource: 'litellm' | 'fallback' | 'mixed'
  models: PricingModelResponse[]
}

export interface PricingModelResponse {
  provider: string
  model: string
  inputTokenPricePerMillion: number
  outputTokenPricePerMillion: number
  source: 'REMOTE' | 'FALLBACK' | 'OVERRIDE'
  fetchedAt: string
  externalModelId?: string | null
}
