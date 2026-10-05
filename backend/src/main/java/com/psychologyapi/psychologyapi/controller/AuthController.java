package com.psychologyapi.psychologyapi.controller;

import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.authsecurity.CookieRefreshToken;
import com.psychologyapi.psychologyapi.authsecurity.CustomAuthentication;
import com.psychologyapi.psychologyapi.controller.dto.TokenResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.usuario.UsuarioCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.usuario.UsuarioLoginDTO;
import com.psychologyapi.psychologyapi.controller.dto.usuario.UsuarioResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.CadastroIndisponivelException;
import com.psychologyapi.psychologyapi.exceptions.SessaoInvalidaException;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.service.RefreshEmitido;
import com.psychologyapi.psychologyapi.service.RefreshTokenService;
import com.psychologyapi.psychologyapi.service.TermosService;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.service.UsuarioService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UsuarioService usuarioService;
    private final RefreshTokenService refreshTokenService;
    private final CookieRefreshToken cookieRefreshToken;
    private final TermosService termosService;
    private final Validator validator;

    @PostMapping("/registrar")
    public ResponseEntity<UsuarioResponseDTO> registrarUsuario(@RequestBody UsuarioCadastroDTO dto){
        if (termosService.atual().isEmpty()) {
            throw new CadastroIndisponivelException("Cadastro ainda não está disponível.");
        }
        Set<ConstraintViolation<UsuarioCadastroDTO>> violacoes = validator.validate(dto);
        if (!violacoes.isEmpty()) {
            throw new ConstraintViolationException(violacoes);
        }
        UsuarioResponseDTO usuario = usuarioService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> logarUsuario(@RequestBody UsuarioLoginDTO dto){
        var authenticationToken = new UsernamePasswordAuthenticationToken(dto.emailUsuario(), dto.senha());
        var authenticate = (CustomAuthentication) authenticationManager.authenticate(authenticationToken);
        Usuario usuario = authenticate.getUsuario();

        refreshTokenService.limparExpiradosDe(usuario);
        RefreshEmitido emitido = refreshTokenService.emitir(usuario);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieRefreshToken.emitir(emitido.tokenEmClaro()).toString())
                .body(new TokenResponseDTO(tokenService.gerarToken(usuario.getEmailUsuario())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDTO> renovar(
            @CookieValue(name = CookieRefreshToken.NOME, required = false) String refreshToken){

        if (refreshToken == null) {
            throw new SessaoInvalidaException("Sessão inválida. Entre novamente.");
        }

        RefreshEmitido emitido = refreshTokenService.rotacionar(refreshToken);
        String acesso = tokenService.gerarToken(emitido.registro().getUsuario().getEmailUsuario());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieRefreshToken.emitir(emitido.tokenEmClaro()).toString())
                .body(new TokenResponseDTO(acesso));
    }

    /**
     * Depende do cookie, nao do access token: expirar o acesso nao pode impedir
     * alguem de encerrar a propria sessao. Idempotente — sem cookie, 204 igual.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> sair(
            @CookieValue(name = CookieRefreshToken.NOME, required = false) String refreshToken){

        refreshTokenService.revogar(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefreshToken.expirar().toString())
                .build();
    }

}
