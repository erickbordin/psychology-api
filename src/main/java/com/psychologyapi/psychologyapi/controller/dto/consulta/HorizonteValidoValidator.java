package com.psychologyapi.psychologyapi.controller.dto.consulta;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.psychologyapi.psychologyapi.service.serie.OcorrenciasSemanais.MAX_OCORRENCIAS;
import static com.psychologyapi.psychologyapi.service.serie.OcorrenciasSemanais.MIN_OCORRENCIAS;

import com.psychologyapi.psychologyapi.service.serie.OcorrenciasSemanais;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * O horizonte da serie e um XOR entre dois campos, o que anotacao de campo nao
 * expressa, e o teto do dtLimite depende de dtPrimeiraConsulta. Por isso a
 * constraint e de classe.
 *
 * A violacao e anexada ao path de quantidadeSessoes de proposito: constraint de
 * classe sem addPropertyNode produz violacao global, e o GlobalExceptionsHandler
 * so le getFieldErrors() — a resposta 400 sairia com a lista "erros" vazia.
 */
public class HorizonteValidoValidator implements ConstraintValidator<HorizonteValido, ConsultaRecorrenteCadastroDTO> {

    private static final String CAMPO = "quantidadeSessoes";

    @Override
    public boolean isValid(ConsultaRecorrenteCadastroDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        Integer quantidade = dto.quantidadeSessoes();
        LocalDate limite = dto.dtLimite();

        if ((quantidade == null) == (limite == null)) {
            return erro(context, "informe exatamente um entre quantidadeSessoes e dtLimite");
        }

        if (quantidade != null) {
            if (quantidade < MIN_OCORRENCIAS || quantidade > MAX_OCORRENCIAS) {
                return erro(context, "quantidadeSessoes deve estar entre " + MIN_OCORRENCIAS + " e " + MAX_OCORRENCIAS);
            }
            return true;
        }

        LocalDateTime primeira = dto.dtPrimeiraConsulta();
        if (primeira == null) {
            return true; // o @NotNull do campo ja reporta esse caso
        }

        int ocorrencias = OcorrenciasSemanais.ate(primeira, limite, MAX_OCORRENCIAS).size();
        if (ocorrencias < MIN_OCORRENCIAS || ocorrencias > MAX_OCORRENCIAS) {
            return erro(context, "dtLimite deve caber entre " + MIN_OCORRENCIAS + " e " + MAX_OCORRENCIAS
                    + " sessoes semanais a partir de dtPrimeiraConsulta");
        }
        return true;
    }

    private boolean erro(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem)
                .addPropertyNode(CAMPO)
                .addConstraintViolation();
        return false;
    }
}
