package com.psychologyapi.psychologyapi.service;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psychologyapi.psychologyapi.controller.dto.ajustes.AjustesAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.ajustes.AjustesResponseDTO;
import com.psychologyapi.psychologyapi.exceptions.CampoInvalidoException;
import com.psychologyapi.psychologyapi.model.BlocoAviso;
import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.AcaoAuditoria;
import com.psychologyapi.psychologyapi.repository.UsuarioRepository;
import com.psychologyapi.psychologyapi.service.security.SecurityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AjustesService {

    public static final String ENTIDADE_AUDITADA = "Ajustes";

    private final SecurityService securityService;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public AjustesResponseDTO obter() {
        return resposta(securityService.obterUsuarioLogado());
    }

    @Transactional
    public AjustesResponseDTO atualizar(AjustesAtualizacaoDTO dto) {
        Usuario usuario = securityService.obterUsuarioLogado();

        String titulo = dto.tituloProfissional() == null || dto.tituloProfissional().isBlank()
                ? null
                : dto.tituloProfissional().trim();
        String nome = dto.nome().trim();
        if (nome.length() < 2 || nome.length() > 100) {
            throw new CampoInvalidoException("nome", "O nome deve ter entre 2 e 100 caracteres.");
        }
        validarTitulo(dto.tituloNoEmail(), titulo);
        validarExpediente(dto.expedienteInicio(), dto.expedienteFim());

        usuario.setNomeUsuario(nome);
        usuario.setValorPadraoSessao(dto.valorPadraoSessao());
        usuario.setAntecedencia(dto.antecedencia());
        usuario.setBlocosAviso(blocos(dto.blocosAviso()));
        usuario.setTituloNoEmail(dto.tituloNoEmail());
        usuario.setTituloProfissional(titulo);
        usuario.setResumoDiario(dto.resumoDiario());
        usuario.setExpedienteInicio(dto.expedienteInicio());
        usuario.setExpedienteFim(dto.expedienteFim());

        Usuario salvo = usuarioRepository.save(usuario);
        auditoriaService.registrar(salvo, AcaoAuditoria.ATUALIZACAO, ENTIDADE_AUDITADA, salvo.getId());
        return resposta(salvo);
    }

    private Set<BlocoAviso> blocos(Set<BlocoAviso> recebidos) {
        Set<BlocoAviso> blocos = new HashSet<>();
        if (recebidos != null) {
            recebidos.stream().filter(Objects::nonNull).forEach(blocos::add);
        }
        return blocos;
    }

    private void validarTitulo(boolean tituloNoEmail, String titulo) {
        if (tituloNoEmail && titulo == null) {
            throw new CampoInvalidoException("tituloProfissional",
                    "Informe o título profissional para incluí-lo no e-mail.");
        }
    }

    private void validarExpediente(Integer inicio, Integer fim) {
        if (inicio == null && fim == null) {
            return;
        }
        String faltando = inicio == null ? "expedienteInicio" : fim == null ? "expedienteFim" : null;
        if (faltando != null) {
            throw new CampoInvalidoException(faltando,
                    "Informe o início e o fim do expediente, ou nenhum dos dois.");
        }
        if (fim <= inicio) {
            throw new CampoInvalidoException("expedienteFim",
                    "O fim do expediente deve ser depois do início.");
        }
    }

    private AjustesResponseDTO resposta(Usuario usuario) {
        Set<BlocoAviso> blocos = usuario.getBlocosAviso() == null
                ? Set.of()
                : new HashSet<>(usuario.getBlocosAviso());
        return new AjustesResponseDTO(
                usuario.getNomeUsuario(),
                usuario.getEmailUsuario(),
                usuario.getValorPadraoSessao(),
                usuario.getAntecedencia(),
                blocos,
                usuario.isTituloNoEmail(),
                usuario.getTituloProfissional(),
                usuario.isResumoDiario(),
                usuario.getExpedienteInicio(),
                usuario.getExpedienteFim());
    }
}
