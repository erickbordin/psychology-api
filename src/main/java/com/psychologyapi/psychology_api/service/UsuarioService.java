package com.psychologyapi.psychology_api.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.psychologyapi.psychology_api.controller.dto.UsuarioCadastroDTO;
import com.psychologyapi.psychology_api.controller.dto.UsuarioResponseDTO;
import com.psychologyapi.psychology_api.exceptions.RegistroDuplicadoException;
import com.psychologyapi.psychology_api.mapper.UsuarioMapper;
import com.psychologyapi.psychology_api.model.Usuario;
import com.psychologyapi.psychology_api.repository.UsuarioRepository;

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
