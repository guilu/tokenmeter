# Archive Report: dynamic-pricing-fetch

Archived: 2026-05-16

## Outcome

- All 74 tasks completed (74/74 `[x]` in `tasks.md`).
- All 30 spec scenarios across `pricing`, `cost-estimation`, and `pricing-api` are covered by passing tests.
- `./gradlew check` green: 147 backend tests, 0 failures, 0 errors, 0 skipped; Spotless + Checkstyle clean.
- Frontend `npm run lint` clean (one pre-existing, unrelated warning) and `npm run build` succeeds.
- Recommended by `sdd-verify` with status `success` and recommendation `ARCHIVE`.

## Merged Specs

| Delta path | Main spec path |
|---|---|
| `openspec/changes/dynamic-pricing-fetch/specs/pricing/spec.md` | `openspec/specs/pricing/spec.md` |
| `openspec/changes/dynamic-pricing-fetch/specs/cost-estimation/spec.md` | `openspec/specs/cost-estimation/spec.md` |
| `openspec/changes/dynamic-pricing-fetch/specs/pricing-api/spec.md` | `openspec/specs/pricing-api/spec.md` |

Initial population — no existing scenarios overwritten. No destructive deltas (no removals or Flyway baseline changes) per `openspec/config.yaml` rules.archive.

## Commits

Gitmoji + Conventional Commits introduced by this change (oldest first, range `57e941c..HEAD`):

1. `6baab93` 📝 docs(sdd): scaffold dynamic-pricing-fetch change with exploration, proposal, specs, design, tasks
2. `e48f805` 🗃️ feat(pricing): V5 model_pricing snapshot table + config skeleton + Tier 1+2 catalogue
3. `b035248` ✨ feat(pricing): composite provider with OVERRIDE > REMOTE > FALLBACK precedence and cold-start seeding
4. `028f9c8` ✨ feat(pricing): LiteLLM client + transactional refresh service with scheduling and metrics
5. `bd2b223` ✨ feat(api): freshness metadata in /api/pricing and admin refresh endpoint
6. `f4f5a33` 💄 feat(frontend): pricing freshness banner, source pills, locale-aware relative time
7. `be0feee` 🧪 test(pricing): unit, slice, and integration coverage for the dynamic pricing pipeline
8. `27384df` 📝 docs: pricing pipeline architecture, env vars, runbook, no-go zones

## Open Follow-ups (not blocking archival)

- Verify Tier 2 LiteLLM mapping keys (`qwen3-coder`, `qwen3-max`, `grok-4`) against the live LiteLLM JSON on the first scheduled refresh — pricing.yaml carries `# TODO verify` markers acknowledged in design "Open Questions".
- Add a Vitest/RTL harness for `ModelsPage` to cover freshness banner and source-pill rendering (task 6.17 deferred; documented in `frontend/README.md`).
- Pre-existing ESLint warning in `frontend/src/App.tsx` (`react-hooks/exhaustive-deps` on `search`) — unrelated to this change, leave for a separate fix.
- Spec wording vs implementation nuance: the admin endpoint returns HTTP `503` (via `PricingExceptionHandler`) on upstream failure instead of `202` with `failed >= 1` in the body. Behaviour and test coverage are correct; the spec text could be tightened in a future delta.

## Rollback Reference

See `proposal.md` § "Rollback Plan" inside this archived folder and `docs/RUNBOOK.md` (added in commit `27384df`). Key levers: disable `tokenmeter.pricing.refresh.enabled`, drop `model_pricing` rows / revert V5 migration, and (if required) revert the eight commits listed above in reverse order.
