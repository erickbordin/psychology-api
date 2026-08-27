package com.psychologyapi.psychologyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.psychologyapi.psychologyapi.exceptions.SessaoInvalidaException;
import com.psychologyapi.psychologyapi.model.RefreshToken;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setEmailUsuario("ana@teste.com");
        return usuario;
    }

    private void comExpiracaoDe(int dias) {
        ReflectionTestUtils.setField(refreshTokenService, "expiracaoDias", dias);
    }

    @Test
    @DisplayName("emite guardando o hash, nunca o token em claro")
    void emiteGuardandoOHash() {
        comExpiracaoDe(14);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshEmitido emitido = refreshTokenService.emitir(usuario());

        assertThat(emitido.tokenEmClaro()).isNotBlank();
        assertThat(emitido.registro().getHashToken())
                .isEqualTo(RefreshTokenService.hashDe(emitido.tokenEmClaro()))
                .hasSize(64)
                .isNotEqualTo(emitido.tokenEmClaro());
    }

    @Test
    @DisplayName("dois tokens emitidos nunca coincidem")
    void doisTokensNaoCoincidem() {
        comExpiracaoDe(14);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        String primeiro = refreshTokenService.emitir(usuario()).tokenEmClaro();
        String segundo = refreshTokenService.emitir(usuario()).tokenEmClaro();

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    @DisplayName("a expiracao respeita a janela configurada")
    void expiracaoRespeitaAJanela() {
        comExpiracaoDe(14);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshToken registro = refreshTokenService.emitir(usuario()).registro();

        assertThat(registro.getExpiresAt()).isAfter(java.time.LocalDateTime.now().plusDays(13));
        assertThat(registro.getExpiresAt()).isBefore(java.time.LocalDateTime.now().plusDays(15));
    }

    private RefreshToken registroVivo(Usuario dono, String tokenEmClaro) {
        RefreshToken registro = new RefreshToken();
        registro.setUsuario(dono);
        registro.setHashToken(RefreshTokenService.hashDe(tokenEmClaro));
        registro.setExpiresAt(LocalDateTime.now().plusDays(14));
        return registro;
    }

    @Test
    @DisplayName("rotacionar revoga o token apresentado e encadeia o proximo")
    void rotacionarRevogaEEncadeia() {
        comExpiracaoDe(14);
        Usuario dono = usuario();
        RefreshToken atual = registroVivo(dono, "token-atual");
        when(refreshTokenRepository.findByHashToken(RefreshTokenService.hashDe("token-atual")))
                .thenReturn(Optional.of(atual));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshEmitido proximo = refreshTokenService.rotacionar("token-atual");

        assertThat(atual.getRevokedAt()).isNotNull();
        assertThat(proximo.tokenEmClaro()).isNotEqualTo("token-atual");
        verify(refreshTokenRepository).save(atual);
    }

    @Test
    @DisplayName("token desconhecido nao renova")
    void tokenDesconhecidoNaoRenova() {
        when(refreshTokenRepository.findByHashToken(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotacionar("nao-existe"))
                .isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    @DisplayName("token expirado nao renova")
    void tokenExpiradoNaoRenova() {
        Usuario dono = usuario();
        RefreshToken vencido = registroVivo(dono, "token-vencido");
        vencido.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByHashToken(RefreshTokenService.hashDe("token-vencido")))
                .thenReturn(Optional.of(vencido));

        assertThatThrownBy(() -> refreshTokenService.rotacionar("token-vencido"))
                .isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    @DisplayName("token ja revogado apresentado de novo derruba a cadeia inteira do usuario")
    void reusoDerrubaACadeia() {
        Usuario dono = usuario();
        RefreshToken revogado = registroVivo(dono, "token-roubado");
        revogado.setRevokedAt(LocalDateTime.now().minusMinutes(5));
        RefreshToken vivo = registroVivo(dono, "token-legitimo");

        when(refreshTokenRepository.findByHashToken(RefreshTokenService.hashDe("token-roubado")))
                .thenReturn(Optional.of(revogado));
        when(refreshTokenRepository.findByUsuarioAndRevokedAtIsNull(dono)).thenReturn(List.of(vivo));

        assertThatThrownBy(() -> refreshTokenService.rotacionar("token-roubado"))
                .isInstanceOf(SessaoInvalidaException.class);

        assertThat(vivo.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).saveAll(List.of(vivo));
    }

    @Test
    @DisplayName("revogar sem cookie nao estoura")
    void revogarSemCookieNaoEstoura() {
        refreshTokenService.revogar(null);
        verify(refreshTokenRepository, org.mockito.Mockito.never()).save(any());
    }
}
