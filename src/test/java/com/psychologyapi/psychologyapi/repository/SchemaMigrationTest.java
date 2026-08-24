package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

/**
 * O container sobe vazio, entao o Flyway aplica db/migration do zero em cada
 * build. Se uma migration quebrar ou faltar, e aqui que aparece.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class SchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("todas as migrations aplicam limpas num banco vazio")
    void migrationsAplicamLimpas() {
        List<String> comFalha = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = false", String.class);

        assertThat(comFalha).isEmpty();
    }

    @Test
    @DisplayName("toda entidade com exclusao logica tem a coluna deleted_at correspondente no schema")
    void colunasDeSoftDeleteExistemNoSchema() {
        assertThat(existeColuna("paciente", "deleted_at_paciente")).isTrue();
        assertThat(existeColuna("consulta", "deleted_at_consulta")).isTrue();
        assertThat(existeColuna("lembrete", "deleted_at_lembrete")).isTrue();
        assertThat(existeColuna("anotacao", "deleted_at_anotacao")).isTrue();
    }

    @Test
    @DisplayName("coluna de exclusao logica e anulavel: registro nasce sem deletedAt")
    void colunasDeSoftDeleteSaoAnulaveis() {
        assertThat(colunaEAnulavel("paciente", "deleted_at_paciente")).isTrue();
        assertThat(colunaEAnulavel("consulta", "deleted_at_consulta")).isTrue();
        assertThat(colunaEAnulavel("lembrete", "deleted_at_lembrete")).isTrue();
    }

    private boolean existeColuna(String tabela, String coluna) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                Integer.class, tabela, coluna);
        return total != null && total > 0;
    }

    private boolean colunaEAnulavel(String tabela, String coluna) {
        String anulavel = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                String.class, tabela, coluna);
        return "YES".equals(anulavel);
    }
}
