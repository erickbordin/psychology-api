package com.psychologyapi.psychologyapi.controller.dto.paciente;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.psychologyapi.psychologyapi.model.CanalAviso;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * Atualizacao PARCIAL do paciente. Duas regras, e elas se complementam:
 *
 * <ul>
 *   <li><b>Campo AUSENTE do corpo nao e tocado.</b> Garantia do
 *       {@code NullValuePropertyMappingStrategy.IGNORE} em
 *       {@code PacienteMapper.atualizarPacienteAPartirDoDto}. Sem ela — a
 *       divida I6, corrigida em 14/09/2026 — um PUT que so mudava o telefone
 *       apagava nome, e-mail, nascimento e valor da sessao.</li>
 *   <li><b>Campo enviado EM BRANCO ({@code ""}) limpa o que estava salvo.</b>
 *       Vale so para {@code email} e {@code telefone} (decisao do dono,
 *       revisao da Fatia 2). A conversao branco -&gt; nulo mora no
 *       {@code PacienteService}, no caminho de update, e NAO no mapper: e a
 *       estrategia IGNORE do mapper que a primeira regra protege, e mexer nela
 *       traria a I6 de volta.</li>
 * </ul>
 *
 * As duas convivem porque dizem coisas diferentes: ausente e "nao mexa",
 * branco e "apague". Nulo explicito no JSON ({@code {"email": null}}) cai na
 * primeira: e indistinguivel de ausente para o Jackson sem {@code JsonNullable},
 * e introduzir essa dependencia so para isso nao se paga.
 *
 * {@code valorSessao}, {@code dataNascimento} e {@code canalAviso} NAO tem
 * mecanismo de limpeza — nenhuma tela pede, e "" nao e valor valido para
 * nenhum dos tres.
 *
 * Os {@code @Size} abaixo so espelham o limite que ja existe na coluna do
 * banco, para que estourar o limite vire 400 na validacao, nao 500 na
 * constraint. Todos toleram {@code ""}, que e o que faz a limpeza chegar ao
 * service em vez de morrer num 400.
 *
 * <p><b>Sem {@code @EmailExigidoPeloCanal} aqui, de proposito.</b> A regra
 * condicional de spec 5.4 e de bean no {@link PacienteCadastroDTO}, onde ler so
 * o payload esta certo. Num PUT parcial, ler so o payload recusava
 * {@code {"canalAviso":"EMAIL"}} sozinho mesmo com e-mail ja salvo. Aqui ela
 * roda no {@code PacienteService}, sobre o estado efetivo.
 */
public record PacienteAtualizacaoDTO(
        @Size(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.") String nome,

        // @TelefoneBR conta digitos, nao policia mascara — @Size continua
        // porque muitos separadores podem estourar a coluna mesmo com os
        // digitos certos. Os dois aceitam "" (o validador trata isBlank como
        // valido), que e o que permite a limpeza por valor em branco.
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres.")
        @TelefoneBR
        @Schema(description = "Telefone do paciente. Ausente do corpo: não é alterado. "
                + "Enviado em branco (\"\"): limpa o telefone salvo.")
        String telefone,

        @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres.")
        @Email(message = "Informe um e-mail em formato válido.")
        @Schema(description = "E-mail do paciente. Ausente do corpo: não é alterado. "
                + "Enviado em branco (\"\"): limpa o e-mail salvo — recusado com 400 "
                + "quando o canal de aviso do paciente é EMAIL.")
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
