package com.psychologyapi.psychologyapi.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.psychologyapi.psychologyapi.controller.dto.usuario.UsuarioCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.usuario.UsuarioResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.RegistroDuplicadoException;
import com.psychologyapi.psychologyapi.mapper.UsuarioMapper;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;

    public UsuarioResponseDTO cadastrar(UsuarioCadastroDTO dto){

        if(usuarioRepository.existsByEmailUsuario(dto.email())){
            throw new RegistroDuplicadoException("\"Já existe um usuário cadastrado com este e-mail.\"");
        }

        Usuario usuario = usuarioMapper.toEntity(dto);
        usuario.setSenhaUsuario(passwordEncoder.encode(dto.senha()));

        Usuario usuarioSalvo = usuarioRepository.save(usuario);
        return usuarioMapper.toResponseDTO(usuarioSalvo);
    }

    public Optional<Usuario> obterPorEmail(String email){
        return usuarioRepository.findByEmailUsuario(email);
    }

}
