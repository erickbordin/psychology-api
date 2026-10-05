package com.psychologyapi.psychologyapi.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.usuario.UsuarioCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.usuario.UsuarioResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.CadastroIndisponivelException;
import com.psychologyapi.psychologyapi.exceptions.RegistroDuplicadoException;
import com.psychologyapi.psychologyapi.exceptions.TermosDesatualizadosException;
import com.psychologyapi.psychologyapi.mapper.UsuarioMapper;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;
    private final TermosService termosService;
    private final AuditoriaService auditoriaService;
    private final ConviteService conviteService;
    private final Clock clock;

    @Transactional
    public UsuarioResponseDTO cadastrar(UsuarioCadastroDTO dto){

        conviteService.exigirDisponivel(dto.codigoConvite());

        String versaoAtual = termosService.atual()
                .orElseThrow(() -> new CadastroIndisponivelException("Cadastro ainda não está disponível."))
                .versao();
        if(!versaoAtual.equals(dto.versaoTermos())){
            throw new TermosDesatualizadosException("Os termos mudaram. Leia a versão atual.");
        }

        if(usuarioRepository.existsByEmailUsuario(dto.email())){
            throw new RegistroDuplicadoException("Já existe um usuário cadastrado com este e-mail.");
        }

        Usuario usuario = usuarioMapper.toEntity(dto);
        usuario.setSenhaUsuario(passwordEncoder.encode(dto.senha()));
        usuario.setTermosVersao(versaoAtual);
        usuario.setTermosAceitosEm(LocalDateTime.now(clock));

        Usuario usuarioSalvo;
        try {
            usuarioSalvo = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new RegistroDuplicadoException("Já existe um usuário cadastrado com este e-mail.");
        }
        conviteService.queimar(dto.codigoConvite(), usuarioSalvo);
        auditoriaService.registrar(usuarioSalvo, AcaoAuditoria.CRIACAO, "AceiteDosTermos", usuarioSalvo.getId());
        return usuarioMapper.toResponseDTO(usuarioSalvo);
    }

    public Optional<Usuario> obterPorEmail(String email){
        return usuarioRepository.findByEmailUsuario(email);
    }

}
