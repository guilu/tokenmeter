import type { AnalysisJobStatusResponse, JobPhase } from '../types/api'

export interface AnalysisStage {
  label: string
  detail: string
}

export const analysisStages: readonly AnalysisStage[] = [
  { label: 'Cloning repository', detail: 'Opening a clean workspace and fetching the public Git history.' },
  { label: 'Detecting languages', detail: 'Classifying source files, frameworks and generated artifacts.' },
  { label: 'Parsing files', detail: 'Filtering noise and preparing a normalized code corpus.' },
  { label: 'Counting tokens', detail: 'Measuring the repository footprint with model-compatible encoding.' },
  { label: 'Building context windows', detail: 'Estimating prompt chunks and context required to recreate the project.' },
  { label: 'Simulating AI workflows', detail: 'Comparing raw, assisted and agentic generation strategies.' },
  { label: 'Calculating pricing models', detail: 'Applying input, output and workflow overhead across model families.' },
  { label: 'Generating estimates', detail: 'Compiling the cost intelligence report and shareable analysis.' },
] as const

/**
 * Maps a backend {@link JobPhase} to the index of the 8 frontend pipeline stages rendered in
 * `LoadingState`. Sub-phases inside `COUNTING_TOKENS` are derived dynamically from
 * `metrics.contextWindows` / `metrics.pricingModelsProcessed` becoming non-null.
 */
const PHASE_STAGE_INDEX: Record<JobPhase, number> = {
  QUEUED: 0,
  CHECKING_CACHE: 0,
  CLONING_REPOSITORY: 0,
  SCANNING_FILES: 1,
  FILTERING_FILES: 2,
  COUNTING_TOKENS: 3,
  CALCULATING_COSTS: 6,
  SAVING_REPORT: 7,
  COMPLETED: 7,
  FAILED: 7,
}

export function stageIndexFromJob(job: AnalysisJobStatusResponse | null): number {
  if (!job) return 0
  const base = PHASE_STAGE_INDEX[job.phase] ?? 0
  if (job.phase === 'COUNTING_TOKENS') {
    const pricingDone = (job.metrics?.pricingModelsProcessed ?? null) !== null
    if (pricingDone) return 5
    const windowsDone = (job.metrics?.contextWindows ?? null) !== null
    if (windowsDone) return 4
    return base
  }
  return base
}

/**
 * Clamps the rendered progress percentage so the UI never reaches 100% before the backend confirms
 * a `SUCCESS` snapshot with a non-null `analysisId`. Enforces the *Client Progress Invariants*
 * spec scenario "Client suppresses 100% while RUNNING".
 */
export function progressFromJob(job: AnalysisJobStatusResponse | null): number {
  if (!job) return 0
  if (job.status === 'SUCCESS' && job.analysisId) {
    return 100
  }
  const raw = Number.isFinite(job.progressPercent) ? job.progressPercent : 0
  return Math.max(0, Math.min(99, raw))
}
