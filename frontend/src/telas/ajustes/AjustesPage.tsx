import { useMemo, useRef, useState } from 'react'

import { useFormularioDoProduto } from '../../formulario/useFormularioDoProduto'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { DialogoDeSenha } from './DialogoDeSenha'
import { TrilhaDeAcesso } from './TrilhaDeAcesso'
import { esquemaDosAjustes, type DadosDosAjustes } from './esquema'
import { montarPrevia } from './previa'
import { useAjustes, useSalvarAjustes, type Ajustes } from './queries'

const CAMPOS_NA_TELA = [
  'nome',
  'valorPadraoSessao',
  'antecedencia',
  'tituloProfissional',
  'expedienteInicio',
  'expedienteFim',
]

const ANTECEDENCIAS = [
  { valor: 'H24', rotulo: '24 horas antes' },
  { valor: 'H2', rotulo: '2 horas antes' },
  { valor: 'VESPERA', rotulo: 'Na véspera, às 18h' },
] as const

const FRASES = [
  { valor: 'REMARCAR', texto: 'Se precisar remarcar, é só responder este e-mail.' },
  { valor: 'CHEGADA', texto: 'Chegue com alguns minutos de antecedência.' },
  { valor: 'DURACAO', texto: 'Nosso encontro dura 50 minutos.' },
] as const

const horaCheia = (h: number) => `${String(h).padStart(2, '0')}:00`

const HORAS_DE_INICIO = Array.from({ length: 24 }, (_, i) => i)
const HORAS_DE_FIM = Array.from({ length: 24 }, (_, i) => i + 1)

function amanhaAsQuatorze() {
  const quando = new Date()
  quando.setDate(quando.getDate() + 1)
  quando.setHours(14, 0, 0, 0)
  return quando
}

function valoresDoFormulario(ajustes: Ajustes): DadosDosAjustes {
  return {
    nome: ajustes.nome,
    valorPadraoSessao: ajustes.valorPadraoSessao != null ? String(ajustes.valorPadraoSessao) : '',
    antecedencia: ajustes.antecedencia,
    blocosAviso: ajustes.blocosAviso,
    tituloNoEmail: ajustes.tituloNoEmail,
    tituloProfissional: ajustes.tituloProfissional ?? '',
    resumoDiario: ajustes.resumoDiario,
    expedienteInicio: ajustes.expedienteInicio != null ? String(ajustes.expedienteInicio) : '',
    expedienteFim: ajustes.expedienteFim != null ? String(ajustes.expedienteFim) : '',
  }
}

function FormularioDeAjustes({ ajustes }: { ajustes: Ajustes }) {
  const salvar = useSalvarAjustes()
  const amanha = useMemo(amanhaAsQuatorze, [])
  const [senhaAberta, setSenhaAberta] = useState(false)
  const botaoDeSenha = useRef<HTMLButtonElement>(null)
  const [senhaTrocada, setSenhaTrocada] = useState(false)

  const f = useFormularioDoProduto(
    esquemaDosAjustes,
    async (valores: DadosDosAjustes) => {
      salvar.reset()
      setSenhaTrocada(false)
      const salvo = await salvar.mutateAsync(valores)
      f.reset(valoresDoFormulario(salvo))
    },
    { defaultValues: valoresDoFormulario(ajustes) },
  )

  const atuais = f.watch()
  const previa = montarPrevia(
    {
      nome: atuais.nome ?? '',
      blocosAviso: atuais.blocosAviso ?? [],
      tituloNoEmail: Boolean(atuais.tituloNoEmail),
      tituloProfissional: atuais.tituloProfissional ?? '',
    },
    { primeiroNome: 'Ana', quando: amanha },
  )

  const erroTitulo = f.formState.errors.tituloProfissional?.message
  const erroAntecedencia = f.formState.errors.antecedencia?.message
  const erroInicio = f.formState.errors.expedienteInicio?.message
  const erroFim = f.formState.errors.expedienteFim?.message
  const alertaGeral =
    f.erroDeEnvio !== null &&
    (f.erroDeEnvio.erros.length === 0 ||
      f.erroDeEnvio.erros.some((e) => !CAMPOS_NA_TELA.includes(e.campo)))

  return (
    <>
      <form className="ajustes" onSubmit={f.handleSubmit(f.enviar)} noValidate>
        <section aria-label="Perfil" className="cartao ajustes-secao">
          <h2>Perfil</h2>
          <Campo
            rotulo="Nome"
            obrigatorio
            erro={f.formState.errors.nome?.message}
            {...f.register('nome')}
          />
          <div className="campo">
            <label className="campo-rotulo" htmlFor="ajustes-email">
              E-mail
            </label>
            <input id="ajustes-email" type="email" value={ajustes.email} readOnly />
          </div>
          <Botao ref={botaoDeSenha} type="button" variante="secundario" onClick={() => setSenhaAberta(true)}>
            Trocar senha
          </Botao>
        </section>

        <section aria-label="Sessão" className="cartao ajustes-secao">
          <h2>Sessão</h2>
          <Campo
            rotulo="Valor padrão da sessão"
            type="number"
            step="0.01"
            erro={f.formState.errors.valorPadraoSessao?.message}
            {...f.register('valorPadraoSessao')}
          />
          <p className="sub">Usado quando o paciente não tem valor próprio.</p>
        </section>

        <section aria-label="Expediente" className="cartao ajustes-secao">
          <h2>Expediente</h2>
          <div className="campo">
            <label className="campo-rotulo" htmlFor="ajustes-expediente-inicio">
              Início
            </label>
            <select
              id="ajustes-expediente-inicio"
              aria-invalid={erroInicio ? true : undefined}
              aria-describedby={erroInicio ? 'ajustes-expediente-inicio-erro' : undefined}
              {...f.register('expedienteInicio')}
            >
              <option value="">Sem expediente</option>
              {HORAS_DE_INICIO.map((h) => (
                <option key={h} value={String(h)}>
                  {horaCheia(h)}
                </option>
              ))}
            </select>
            {erroInicio && (
              <p className="campo-erro" id="ajustes-expediente-inicio-erro">
                {erroInicio}
              </p>
            )}
          </div>
          <div className="campo">
            <label className="campo-rotulo" htmlFor="ajustes-expediente-fim">
              Fim
            </label>
            <select
              id="ajustes-expediente-fim"
              aria-invalid={erroFim ? true : undefined}
              aria-describedby={erroFim ? 'ajustes-expediente-fim-erro' : undefined}
              {...f.register('expedienteFim')}
            >
              <option value="">Sem expediente</option>
              {HORAS_DE_FIM.map((h) => (
                <option key={h} value={String(h)}>
                  {horaCheia(h)}
                </option>
              ))}
            </select>
            {erroFim && (
              <p className="campo-erro" id="ajustes-expediente-fim-erro">
                {erroFim}
              </p>
            )}
          </div>
          <p className="sub">A agenda mostra só estas horas. Sem expediente, mostra das 07h às 19h.</p>
        </section>

        <section aria-label="Aviso de consulta" className="cartao ajustes-secao">
          <h2>Aviso de consulta</h2>
          <div className="campo">
            <label className="campo-rotulo" htmlFor="ajustes-antecedencia">
              Antecedência
            </label>
            <select
              id="ajustes-antecedencia"
              aria-invalid={erroAntecedencia ? true : undefined}
              aria-describedby={erroAntecedencia ? 'ajustes-antecedencia-erro' : undefined}
              {...f.register('antecedencia')}
            >
              {ANTECEDENCIAS.map((a) => (
                <option key={a.valor} value={a.valor}>
                  {a.rotulo}
                </option>
              ))}
            </select>
            {erroAntecedencia && (
              <p className="campo-erro" id="ajustes-antecedencia-erro">
                {erroAntecedencia}
              </p>
            )}
          </div>

          <div className="ajustes-frases">
            <p>Olá, {'{paciente}'}.</p>
            <p>Você tem um horário marcado para {'{data}'}, às {'{hora}'}.</p>
            <p className="sub">Estas duas frases sempre aparecem.</p>
          </div>

          <div className="ajustes-caixas">
            {FRASES.map((frase) => (
              <label key={frase.valor} className="ajustes-caixa">
                <input type="checkbox" value={frase.valor} {...f.register('blocosAviso')} />
                {frase.texto}
              </label>
            ))}
            <label className="ajustes-caixa">
              <input type="checkbox" {...f.register('tituloNoEmail')} />
              Incluir meu título profissional no e-mail
            </label>
          </div>

          <div className="campo">
            <label className="campo-rotulo" htmlFor="ajustes-titulo">
              Título profissional
            </label>
            <input
              id="ajustes-titulo"
              disabled={!atuais.tituloNoEmail}
              aria-invalid={erroTitulo ? true : undefined}
              aria-describedby={erroTitulo ? 'ajustes-titulo-erro' : undefined}
              {...f.register('tituloProfissional')}
            />
            {erroTitulo && (
              <p className="campo-erro" id="ajustes-titulo-erro">
                {erroTitulo}
              </p>
            )}
          </div>

          <label className="ajustes-caixa">
            <input type="checkbox" {...f.register('resumoDiario')} />
            Receber às 18h o resumo da agenda de amanhã
          </label>
        </section>

        <section aria-label="Como o aviso chega" className="cartao ajustes-secao">
          <h2>Como o aviso chega</h2>
          <dl className="ajustes-previa-cabecalho">
            <dt>De</dt>
            <dd>{previa.de}</dd>
            <dt>Assunto</dt>
            <dd>{previa.assunto}</dd>
          </dl>
          <p className="ajustes-previa-corpo">{previa.corpo}</p>
        </section>

        {alertaGeral && f.erroDeEnvio && (
          <p className="anuncio" role="alert">
            {f.erroDeEnvio.mensagem}
          </p>
        )}
        <p className="sub" role="status">
          {senhaTrocada
            ? 'Senha trocada. As outras sessões foram encerradas.'
            : salvar.isSuccess
              ? 'Ajustes salvos.'
              : ''}
        </p>

        <Botao type="submit" disabled={f.enviando}>
          Salvar ajustes
        </Botao>
      </form>
      <TrilhaDeAcesso />
      <DialogoDeSenha
        aberto={senhaAberta}
        aoFechar={() => setSenhaAberta(false)}
        focoAoFechar={botaoDeSenha}
        aoTrocar={() => {
          salvar.reset()
          setSenhaTrocada(true)
        }}
      />
    </>
  )
}

export function AjustesPage() {
  const ajustes = useAjustes()

  return (
    <div className="tela">
      <h1>Ajustes</h1>
      {ajustes.isPending && <p>Carregando…</p>}
      {ajustes.isError && (
        <p className="anuncio" role="alert">
          Não foi possível carregar. Tente de novo em instantes.
        </p>
      )}
      {ajustes.data && <FormularioDeAjustes ajustes={ajustes.data} />}
    </div>
  )
}
