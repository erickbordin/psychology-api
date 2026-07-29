package com.psychologyapi.psychology_api.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.psychologyapi.psychology_api.model.Usuario;
import com.psychologyapi.psychology_api.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public void salvar(Usuario usuario){
        var senha = usuario.getSenhaUsuario();

        if(usuarioRepository.findByEmailUsuario(usuario.getEmailUsuario()).isPresent()){
            throw new RuntimeException("Já existe um usuário cadastrado com este e-mail.");
        }

        usuario.setSenhaUsuario(passwordEncoder.encode(senha));
        usuarioRepository.save(usuario);
    }

    public Optional obterPorEmail(String email){
        return usuarioRepository.findByEmailUsuario(email);
    }

}
