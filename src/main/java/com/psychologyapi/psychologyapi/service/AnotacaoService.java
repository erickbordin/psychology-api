package com.psychologyapi.psychologyapi.service;

import com.psychologyapi.psychologyapi.controller.dto.AnotacaoCadastroDTO;
import com.psychologyapi.psychologyapi.controller.dto.AnotacaoResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.mapper.AnotacaoMapper;
import com.psychologyapi.psychologyapi.mapper.PacienteMapper;
import com.psychologyapi.psychologyapi.model.Anotacao;
import com.psychologyapi.psychologyapi.model.Paciente;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.repository.AnotacaoRepository;
import com.psychologyapi.psychologyapi.repository.PacienteRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnotacaoService {

    private final AnotacaoRepository anotacaoRepository;
    private final AnotacaoMapper anotacaoMapper;
    private final SecurityService securityService;
    private final PacienteService pacienteService;
    private final PacienteRepository pacienteRepository;

    public AnotacaoResponseDTO cadastrar(AnotacaoCadastroDTO dto){

    Usuario usuarioLogado = securityService.obterUsuarioLogado();
    Paciente paciente = pacienteRepository.findByIdAndUsuario(dto.pacienteId(), usuarioLogado)
            .orElseThrow( () -> new PacienteNaoEncontradoException("Paciente nao encontrado"));
    Anotacao anotacao = anotacaoMapper.toEntity(dto);
    anotacao.setPaciente(paciente);
    anotacao.setUsuario(usuarioLogado);
    anotacaoRepository.save(anotacao);
    return anotacaoMapper.toResponseDTO(anotacao);

   }

   public AnotacaoResponseDTO listarPorPaciente(){
        // TODO
   }

   public void deletar(){
       anotacaoRepository.delete()
   }

}
