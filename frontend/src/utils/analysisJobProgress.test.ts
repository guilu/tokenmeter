import { describe, expect, it } from 'vitest'

import type { AnalysisJobStatusResponse, JobMetrics } from '../types/api'
import {
  COUNTING_TOKENS_MICROCOPY,
  analysisStages,
  liveStatsFromMetrics,
  loadingDetailFromJob,
  progressFromJob,
  stageIndexFromJob,
} from './analysisJobProgress'

function metrics(overrides: Partial<JobMetrics> = {}): JobMetrics {
  return {
    filesDiscovered: null,
    filesProcessed: null,
    filesSkipped: null,
    tokensCounted: null,
    contextWindows: null,
    pricingModelsProcessed: null,
    ...overrides,
  }
}

function snapshot(overrides: Partial<AnalysisJobStatusResponse> = {}): AnalysisJobStatusResponse {
  return {
    jobId: 'job-1',
    status: 'RUNNING',
    phase: 'COUNTING_TOKENS',
    phaseLabel: 'Counting tokens',
    progressPercent: 50,
    message: null,
    analysisId: null,
    error: null,
    metrics: null,
    timestamps: {
      createdAt: '2025-01-01T00:00:00Z',
      startedAt: '2025-01-01T00:00:01Z',
      updatedAt: '2025-01-01T00:00:02Z',
      completedAt: null,
    },
    ...overrides,
  }
}

describe('analysisStages', () => {
  it('exports 8 analysisStages with label and detail fields', () => {
    expect(analysisStages).toHaveLength(8)
    for (const stage of analysisStages) {
      expect(typeof stage.label).toBe('string')
      expect(stage.label.length).toBeGreaterThan(0)
      expect(typeof stage.detail).toBe('string')
      expect(stage.detail.length).toBeGreaterThan(0)
    }
  })
})

describe('progressFromJob', () => {
  it('returns 0 when the job is null', () => {
    expect(progressFromJob(null)).toBe(0)
  })

  it('clamps progress to <= 99 while the job is not SUCCESS with analysisId', () => {
    expect(progressFromJob(snapshot({ status: 'RUNNING', progressPercent: 95 }))).toBe(95)
    // Even if the backend reported 100 mistakenly, the client must never render 100% in-flight.
    expect(progressFromJob(snapshot({ status: 'RUNNING', progressPercent: 100 }))).toBe(99)
    expect(
      progressFromJob(snapshot({ status: 'SUCCESS', progressPercent: 100, analysisId: null })),
    ).toBe(99)
  })

  it('allows 100 only when status is SUCCESS and analysisId is present', () => {
    expect(
      progressFromJob(
        snapshot({
          status: 'SUCCESS',
          phase: 'COMPLETED',
          progressPercent: 100,
          analysisId: 'analysis-xyz',
        }),
      ),
    ).toBe(100)
  })

  it('floors negative or NaN progressPercent to 0', () => {
    expect(progressFromJob(snapshot({ progressPercent: -5 }))).toBe(0)
    expect(progressFromJob(snapshot({ progressPercent: Number.NaN }))).toBe(0)
  })
})

describe('stageIndexFromJob', () => {
  it('maps QUEUED/CHECKING_CACHE/CLONING_REPOSITORY to stage 0', () => {
    expect(stageIndexFromJob(snapshot({ phase: 'QUEUED' }))).toBe(0)
    expect(stageIndexFromJob(snapshot({ phase: 'CHECKING_CACHE' }))).toBe(0)
    expect(stageIndexFromJob(snapshot({ phase: 'CLONING_REPOSITORY' }))).toBe(0)
  })

  it('maps mid-pipeline phases to their stages', () => {
    expect(stageIndexFromJob(snapshot({ phase: 'SCANNING_FILES' }))).toBe(1)
    expect(stageIndexFromJob(snapshot({ phase: 'FILTERING_FILES' }))).toBe(2)
    expect(stageIndexFromJob(snapshot({ phase: 'CALCULATING_COSTS' }))).toBe(6)
    expect(stageIndexFromJob(snapshot({ phase: 'SAVING_REPORT' }))).toBe(7)
    expect(stageIndexFromJob(snapshot({ phase: 'COMPLETED' }))).toBe(7)
  })

  it('shifts inside COUNTING_TOKENS as metrics fill in', () => {
    const base = snapshot({ phase: 'COUNTING_TOKENS', metrics: null })
    expect(stageIndexFromJob(base)).toBe(3)

    expect(
      stageIndexFromJob({
        ...base,
        metrics: {
          filesDiscovered: 10,
          filesProcessed: 10,
          filesSkipped: 0,
          tokensCounted: 1000,
          contextWindows: 3,
          pricingModelsProcessed: null,
        },
      }),
    ).toBe(4)

    expect(
      stageIndexFromJob({
        ...base,
        metrics: {
          filesDiscovered: 10,
          filesProcessed: 10,
          filesSkipped: 0,
          tokensCounted: 1000,
          contextWindows: 3,
          pricingModelsProcessed: 5,
        },
      }),
    ).toBe(5)
  })
})

describe('liveStatsFromMetrics', () => {
  it('renders dashes for every stat when metrics is null', () => {
    const stats = liveStatsFromMetrics(null)
    expect(stats).toHaveLength(3)
    for (const stat of stats) {
      expect(stat.value).toBe('—')
    }
  })

  it('shows processed / discovered as "X / Y" when both are available', () => {
    const stats = liveStatsFromMetrics(metrics({ filesProcessed: 42, filesDiscovered: 120 }))
    expect(stats[0]).toEqual({ label: 'Files inspected', value: '42 / 120' })
  })

  it('falls back to the single available file count when only one is present', () => {
    expect(liveStatsFromMetrics(metrics({ filesDiscovered: 120 }))[0].value).toBe('120')
    expect(liveStatsFromMetrics(metrics({ filesProcessed: 42 }))[0].value).toBe('42')
  })

  it('formats accumulated tokens with compact notation', () => {
    const stats = liveStatsFromMetrics(metrics({ tokensCounted: 1_500_000 }))
    expect(stats[1].label).toBe('Tokens counted')
    expect(stats[1].value).toBe('1.5M')
  })

  it('renders context windows with full notation', () => {
    expect(liveStatsFromMetrics(metrics({ contextWindows: 12 }))[2]).toEqual({
      label: 'Context windows',
      value: '12',
    })
  })
})

describe('loadingDetailFromJob', () => {
  const fallback = 'Counting tokens detail'

  it('uses the stage detail fallback when the job has no message', () => {
    expect(loadingDetailFromJob(snapshot({ message: null }), fallback).message).toBe(fallback)
    expect(loadingDetailFromJob(snapshot({ message: '   ' }), fallback).message).toBe(fallback)
    expect(loadingDetailFromJob(null, fallback).message).toBe(fallback)
  })

  it('uses the backend message as the main detail when present', () => {
    const detail = loadingDetailFromJob(snapshot({ message: 'Counting tokens in src/App.tsx' }), fallback)
    expect(detail.message).toBe('Counting tokens in src/App.tsx')
  })

  it('adds COUNTING_TOKENS microcopy only during the token-counting phase', () => {
    expect(loadingDetailFromJob(snapshot({ phase: 'COUNTING_TOKENS' }), fallback).microcopy).toBe(
      COUNTING_TOKENS_MICROCOPY,
    )
    expect(loadingDetailFromJob(snapshot({ phase: 'CLONING_REPOSITORY' }), fallback).microcopy).toBeNull()
    expect(loadingDetailFromJob(null, fallback).microcopy).toBeNull()
  })
})
