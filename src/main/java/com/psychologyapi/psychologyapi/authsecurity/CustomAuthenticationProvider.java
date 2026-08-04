package com.psychologyapi.psychologyapi.authsecurity;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.service.UsuarioService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider{

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String login = authentication.getName();
        String senhaDigitada = authentication.getCredentials().toString();
        Usuario usuarioEncontrado = usuarioService.obterPorEmail(login).orElseThrow( () -> userNotFound() );
        String senhaCriptografada = usuarioEncontrado.getSenhaUsuario();
        boolean senhasBatem = passwordEncoder.matches(senhaDigitada, senhaCriptografada);

        if(senhasBatem){
            return new CustomAuthentication(usuarioEncontrado);
        }

        throw userNotFound();

    }

    private static BadCredentialsException userNotFound(){
        return new BadCredentialsException("Usuario e/ou senha incorretos!");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.isAssignableFrom(UsernamePasswordAuthenticationToken.class);
    }
    
}