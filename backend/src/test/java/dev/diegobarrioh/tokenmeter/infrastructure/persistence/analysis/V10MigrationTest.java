package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class V10MigrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void tokenizerIdColumnExists() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE TABLE_NAME = 'COST_ESTIMATES'"
                + " AND COLUMN_NAME = 'TOKENIZER_ID'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void tokenizationPrecisionColumnExists() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE TABLE_NAME = 'COST_ESTIMATES'"
                + " AND COLUMN_NAME = 'TOKENIZATION_PRECISION'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void newColumnsAreNullable() {
    // Both new columns must be nullable (no NOT NULL constraint) to support legacy rows.
    Integer notNullableCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE TABLE_NAME = 'COST_ESTIMATES'"
                + " AND COLUMN_NAME IN ('TOKENIZER_ID', 'TOKENIZATION_PRECISION')"
                + " AND IS_NULLABLE = 'NO'",
            Integer.class);
    assertThat(notNullableCount).isEqualTo(0);
  }
}
