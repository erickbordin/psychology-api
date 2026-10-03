import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { deApi } from '../../api/data'
import { ErroDaApi } from '../../api/erro'
import { usePrivacidade } from '../../privacidade/usePrivacidade'
import { Botao } from '../../ui/Botao'
import { Chip } from '../../ui/Chip'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { emReais, mesPorExtenso, mesVizinho } from '../../ui/dinheiro'
import { DialogoDeValor } from './DialogoDeValor'
import {
  useDarBaixa,
  useDarBaixaEmLote,
  useDesfazerBaixa,
  useFinanceiroDoMes,
  usePainelDoMes,
  type Devedor,
  type SessaoFinanceira,
} from './queries'

const FALHA_GENERICA = 'Não foi possível completar a ação. Tente de novo.'

const mensagemDoErro = (erro: Error) => (erro instanceof ErroDaApi ? erro.mensagem : FALHA_GENERICA)

function Nome({ privacidade, children }: { privacidade: boolean; children: string }) {
  return <span data-borrado={privacidade ? 'true' : undefined}>{children}</span>
}

function Valor({ privacidade, children }: { privacidade: boolean; children: string }) {
  return <span data-borrado={privacidade ? 'true' : undefined}>{children}</span>
}

function Indicador({ rotulo, children }: { rotulo: string; children: ReactNode }) {
  return (
    <div className="indicador">
      <dt>{rotulo}</dt>
      <dd>{children}</dd>
    </div>
  )
}

const dataEHora = (iso: string) => {
  const data = deApi(iso)
  const dia = data.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
  const hora = data.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
  return `${dia} · ${hora}`
}

type Anuncio = { quantidade: number; nome: string }

export function FinanceiroPage() {
  const [{ ano, mes }, setMes] = useState(() => {
    const hoje = new Date()
    return { ano: hoje.getFullYear(), mes: hoje.getMonth() + 1 }
  })
  const [anuncio, setAnuncio] = useState<Anuncio | null>(null)
  const [editando, setEditando] = useState<SessaoFinanceira | null>(null)
  const { privacidade } = usePrivacidade()

  const financeiro = useFinanceiroDoMes(ano, mes)
  const painel = usePainelDoMes(ano, mes)
  const baixa = useDarBaixa()
  const desfazer = useDesfazerBaixa()
  const lote = useDarBaixaEmLote()

  const carregando = financeiro.isLoading || painel.isLoading
  const falhou = financeiro.isError || painel.isError

  const irPara = (passo: -1 | 1) => {
    setMes(mesVizinho(ano, mes, passo))
    setAnuncio(null)
  }

  const darBaixaEmLote = (devedor: Devedor) =>
    lote.mutate(devedor.consultaIds, {
      onSuccess: (r) => setAnuncio({ quantidade: r.consultasBaixadas, nome: devedor.pacienteNome }),
    })

  const dados = financeiro.data
  const resumo = painel.data

  return (
    <div className="tela">
      <header className="cabecalho-dia">
        <div>
          <h1>Financeiro</h1>
          <p className="sub">{mesPorExtenso(ano, mes)}</p>
        </div>
        <div className="acoes-cabecalho-dia">
          <Botao variante="secundario" className="mini" onClick={() => irPara(-1)}>
            Mês anterior
          </Botao>
          <Botao variante="secundario" className="mini" onClick={() => irPara(1)}>
            Próximo mês
          </Botao>
        </div>
      </header>

      <p className="sub" role="status">
        {carregando ? (
          'Carregando…'
        ) : anuncio ? (
          <>
            {anuncio.quantidade} {anuncio.quantidade === 1 ? 'sessão' : 'sessões'} de{' '}
            <Nome privacidade={privacidade}>{anuncio.nome}</Nome> com baixa.
          </>
        ) : null}
      </p>

      {falhou && (
        <p className="anuncio" role="alert">
          Não foi possível carregar o financeiro. Tente de novo.
        </p>
      )}

      {resumo && (
        <section aria-label="O mês">
          <h2 className="titulo-secao">O mês</h2>
          <dl className="indicadores cartao">
            <Indicador rotulo="Realizadas">{resumo.realizadas}</Indicador>
            <Indicador rotulo="Faltas">{resumo.faltas}</Indicador>
            <Indicador rotulo="Canceladas">{resumo.canceladas}</Indicador>
            <Indicador rotulo="Taxa de falta">
              {resumo.taxaDeFalta == null ? '—' : `${Math.round(resumo.taxaDeFalta * 100)}%`}
            </Indicador>
            <Indicador rotulo="Pacientes ativos">{resumo.pacientesAtivos}</Indicador>
          </dl>
        </section>
      )}

      {dados && (
        <>
          <section aria-label="Totais">
            <h2 className="titulo-secao">Totais</h2>
            <dl className="indicadores cartao">
              <Indicador rotulo="Faturado">
                <Valor privacidade={privacidade}>{emReais(dados.faturado)}</Valor>
              </Indicador>
              <Indicador rotulo="Recebido">
                <Valor privacidade={privacidade}>{emReais(dados.recebido)}</Valor>
              </Indicador>
              <Indicador rotulo="A receber">
                <Valor privacidade={privacidade}>{emReais(dados.aReceber)}</Valor>
              </Indicador>
            </dl>
            {dados.sessoesSemValor > 0 && (
              <p className="sub">
                {dados.sessoesSemValor} {dados.sessoesSemValor === 1 ? 'sessão sem valor definido' : 'sessões sem valor definido'}, fora dos totais
              </p>
            )}
          </section>

          <section aria-label="Quem está devendo">
            <h2 className="titulo-secao">Quem está devendo</h2>
            {dados.devendo.length === 0 ? (
              <EstadoVazio titulo="Ninguém devendo" apoio="Sessões realizadas sem baixa aparecem aqui." />
            ) : (
              <ul className="lista-sessoes cartao">
                {dados.devendo.map((d) => (
                  <li
                    className="linha-pendencia"
                    key={d.pacienteId}
                    aria-label={`Débito de ${d.pacienteNome}`}
                  >
                    <span>
                      <Nome privacidade={privacidade}>{d.pacienteNome}</Nome>
                      {d.pacienteExcluido && ' (paciente excluído)'} · {d.quantidadeSessoes}{' '}
                      {d.quantidadeSessoes === 1 ? 'sessão' : 'sessões'} ·{' '}
                      <Valor privacidade={privacidade}>{emReais(d.total)}</Valor>
                    </span>
                    <span className="acoes-linha">
                      <Botao
                        className="mini"
                        disabled={lote.isPending && lote.variables === d.consultaIds}
                        onClick={() => darBaixaEmLote(d)}
                      >
                        Dar baixa em todas ({d.quantidadeSessoes})
                      </Botao>
                      {!d.pacienteExcluido && (
                        <Link className="botao secundario mini" to={`/pacientes/${d.pacienteId}`}>
                          Abrir a ficha
                        </Link>
                      )}
                    </span>
                    {lote.isError && lote.variables === d.consultaIds && (
                      <p className="anuncio" role="alert">
                        {mensagemDoErro(lote.error)}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section aria-label="Sessões do mês">
            <h2 className="titulo-secao">Sessões do mês</h2>
            {dados.sessoes.length === 0 ? (
              <EstadoVazio titulo={`Nenhuma sessão realizada em ${mesPorExtenso(ano, mes)}.`} />
            ) : (
              <ul className="lista-sessoes cartao">
                {dados.sessoes.map((s) => {
                  const emVoo =
                    (baixa.isPending && baixa.variables === s.consultaId) ||
                    (desfazer.isPending && desfazer.variables === s.consultaId)
                  const erro =
                    baixa.isError && baixa.variables === s.consultaId
                      ? baixa.error
                      : desfazer.isError && desfazer.variables === s.consultaId
                        ? desfazer.error
                        : null
                  return (
                    <li
                      className="linha-pendencia"
                      key={s.consultaId}
                      aria-label={`Sessão de ${s.pacienteNome}`}
                    >
                      <span>
                        <span className="hora">{dataEHora(s.dtConsulta)}</span>{' '}
                        <Nome privacidade={privacidade}>{s.pacienteNome}</Nome>
                        {s.valor != null && (
                          <>
                            {' '}
                            · <Valor privacidade={privacidade}>{emReais(s.valor)}</Valor>
                          </>
                        )}
                      </span>
                      <span className="acoes-linha">
                        {s.valor == null ? (
                          <Chip>Sem valor definido</Chip>
                        ) : s.pagoEm ? (
                          <Chip variante="realizada">Pago</Chip>
                        ) : (
                          <Chip variante="alerta">A receber</Chip>
                        )}
                        {s.valor == null ? (
                          <Botao variante="secundario" className="mini" onClick={() => setEditando(s)}>
                            Definir valor
                          </Botao>
                        ) : s.pagoEm ? (
                          <Botao
                            variante="secundario"
                            className="mini"
                            disabled={emVoo}
                            onClick={() => desfazer.mutate(s.consultaId)}
                          >
                            Desfazer baixa
                          </Botao>
                        ) : (
                          <>
                            <Botao className="mini" disabled={emVoo} onClick={() => baixa.mutate(s.consultaId)}>
                              Dar baixa
                            </Botao>
                            <Botao variante="secundario" className="mini" onClick={() => setEditando(s)}>
                              Alterar valor
                            </Botao>
                          </>
                        )}
                      </span>
                      {erro && (
                        <p className="anuncio" role="alert">
                          {mensagemDoErro(erro)}
                        </p>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </section>
        </>
      )}

      {editando && <DialogoDeValor sessao={editando} aoFechar={() => setEditando(null)} />}
    </div>
  )
}
