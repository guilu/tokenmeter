package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

public interface LeaderboardJpaRepository extends Repository<AnalysisEntity, UUID> {

  @Query(
      value =
          """
          SELECT a.id,
                 a.repository_url,
                 a.owner_name,
                 a.repository_name,
                 a.created_at,
                 a.total_files,
                 a.total_lines,
                 a.total_bytes,
                 a.total_tokens,
                 CAST(1 AS BIGINT) AS analysis_count,
                 ce.provider,
                 ce.model,
                 ce.mode,
                 ce.total_cost,
                 a.pricing_snapshot_id,
                 a.pricing_primary_source,
                 a.pricing_captured_at
          FROM analysis a
          JOIN LATERAL (
              SELECT provider, model, mode, total_cost
              FROM cost_estimates
              WHERE analysis_id = a.id
                AND (:mode IS NULL OR mode = :mode)
                AND (:provider IS NULL OR provider = :provider)
                AND (:model IS NULL OR LOWER(model) = LOWER(:model))
              ORDER BY total_cost DESC
              LIMIT 1
          ) ce ON true
          ORDER BY ce.total_cost DESC, a.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<LeaderboardRow> findMostExpensive(
      @Nullable @Param("mode") String mode,
      @Nullable @Param("provider") String provider,
      @Nullable @Param("model") String model,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Query(
      value =
          """
          SELECT a.id,
                 a.repository_url,
                 a.owner_name,
                 a.repository_name,
                 a.created_at,
                 a.total_files,
                 a.total_lines,
                 a.total_bytes,
                 a.total_tokens,
                 CAST(1 AS BIGINT) AS analysis_count,
                 ce.provider,
                 ce.model,
                 ce.mode,
                 ce.total_cost,
                 a.pricing_snapshot_id,
                 a.pricing_primary_source,
                 a.pricing_captured_at
          FROM analysis a
          JOIN LATERAL (
              SELECT provider, model, mode, total_cost
              FROM cost_estimates
              WHERE analysis_id = a.id
                AND (:mode IS NULL OR mode = :mode)
                AND (:provider IS NULL OR provider = :provider)
                AND (:model IS NULL OR LOWER(model) = LOWER(:model))
              ORDER BY total_cost ASC
              LIMIT 1
          ) ce ON true
          ORDER BY ce.total_cost ASC, a.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<LeaderboardRow> findCheapest(
      @Nullable @Param("mode") String mode,
      @Nullable @Param("provider") String provider,
      @Nullable @Param("model") String model,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Query(
      value =
          """
          SELECT a.id,
                 a.repository_url,
                 a.owner_name,
                 a.repository_name,
                 a.created_at,
                 a.total_files,
                 a.total_lines,
                 a.total_bytes,
                 a.total_tokens,
                 CAST(1 AS BIGINT) AS analysis_count,
                 ce.provider,
                 ce.model,
                 ce.mode,
                 ce.total_cost,
                 a.pricing_snapshot_id,
                 a.pricing_primary_source,
                 a.pricing_captured_at
          FROM analysis a
          JOIN LATERAL (
              SELECT provider, model, mode, total_cost
              FROM cost_estimates
              WHERE analysis_id = a.id
                AND (:mode IS NULL OR mode = :mode)
                AND (:provider IS NULL OR provider = :provider)
                AND (:model IS NULL OR LOWER(model) = LOWER(:model))
              ORDER BY total_cost ASC
              LIMIT 1
          ) ce ON true
          WHERE a.total_tokens > 0
          ORDER BY (ce.total_cost * 1000000 / a.total_tokens) ASC, a.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<LeaderboardRow> findBestCostEfficiency(
      @Nullable @Param("mode") String mode,
      @Nullable @Param("provider") String provider,
      @Nullable @Param("model") String model,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Query(
      value =
          """
          WITH deduped AS (
              SELECT DISTINCT ON (repository_url)
                     id, repository_url, owner_name, repository_name, created_at,
                     total_files, total_lines, total_bytes, total_tokens,
                     pricing_snapshot_id, pricing_primary_source, pricing_captured_at
              FROM analysis
              ORDER BY repository_url, total_bytes DESC, created_at DESC
          )
          SELECT d.id,
                 d.repository_url,
                 d.owner_name,
                 d.repository_name,
                 d.created_at,
                 d.total_files,
                 d.total_lines,
                 d.total_bytes,
                 d.total_tokens,
                 CAST(1 AS BIGINT) AS analysis_count,
                 ce.provider,
                 ce.model,
                 ce.mode,
                 ce.total_cost,
                 d.pricing_snapshot_id,
                 d.pricing_primary_source,
                 d.pricing_captured_at
          FROM deduped d
          LEFT JOIN LATERAL (
              SELECT provider, model, mode, total_cost
              FROM cost_estimates
              WHERE analysis_id = d.id
                AND (:mode IS NULL OR mode = :mode)
                AND (:provider IS NULL OR provider = :provider)
                AND (:model IS NULL OR LOWER(model) = LOWER(:model))
              ORDER BY total_cost ASC
              LIMIT 1
          ) ce ON true
          ORDER BY d.total_bytes DESC, d.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<LeaderboardRow> findLargest(
      @Nullable @Param("mode") String mode,
      @Nullable @Param("provider") String provider,
      @Nullable @Param("model") String model,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Query(
      value =
          """
          WITH deduped AS (
              SELECT DISTINCT ON (repository_url)
                     id, repository_url, owner_name, repository_name, created_at,
                     total_files, total_lines, total_bytes, total_tokens,
                     pricing_snapshot_id, pricing_primary_source, pricing_captured_at
              FROM analysis
              ORDER BY repository_url, total_tokens DESC, created_at DESC
          )
          SELECT d.id,
                 d.repository_url,
                 d.owner_name,
                 d.repository_name,
                 d.created_at,
                 d.total_files,
                 d.total_lines,
                 d.total_bytes,
                 d.total_tokens,
                 CAST(1 AS BIGINT) AS analysis_count,
                 ce.provider,
                 ce.model,
                 ce.mode,
                 ce.total_cost,
                 d.pricing_snapshot_id,
                 d.pricing_primary_source,
                 d.pricing_captured_at
          FROM deduped d
          LEFT JOIN LATERAL (
              SELECT provider, model, mode, total_cost
              FROM cost_estimates
              WHERE analysis_id = d.id
                AND (:mode IS NULL OR mode = :mode)
                AND (:provider IS NULL OR provider = :provider)
                AND (:model IS NULL OR LOWER(model) = LOWER(:model))
              ORDER BY total_cost ASC
              LIMIT 1
          ) ce ON true
          ORDER BY d.total_tokens DESC, d.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<LeaderboardRow> findHighestTokenCount(
      @Nullable @Param("mode") String mode,
      @Nullable @Param("provider") String provider,
      @Nullable @Param("model") String model,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Query(
      value =
          """
          WITH ranked AS (
              SELECT id,
                     repository_url,
                     owner_name,
                     repository_name,
                     created_at,
                     total_files,
                     total_lines,
                     total_bytes,
                     total_tokens,
                     pricing_snapshot_id,
                     pricing_primary_source,
                     pricing_captured_at,
                     COUNT(*) OVER (PARTITION BY repository_url)          AS analysis_count,
                     ROW_NUMBER() OVER (PARTITION BY repository_url
                                        ORDER BY created_at DESC)         AS rn
              FROM analysis
          )
          SELECT r.id,
                 r.repository_url,
                 r.owner_name,
                 r.repository_name,
                 r.created_at,
                 r.total_files,
                 r.total_lines,
                 r.total_bytes,
                 r.total_tokens,
                 r.analysis_count,
                 ce.provider,
                 ce.model,
                 ce.mode,
                 ce.total_cost,
                 r.pricing_snapshot_id,
                 r.pricing_primary_source,
                 r.pricing_captured_at
          FROM ranked r
          LEFT JOIN LATERAL (
              SELECT provider, model, mode, total_cost
              FROM cost_estimates
              WHERE analysis_id = r.id
              ORDER BY total_cost ASC
              LIMIT 1
          ) ce ON true
          WHERE r.rn = 1
          ORDER BY r.analysis_count DESC, r.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<LeaderboardRow> findMostAnalyzed(@Param("limit") int limit, @Param("offset") long offset);

  @Query(
      value =
          """
          SELECT COUNT(DISTINCT a.id)
          FROM analysis a
          WHERE EXISTS (
              SELECT 1
              FROM cost_estimates ce
              WHERE ce.analysis_id = a.id
                AND (:mode IS NULL OR ce.mode = :mode)
                AND (:provider IS NULL OR ce.provider = :provider)
                AND (:model IS NULL OR LOWER(ce.model) = LOWER(:model))
          )
          """,
      nativeQuery = true)
  long countCostFiltered(
      @Nullable @Param("mode") String mode,
      @Nullable @Param("provider") String provider,
      @Nullable @Param("model") String model);

  @Query(value = "SELECT COUNT(*) FROM analysis", nativeQuery = true)
  long countAll();

  @Query(value = "SELECT COUNT(DISTINCT repository_url) FROM analysis", nativeQuery = true)
  long countDistinctRepositories();
}
