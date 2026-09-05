package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
        assertThat(existeColuna("pendencia", "deleted_at_pendencia")).isTrue();
        assertThat(existeColuna("anotacao", "deleted_at_anotacao")).isTrue();
    }

    @Test
    @DisplayName("coluna de exclusao logica e anulavel: registro nasce sem deletedAt")
    void colunasDeSoftDeleteSaoAnulaveis() {
        assertThat(colunaEAnulavel("paciente", "deleted_at_paciente")).isTrue();
        assertThat(colunaEAnulavel("consulta", "deleted_at_consulta")).isTrue();
        assertThat(colunaEAnulavel("pendencia", "deleted_at_pendencia")).isTrue();
    }

    @Test
    @DisplayName("a tabela chama-se pendencia; lembrete nao existe mais (spec 5.2)")
    void lembreteVirouPendencia() {
        assertThat(existeTabela("pendencia")).isTrue();
        assertThat(existeTabela("lembrete")).isFalse();
    }

    @Test
    @DisplayName("pendencia mantem exclusao logica anulavel")
    void pendenciaTemSoftDelete() {
        assertThat(existeColuna("pendencia", "deleted_at_pendencia")).isTrue();
        assertThat(colunaEAnulavel("pendencia", "deleted_at_pendencia")).isTrue();
    }

    @Test
    @DisplayName("paciente tem os campos de aviso e de valor da sessao (spec 5.4)")
    void pacienteTemCamposDeAvisoEValor() {
        assertThat(existeColuna("paciente", "valor_sessao_paciente")).isTrue();
        assertThat(existeColuna("paciente", "consentimento_aviso_em_paciente")).isTrue();
        assertThat(existeColuna("paciente", "canal_aviso_paciente")).isTrue();
        assertThat(existeColuna("paciente", "token_descadastro_paciente")).isTrue();
    }

    @Test
    @DisplayName("observacoes_paciente saiu do schema (spec 5.8, decisao do dono)")
    void observacoesPacienteNaoExisteMais() {
        assertThat(existeColuna("paciente", "observacoes_paciente")).isFalse();
    }

    @Test
    @DisplayName("consulta tem valor e pago_em (spec 5.3)")
    void consultaTemDinheiro() {
        assertThat(existeColuna("consulta", "valor_consulta")).isTrue();
        assertThat(existeColuna("consulta", "pago_em_consulta")).isTrue();
    }

    @Test
    @DisplayName("pago_em e anulavel: consulta nasce sem baixa")
    void pagoEmEAnulavel() {
        assertThat(colunaEAnulavel("consulta", "pago_em_consulta")).isTrue();
    }

    @Test
    @DisplayName("token de descadastro e unico: link de e-mail nao pode servir a dois pacientes")
    void tokenDescadastroEUnico() {
        Integer total = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name
                WHERE tc.table_name = 'paciente'
                  AND tc.constraint_type = 'UNIQUE'
                  AND ccu.column_name = 'token_descadastro_paciente'
                """, Integer.class);
        assertThat(total).isEqualTo(1);
    }

    @Test
    @DisplayName("anotacao aponta para a consulta, e o vinculo e opcional (spec 5.1)")
    void anotacaoLigaAConsulta() {
        assertThat(existeColuna("anotacao", "id_consulta")).isTrue();
        assertThat(colunaEAnulavel("anotacao", "id_consulta")).isTrue();
    }

    @Test
    @DisplayName("o vinculo anotacao->consulta e chave estrangeira de verdade")
    void anotacaoTemFkParaConsulta() {
        Integer total = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_name = 'anotacao'
                  AND constraint_type = 'FOREIGN KEY'
                  AND constraint_name = 'fk_anotacao_consulta'
                """, Integer.class);
        assertThat(total).isEqualTo(1);
    }

    @Test
    @DisplayName("aviso_de_consulta existe, presa a uma consulta (spec 5.2)")
    void avisoDeConsultaExiste() {
        assertThat(existeTabela("aviso_de_consulta")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "id_consulta")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "destinatario_aviso")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "canal_aviso")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "agendado_para_aviso")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "enviado_em_aviso")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "estado_aviso")).isTrue();
        assertThat(existeColuna("aviso_de_consulta", "erro_aviso")).isTrue();
    }

    @Test
    @DisplayName("o aviso NAO guarda corpo de mensagem (spec 5.2 e 7.1)")
    void avisoNaoTemCorpoDeMensagem() {
        List<String> colunas = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'aviso_de_consulta'",
                String.class);

        // a lista precisa ter conteudo de verdade: se uma migration futura
        // derrubar ou renomear a tabela por engano, este teste nao pode
        // voltar a passar por vacuidade (lista vazia)
        assertThat(colunas).isNotEmpty();

        // "assunto" e o pior caso: e o campo que aparece na tela de bloqueio
        // do celular do paciente, sem ele controlar. Cobre tambem variantes
        // em ingles, que passariam pela lista original
        assertThat(colunas)
                .as("corpo/conteudo guardado aqui deixa texto arbitrario chegar ao paciente")
                .noneMatch(c -> c.contains("corpo")
                        || c.contains("mensagem")
                        || c.contains("texto")
                        || c.contains("conteudo")
                        || c.contains("assunto")
                        || c.contains("detalhe")
                        || c.contains("nota")
                        || c.contains("body")
                        || c.contains("content")
                        || c.contains("subject"));
    }

    @Test
    @DisplayName("usuario tem as preferencias de aviso e de valor (spec 5.5)")
    void usuarioTemPreferencias() {
        assertThat(existeColuna("usuario", "valor_padrao_sessao_usuario")).isTrue();
        assertThat(existeColuna("usuario", "antecedencia_aviso_usuario")).isTrue();
        assertThat(existeColuna("usuario", "titulo_no_email_usuario")).isTrue();
    }

    @Test
    @DisplayName("os blocos do aviso ficam em tabela filha, nao em coluna com virgulas")
    void blocosDoAvisoEmTabelaFilha() {
        assertThat(existeTabela("usuario_bloco_aviso")).isTrue();
        assertThat(existeColuna("usuario_bloco_aviso", "bloco")).isTrue();
    }

    // --- C1: FK composta amarrando dono a paciente, e anotacao a consulta do
    // mesmo paciente (laudo da revisao do conjunto, 2026-09-05) ---

    private UUID inserirUsuario(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO usuario (id_usuario, nm_usuario, email_usuario, senha_usuario, created_at_usuario) "
                        + "VALUES (?, ?, ?, ?, now())",
                id, "Psicologo de Teste", email, "hash-irrelevante-para-este-teste");
        return id;
    }

    private UUID inserirPaciente(UUID idUsuario, String nome) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO paciente "
                        + "(id_paciente, id_usuario, nm_paciente, canal_aviso_paciente, token_descadastro_paciente, created_at_paciente) "
                        + "VALUES (?, ?, ?, 'NENHUM', ?, now())",
                id, idUsuario, nome, UUID.randomUUID().toString());
        return id;
    }

    private UUID inserirConsulta(UUID idUsuario, UUID idPaciente) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO consulta (id_consulta, id_usuario, id_paciente, data_hora_consulta, status_consulta, created_at_consulta) "
                        + "VALUES (?, ?, ?, now(), 'AGENDADA', now())",
                id, idUsuario, idPaciente);
        return id;
    }

    @Test
    @DisplayName("consulta nao pode ter id_usuario de um psicologo e id_paciente de paciente de outro (FK composta trava C1)")
    void consultaNaoAceitaDonoEPacienteDeUsuariosDiferentes() {
        UUID psicologoA = inserirUsuario("psicologo.a@teste.com");
        UUID psicologoB = inserirUsuario("psicologo.b@teste.com");
        UUID pacienteDeB = inserirPaciente(psicologoB, "Paciente de B");

        assertThatThrownBy(() -> inserirConsulta(psicologoA, pacienteDeB))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("anotacao nao pode apontar para consulta de outro paciente do mesmo psicologo (FK composta trava C1)")
    void anotacaoNaoAceitaConsultaDeOutroPaciente() {
        UUID psicologo = inserirUsuario("psicologo.c@teste.com");
        UUID marina = inserirPaciente(psicologo, "Marina");
        UUID rafael = inserirPaciente(psicologo, "Rafael");
        UUID consultaDeRafael = inserirConsulta(psicologo, rafael);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO anotacao (id_anotacao, id_usuario, id_paciente, id_consulta, anotacao, created_at_anotacao) "
                        + "VALUES (?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), psicologo, marina, consultaDeRafael,
                "anotacao da Marina apontando para a sessao do Rafael"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("anotacao avulsa (sem consulta) continua podendo ser gravada com a FK composta (MATCH SIMPLE nao checa coluna nula)")
    void anotacaoAvulsaContinuaValidaComFkComposta() {
        UUID psicologo = inserirUsuario("psicologo.d@teste.com");
        UUID paciente = inserirPaciente(psicologo, "Paciente Avulso");

        jdbcTemplate.update(
                "INSERT INTO anotacao (id_anotacao, id_usuario, id_paciente, id_consulta, anotacao, created_at_anotacao) "
                        + "VALUES (?, ?, ?, NULL, ?, now())",
                UUID.randomUUID(), psicologo, paciente, "liguei fora do horario");

        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM anotacao WHERE id_paciente = ? AND id_consulta IS NULL",
                Integer.class, paciente);
        assertThat(total).isEqualTo(1);
    }

    /**
     * Regressao pega na re-revisao de 2026-09-05: a FK composta
     * (id_consulta, id_paciente) -> consulta so e checada quando id_consulta
     * NAO e nulo (MATCH SIMPLE). Sem a FK simples de id_paciente ao lado
     * dela, uma anotacao avulsa (o caso normal, nao borda) apontando para um
     * paciente que nao existe seria aceita — o teste acima
     * ({@code anotacaoAvulsaContinuaValidaComFkComposta}) so prova que a
     * avulsa continua VALIDA (com paciente valido); este prova que ela
     * continua VALIDADA.
     */
    @Test
    @DisplayName("anotacao avulsa com id_paciente inexistente e recusada (FK simples ao lado da composta)")
    void anotacaoAvulsaComPacienteInexistenteERecusada() {
        UUID psicologo = inserirUsuario("psicologo.e@teste.com");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO anotacao (id_anotacao, id_usuario, id_paciente, id_consulta, anotacao, created_at_anotacao) "
                        + "VALUES (?, ?, ?, NULL, ?, now())",
                UUID.randomUUID(), psicologo, UUID.randomUUID(), "anotacao para paciente inexistente"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean existeTabela(String tabela) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, tabela);
        return total != null && total > 0;
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
