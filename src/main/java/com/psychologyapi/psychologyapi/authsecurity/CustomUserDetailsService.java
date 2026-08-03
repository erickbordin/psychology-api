package com.psychologyapi.psychologyapi.authsecurity;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.service.UsuarioService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UsuarioService service;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = service.obterPorEmail(email).orElseThrow( () -> new UsernameNotFoundException("Email ou senha inválidos"));

        return User.builder()
            .username(usuario.getEmailUsuario())
            .password(usuario.getSenhaUsuario())
            .build();
    }

}
