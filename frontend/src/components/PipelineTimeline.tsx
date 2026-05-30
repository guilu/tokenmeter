import { useElapsedSeconds } from '../hooks/useElapsedSeconds'
import type { AnalysisJobStatusResponse } from '../types/api'
import { analysisStages, stageIndexFromJob } from '../utils/analysisJobProgress'

function CheckIcon() {
  return (
    <svg
      aria-hidden="true"
      className="h-3.5 w-3.5 shrink-0 text-primary"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.5}
      viewBox="0 0 24 24"
    >
      <path d="M5 13l4 4L19 7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function XIcon() {
  return (
    <svg
      aria-hidden="true"
      className="h-3.5 w-3.5 shrink-0"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.5}
      viewBox="0 0 24 24"
    >
      <path d="M6 18L18 6M6 6l12 12" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function ActiveStageContent({
  job,
  label,
  detail,
  elapsedSeconds,
}: {
  job: AnalysisJobStatusResponse | null
  label: string
  detail: string
  elapsedSeconds: number | null
}) {
  return (
    <>
      <div className="flex min-w-0 flex-1 flex-col">
        <span className="font-semibold text-text">
          {label}
          {elapsedSeconds !== null ? ` — ${elapsedSeconds}s` : ''}
        </span>
        <span className="mt-0.5 text-text/60">{detail}</span>
        {job?.status === 'QUEUED' && job.queueState && job.queueState.queuePosition != null ? (
          <span className="mt-1 tabular-nums text-text/60">
            Position {job.queueState.queuePosition} · {job.queueState.runningCount}/{job.queueState.maxConcurrency} running
          </span>
        ) : null}
      </div>
    </>
  )
}

export function PipelineTimeline({ job }: { job: AnalysisJobStatusResponse | null }) {
  const activeIndex = stageIndexFromJob(job)
  const elapsedSeconds = useElapsedSeconds(job?.timestamps.startedAt ?? null)

  const isSuccess = job?.status === 'SUCCESS' && job.analysisId != null
  const isFailed = job?.status === 'FAILED'

  return (
    <ol className="space-y-2">
      {analysisStages.map((stage, i) => {
        let stageState: 'completed' | 'active' | 'failed' | 'pending'

        if (isSuccess) {
          stageState = 'completed'
        } else if (isFailed) {
          if (i < activeIndex) {
            stageState = 'completed'
          } else if (i === activeIndex) {
            stageState = 'failed'
          } else {
            stageState = 'pending'
          }
        } else {
          if (i < activeIndex) {
            stageState = 'completed'
          } else if (i === activeIndex) {
            stageState = 'active'
          } else {
            stageState = 'pending'
          }
        }

        const label = i === activeIndex && !isSuccess ? (job?.message ?? job?.phaseLabel ?? stage.label) : stage.label

        return (
          <li
            className={`flex items-start gap-3 py-1 text-xs${stageState === 'active' ? ' stage-enter' : ''}`}
            data-stage-state={stageState}
            key={i}
          >
            {stageState === 'completed' ? (
              <>
                <span className="mt-0.5 flex h-3.5 w-3.5 shrink-0 items-center justify-center">
                  <CheckIcon />
                </span>
                <span className="text-text/70">{stage.label}</span>
              </>
            ) : stageState === 'active' ? (
              <>
                <span className="mt-0.5 h-2 w-2 shrink-0 rounded-full bg-primary animate-pulse" />
                <ActiveStageContent
                  detail={stage.detail}
                  elapsedSeconds={elapsedSeconds}
                  job={job}
                  label={label}
                />
              </>
            ) : stageState === 'failed' ? (
              <>
                <span className="mt-0.5 flex h-3.5 w-3.5 shrink-0 items-center justify-center text-red-700 dark:text-red-300">
                  <XIcon />
                </span>
                <span className="text-red-700 dark:text-red-300">{label}</span>
              </>
            ) : (
              <>
                <span className="mt-0.5 h-2 w-2 shrink-0 rounded-full bg-text/20" />
                <span className="text-text/40">{stage.label}</span>
              </>
            )}
          </li>
        )
      })}
    </ol>
  )
}
