/**
 * Bounded context for asynchronous analysis jobs.
 *
 * <p>This package owns the value objects, enums and snapshot records that describe the lifecycle of
 * an {@code AnalysisJob}: a long-running pipeline whose progress is exposed via polling. Types in
 * this package are intentionally framework-agnostic — they MUST NOT depend on Spring, JPA or any
 * persistence/web concern. Adapters live in {@code infrastructure} and {@code application}.
 */
@org.springframework.lang.NonNullApi
package dev.diegobarrioh.tokenmeter.domain.job;
