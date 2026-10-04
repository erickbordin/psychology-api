package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.psychologyapi.psychologyapi.model.EstadoAviso;
import com.psychologyapi.psychologyapi.model.ResumoDiario;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class ResumoDiarioRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ResumoDiarioRepository repository;

    private ResumoDiario resumo(Usuario usuario, LocalDate data) {
        ResumoDiario resumo = new ResumoDiario();
        resumo.setUsuario(usuario);
        resumo.setData(data);
        return resumo;
    }

    @Test
    @DisplayName("resumo persistido volta PENDENTE e com createdAt preenchido")
    void resumoNasceComEstadoPendenteECreatedAt() {
        Usuario usuario = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        ResumoDiario salvo = em.persistAndFlush(resumo(usuario, LocalDate.now()));
        em.clear();

        ResumoDiario lido = repository.findById(salvo.getId()).orElseThrow();

        assertThat(lido.getEstado()).isEqualTo(EstadoAviso.PENDENTE);
        assertThat(lido.getCreatedAt()).isNotNull();
        assertThat(lido.getTentativas()).isZero();
    }

    @Test
    @DisplayName("dois resumos do mesmo usuario e data violam o indice unico")
    void resumoDuplicadoViolaIndiceUnico() {
        Usuario usuario = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        LocalDate data = LocalDate.now();
        em.persistAndFlush(resumo(usuario, data));

        assertThatThrownBy(() -> em.persistAndFlush(resumo(usuario, data)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("uq_resumo_usuario_data");
    }
}
