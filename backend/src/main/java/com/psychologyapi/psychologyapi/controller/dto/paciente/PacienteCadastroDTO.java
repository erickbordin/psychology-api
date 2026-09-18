package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.psychologyapi.psychologyapi.model.CanalAviso;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

@EmailExigidoPeloCanal
public record PacienteCadastroDTO(
        @NotBlank(message = "Informe o nome do paciente.")
        @Size(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.")
        String nome,

        // formato brasileiro, com ou sem mascara: (51) 99811-2345, 51998112345,
        // +55 51 99811-2345. Opcional — quem nao tem telefone cadastra assim mesmo.
        // @TelefoneBR conta digitos, nao policia mascara — @Size continua
        // porque muitos separadores podem estourar a coluna mesmo com os
        // digitos certos
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres.")
        @TelefoneBR
        String telefone,

        // vira canal de aviso (spec 5.4): endereco invalido aqui e falha de
        // envio silenciosa depois
        @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres.")
        @Email(message = "Informe um e-mail em formato válido.")
        String email,

        @Past(message = "A data de nascimento deve estar no passado.")
        @IdadeMaxima(anos = 120, message = "Confira a data de nascimento.")
        LocalDate dataNascimento,

        // "> 0, 2 casas" (spec 6.3). @DecimalMin com inclusive=false e o
        // "maior que zero"; @Digits e as duas casas. Nao precisa de validador
        // proprio — ao contrario de @SenhaForte e @TelefoneBR, o Jakarta
        // expressa esta regra inteira.
        //
        // O @Schema repete as mesmas regras porque o springdoc NAO projeta nem
        // o inclusive=false do @DecimalMin nem o @Digits: sem ele o contrato
        // publicava {"type":"number","minimum":0} — zero aceito e casas
        // ilimitadas, os dois falsos (F1 da revisao da Fatia 2). O contrato
        // gerado e a unica rede automatica contra divergencia entre front e
        // API; um buraco nele nao aparece em teste nenhum ate doer no cliente.
        // A constraint e inclusiva em 0.01 de proposito, e nao exclusiva em 0:
        // com @Digits(fraction = 2) um centavo e o menor valor representavel,
        // entao os dois recusam exatamente o mesmo conjunto — mas so a forma
        // inclusiva chega ao contrato. O springdoc DERIVA minimum do
        // @DecimalMin e ignora o inclusive=false, sobrepondo ate um @Schema
        // explicito: com a forma exclusiva o contrato publicava minimum:0 e
        // passava a aceitar o zero que a API recusa (F1 da revisao da Fatia 2).
        // Fazer a propria constraint dizer 0.01 faz contrato e validacao
        // concordarem por construcao, sem depender de anotacao paralela.
        @DecimalMin(value = "0.01", message = "O valor da sessão deve ser maior que zero.")
        @Digits(integer = 8, fraction = 2, message = "O valor da sessão deve ter no máximo duas casas decimais.")
        @Schema(maximum = "99999999.99", multipleOf = 0.01,
                example = "180.00",
                description = "Valor padrão da sessão. Maior que zero, no máximo 8 dígitos "
                        + "inteiros e 2 casas decimais.")
        BigDecimal valorSessao,

        CanalAviso canalAviso) {
}
