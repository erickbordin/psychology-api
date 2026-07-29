package com.psychologyapi.psychology_api.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import com.psychologyapi.psychology_api.model.Usuario;
import com.psychologyapi.psychology_api.service.UsuarioService;

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
