/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import type { AnalysisJobStatusResponse } from '../types/api'
import { PipelineTimeline } from './PipelineTimeline'

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

describe('PipelineTimeline', () => {
  afterEach(() => {
    cleanup()
  })

  it('QUEUED: shows queue position and running/maxConcurrency', () => {
    render(
      <PipelineTimeline
        job={snapshot({
          status: 'QUEUED',
          phase: 'QUEUED',
          queueState: { queuePosition: 3, runningCount: 2, maxConcurrency: 4 },
        })}
      />,
    )

    expect(screen.getByText(/Position 3/)).toBeInTheDocument()
    expect(screen.getByText(/2\/4/)).toBeInTheDocument()
  })

  it('COUNTING_TOKENS partial: marks earlier stages complete, active, later pending; shows job.message in active stage', () => {
    render(
      <PipelineTimeline
        job={snapshot({
          status: 'RUNNING',
          phase: 'COUNTING_TOKENS',
          phaseLabel: 'Counting tokens',
          message: 'Counting tokens · 120 / 230 files',
        })}
      />,
    )

    // Stage 3 (index 3) is COUNTING_TOKENS — stages 0-2 should be complete
    const completedMarkers = document.querySelectorAll('[data-stage-state="completed"]')
    const activeMarker = document.querySelector('[data-stage-state="active"]')
    const pendingMarkers = document.querySelectorAll('[data-stage-state="pending"]')

    expect(completedMarkers.length).toBeGreaterThanOrEqual(3)
    expect(activeMarker).toBeInTheDocument()
    expect(pendingMarkers.length).toBeGreaterThanOrEqual(1)

    // W1: dynamic job.message must be shown in active stage (not just phaseLabel)
    expect(screen.getByText(/Counting tokens · 120 \/ 230 files/)).toBeInTheDocument()
  })

  it('CALCULATING_COSTS: stage index 6 is active with correct label', () => {
    render(
      <PipelineTimeline
        job={snapshot({
          status: 'RUNNING',
          phase: 'CALCULATING_COSTS',
          phaseLabel: 'Calculating pricing models',
        })}
      />,
    )

    // The active stage should contain the label "Calculating pricing models"
    // (may have elapsed suffix like "— 42s" appended)
    expect(screen.getByText(/Calculating pricing models/)).toBeInTheDocument()
  })

  it('SUCCESS: all stages show completed state, no active marker', () => {
    render(
      <PipelineTimeline
        job={snapshot({
          status: 'SUCCESS',
          phase: 'COMPLETED',
          analysisId: 'analysis-xyz',
          progressPercent: 100,
        })}
      />,
    )

    const completedMarkers = document.querySelectorAll('[data-stage-state="completed"]')
    const activeMarkers = document.querySelectorAll('[data-stage-state="active"]')

    expect(completedMarkers).toHaveLength(8)
    expect(activeMarkers).toHaveLength(0)
  })

  it('FAILED at SAVING_REPORT: 7 completed, 1 failed, 0 pending (exact distribution)', () => {
    render(
      <PipelineTimeline
        job={snapshot({
          status: 'FAILED',
          phase: 'SAVING_REPORT',
          error: { code: 'ANALYSIS_FAILED', message: 'Something went wrong' },
        })}
      />,
    )

    // SAVING_REPORT is stage index 7 (last stage) → 7 stages before it are completed,
    // it is failed, and 0 stages come after it.
    const completedMarkers = document.querySelectorAll('[data-stage-state="completed"]')
    const failedMarkers = document.querySelectorAll('[data-stage-state="failed"]')
    const pendingMarkers = document.querySelectorAll('[data-stage-state="pending"]')

    expect(completedMarkers).toHaveLength(7)
    expect(failedMarkers).toHaveLength(1)
    expect(pendingMarkers).toHaveLength(0)
  })
})
