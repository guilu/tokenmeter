package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class V9MigrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void languageStatsNameIndexExists() {
    // H2 in PostgreSQL mode exposes INFORMATION_SCHEMA.INDEXES
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES"
                + " WHERE TABLE_NAME = 'LANGUAGE_STATS'"
                + " AND INDEX_NAME = 'IDX_LANGUAGE_STATS_NAME'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }
}
