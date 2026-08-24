package com.psychologyapi.psychologyapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.support.Fixtures;
import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class AnotacaoRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private AnotacaoRepository anotacaoRepository;

    @Test
    @DisplayName("anotacao gera o id sozinha no insert, como as demais entidades")
    void anotacaoGeraIdAutomaticamente() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Anotacao anotacao = anotacaoRepository.save(Fixtures.anotacao(dono, paciente, "sessao 1"));

        assertThat(anotacao.getId()).isNotNull();
    }

    @Test
    @DisplayName("busca por id nao enxerga anotacao de outro psicologo")
    void naoRetornaAnotacaoDeOutroUsuario() {
        Usuario dono = em.persistAndFlush(Fixtures.usuario("dono@teste.com"));
        Usuario intruso = em.persistAndFlush(Fixtures.usuario("intruso@teste.com"));
        Paciente paciente = em.persistAndFlush(Fixtures.paciente(dono, "Ana"));

        Anotacao anotacao = Fixtures.anotacao(dono, paciente, "sessao 1");
        anotacao.setId(UUID.randomUUID()); // id na mao: a entidade nao tem @GeneratedValue
        em.persistAndFlush(anotacao);

        assertThat(anotacaoRepository.findByIdAndUsuarioAndDeletedAtIsNull(anotacao.getId(), dono)).isPresent();
        assertThat(anotacaoRepository.findByIdAndUsuarioAndDeletedAtIsNull(anotacao.getId(), intruso)).isEmpty();
    }
}
