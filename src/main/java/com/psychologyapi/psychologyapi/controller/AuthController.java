package com.psychologyapi.psychologyapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.authsecurity.CustomAuthentication;
import com.psychologyapi.psychologyapi.controller.dto.TokenResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.UsuarioCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.UsuarioLoginDTO;
import com.psychologyapi.psychologyapi.service.TokenService;
import com.psychologyapi.psychologyapi.service.UsuarioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UsuarioService usuarioService;


    @PostMapping("/registrar")
    public void registrarUsuario(@RequestBody @Valid UsuarioCadastroDTO dto){
        var usuario = usuarioService.cadastrar(dto);
        ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> logarUsuario(@RequestBody UsuarioLoginDTO dto){
        
        var authenticationToken = new UsernamePasswordAuthenticationToken(dto.emailUsuario(), dto.senha());
        var authenticate = (CustomAuthentication) authenticationManager.authenticate(authenticationToken);
        var token = tokenService.gerarToken(authenticate.getName());
        return ResponseEntity.ok(new TokenResponseDTO(token));


    }

}
