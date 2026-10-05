package com.psychologyapi.psychologyapi.controller.common;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

import com.psychologyapi.psychologyapi.controller.dto.error.ErroCampoDTO;
import com.psychologyapi.psychologyapi.controller.dto.error.ErroRespostaDTO;
import com.psychologyapi.psychologyapi.exceptions.AnotacaoNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.CadastroIndisponivelException;
import com.psychologyapi.psychologyapi.exceptions.ConviteInvalidoException;
import com.psychologyapi.psychologyapi.exceptions.TermosDesatualizadosException;
import com.psychologyapi.psychologyapi.exceptions.ConflitoDeHorarioException;
import com.psychologyapi.psychologyapi.exceptions.BaixaInvalidaException;
import com.psychologyapi.psychologyapi.exceptions.ConsultaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.EmailExigidoPeloCanalException;
import com.psychologyapi.psychologyapi.exceptions.PendenciaNaoEncontradaException;
import com.psychologyapi.psychologyapi.exceptions.PacienteNaoEncontradoException;
import com.psychologyapi.psychologyapi.exceptions.FiltroAuditoriaInvalidoException;
import com.psychologyapi.psychologyapi.exceptions.CampoInvalidoException;
import com.psychologyapi.psychologyapi.exceptions.PeriodoInvalidoException;
import com.psychologyapi.psychologyapi.exceptions.RegistroDuplicadoException;
import com.psychologyapi.psychologyapi.exceptions.SessaoComAnotacaoException;
import com.psychologyapi.psychologyapi.exceptions.SessaoComBaixaException;
import com.psychologyapi.psychologyapi.exceptions.SessaoDeOutroPacienteException;
import com.psychologyapi.psychologyapi.exceptions.SessaoInvalidaException;
import com.psychologyapi.psychologyapi.exceptions.TransicaoDeStatusInvalidaException;

/**
 * Todo erro da API sai no mesmo envelope, o {@link ErroRespostaDTO}: status,
 * mensagem e a lista de campos culpados.
 *
 * O 400 preenche a lista com um item por campo. O 401, o 404 e o 409 nao tem
 * campo culpado — a lista vem vazia e a mensagem carrega o motivo. O cliente le
 * uma forma de erro, nao duas: com texto puro em uns status e JSON em outros,
 * tratar falha exigiria dois caminhos de parsing.
 *
 * O 401 da cadeia de seguranca (sem token, token expirado ou invalido) nao passa
 * por aqui — excecao lancada num filtro sobe antes do DispatcherServlet, fora do
 * alcance do @RestControllerAdvice. Quem monta o envelope naquele caso e o
 * ErroRespostaAuthenticationEntryPoint.
 */
@RestControllerAdvice
public class GlobalExceptionsHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErroRespostaDTO handleBadCredentialsException(BadCredentialsException e) {
        return semCampos(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(SessaoInvalidaException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErroRespostaDTO handleSessaoInvalida(SessaoInvalidaException e) {
        return semCampos(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(RegistroDuplicadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO handleRegistroDuplicadoException(RegistroDuplicadoException e) {
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getFieldErrors();
        List<ErroCampoDTO> listaErro = fieldErrors
                .stream()
                .map(fe -> new ErroCampoDTO(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return new ErroRespostaDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                listaErro);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleConstraintViolation(ConstraintViolationException e) {
        List<ErroCampoDTO> listaErro = e.getConstraintViolations()
                .stream()
                .map(v -> new ErroCampoDTO(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return new ErroRespostaDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                listaErro);
    }

    @ExceptionHandler(CadastroIndisponivelException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErroRespostaDTO handleCadastroIndisponivel(CadastroIndisponivelException e) {
        return semCampos(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(ConviteInvalidoException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErroRespostaDTO handleConviteInvalido(ConviteInvalidoException e) {
        return semCampos(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(TermosDesatualizadosException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO handleTermosDesatualizados(TermosDesatualizadosException e) {
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PacienteNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroRespostaDTO handlePacienteNaoEncontrado(PacienteNaoEncontradoException e){
        return semCampos(HttpStatus.NOT_FOUND, e.getMessage());
    }   

    @ExceptionHandler(ConsultaNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroRespostaDTO handleConsultaNaoEncontrada(ConsultaNaoEncontradaException e){
        return semCampos(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AnotacaoNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroRespostaDTO handleAnotacaoNaoEncontrada(AnotacaoNaoEncontradaException e){
        return semCampos(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PendenciaNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroRespostaDTO handlePendenciaNaoEncontrada(PendenciaNaoEncontradaException e){
        return semCampos(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflitoDeHorarioException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO handelConflitoDeHorario(ConflitoDeHorarioException e){
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * A transicao pedida esbarra no estado ATUAL do recurso — nao no corpo da
     * requisicao, entao nao e 400, e o recurso existe, entao nao e 404. 409 e
     * o mesmo status que ConflitoDeHorarioException e RegistroDuplicadoException
     * ja usam para "a requisicao conflita com o estado atual do recurso".
     */
    @ExceptionHandler(TransicaoDeStatusInvalidaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO handleTransicaoDeStatusInvalida(TransicaoDeStatusInvalidaException e){
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * A sessao apontada existe e e do psicologo logado, mas e de OUTRO
     * paciente (spec 5.1). Nao e 400 — o UUID enviado e valido e existe — nem
     * 404 — o recurso existe para este usuario. E o mesmo 409 de
     * TransicaoDeStatusInvalidaException: a requisicao conflita com o estado
     * atual do recurso.
     */
    @ExceptionHandler(SessaoDeOutroPacienteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO sessaoDeOutroPaciente(SessaoDeOutroPacienteException e) {
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * A sessao existe e e do psicologo logado, mas tem anotacao viva presa a
     * ela (decisao do dono, revisao da Fatia 2). Mesmo 409 dos demais conflitos
     * com o estado atual do recurso: nao e 400 (o id enviado e valido) nem 404
     * (a sessao existe para este usuario). A mensagem carrega a saida — apagar
     * a anotacao primeiro — porque recusar sem dizer o que fazer deixaria o
     * psicologo preso.
     */
    @ExceptionHandler(SessaoComAnotacaoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO sessaoComAnotacao(SessaoComAnotacaoException e) {
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BaixaInvalidaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO baixaInvalida(BaixaInvalidaException e) {
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(SessaoComBaixaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroRespostaDTO sessaoComBaixa(SessaoComBaixaException e) {
        return semCampos(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Spec 5.4: canal EMAIL exige e-mail. No CADASTRO a regra e de bean
     * ({@code @EmailExigidoPeloCanal}), porque o payload e o estado inteiro. Na
     * ATUALIZACAO ela nao pode ser de bean: o PUT e parcial, e um validador que
     * so ve o corpo recusava {@code {"canalAviso":"EMAIL"}} sozinho mesmo com o
     * e-mail ja salvo — 400 falso. A regra mudou para o service, sobre o estado
     * EFETIVO (payload sobre entidade), e chega aqui como excecao.
     *
     * Sai no MESMO formato de 400 que a validacao de bean produzia — campo
     * "email" em {@code erros[]}, mesma mensagem — para o cliente nao ter de
     * distinguir de onde veio a recusa.
     */
    @ExceptionHandler(EmailExigidoPeloCanalException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleEmailExigidoPeloCanal(EmailExigidoPeloCanalException e) {
        return new ErroRespostaDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                List.of(new ErroCampoDTO("email", e.getMessage())));
    }

    @ExceptionHandler(CampoInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleCampoInvalido(CampoInvalidoException e) {
        return new ErroRespostaDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                List.of(new ErroCampoDTO(e.getCampo(), e.getMessage())));
    }

    @ExceptionHandler(PeriodoInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handlePeriodoInvalido(PeriodoInvalidoException e) {
        return new ErroRespostaDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                List.of(new ErroCampoDTO(e.getCampo(), e.getMessage())));
    }

    /**
     * Reaproveita o mesmo formato de 400 da validacao de bean (ErroRespostaDTO):
     * entidadeId sem entidade nao e erro de bind, mas e erro de validacao do
     * mesmo jeito, e o contrato de 400 da API deve ser um so.
     */
    @ExceptionHandler(FiltroAuditoriaInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleFiltroAuditoriaInvalido(FiltroAuditoriaInvalidoException e) {
        return new ErroRespostaDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                List.of(new ErroCampoDTO("entidadeId", e.getMessage())));
    }

    /**
     * Rede de seguranca contra estouro de limite/constraint que a validacao de
     * bean nao pegou antes (DTO desatualizado em relacao ao banco, ou um campo
     * ainda sem @Size) — sem este handler o cliente leva 500 onde a 6.1 pede
     * 400 (I6 do laudo da revisao do conjunto, 2026-09-05).
     *
     * Nao tenta apontar o campo culpado: a mensagem do Postgres para
     * "value too long" nao carrega o nome da coluna, so o tipo/tamanho, entao
     * qualquer tentativa de adivinhar o campo aqui seria frágil. Apontar o
     * campo com precisao e trabalho do @Size no DTO (validado antes de chegar
     * aqui); este handler so evita que o que escapar disso vire 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        return semCampos(HttpStatus.BAD_REQUEST,
                "Não foi possível salvar: um dos valores informados é inválido ou excede o limite permitido.");
    }

    /**
     * Corpo que o Jackson nao consegue desserializar — JSON quebrado, ou um
     * valor de enum que nao existe (ex.: {"status":"BANANA"}) — estoura ANTES
     * da validacao de bean, e sem este handler cai no resolver padrao do
     * Spring: 400 com o corpo generico do Spring (timestamp/status/error/path),
     * sem "mensagem" nem "erros". Isso quebra a garantia de envelope unico
     * desta classe, e o front gera os tipos de erro a partir dela.
     *
     * Nao repassa e.getMessage(): a mensagem do Jackson cita nomes de classe,
     * de campo e os valores aceitos do enum — forma interna vazando para
     * qualquer chamador. Mensagem fixa, "erros" vazio como os demais 400 sem
     * campo culpado.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return semCampos(HttpStatus.BAD_REQUEST,
                "Não foi possível ler o corpo da requisição. Confira os campos enviados.");
    }

    /**
     * Query param ou path variable que o Spring nao consegue converter para o
     * tipo esperado — data mal formada, UUID invalido, enum inexistente — em
     * qualquer endpoint da API. Sem este handler cai no resolver padrao do
     * Spring: 400 com o corpo generico (timestamp/status/error/path), sem
     * "mensagem" nem "erros", quebrando a garantia de envelope unico desta
     * classe (mesmo defeito da HttpMessageNotReadableException acima, so que
     * do lado da leitura por parametro em vez do corpo).
     *
     * Este e um erro de CAMPO — o parametro tem nome, ao contrario dos casos
     * que usam semCampos — entao vai em "erros[]" com getName() como campo,
     * no mesmo formato que a validacao de bean usa.
     *
     * Mensagem fixa, sem e.getMessage() nem o valor recusado: a excecao expoe
     * o tipo Java de destino e o valor bruto enviado, forma interna que nao
     * deve vazar para o chamador, e o valor bruto numa API com dado de
     * paciente pode carregar qualquer coisa. Fixa tambem porque o handler
     * cobre qualquer parametro de qualquer tipo — uma mensagem citando "data"
     * seria errada para um UUID mal formado.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroRespostaDTO handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return new ErroRespostaDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                List.of(new ErroCampoDTO(e.getName(), "Confira o formato do parâmetro enviado.")));
    }

    /**
     * Erro sem campo culpado. A lista vazia — e nao ausente — mantem a forma do
     * envelope estavel: o cliente sempre encontra "erros" como array.
     */
    private static ErroRespostaDTO semCampos(HttpStatus status, String mensagem) {
        return new ErroRespostaDTO(status.value(), mensagem, List.of());
    }

}
