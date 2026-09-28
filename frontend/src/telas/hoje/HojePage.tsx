import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { deApi } from '../../api/data'
import { ErroDaApi } from '../../api/erro'
import { usePrivacidade } from '../../privacidade/usePrivacidade'
import { Botao } from '../../ui/Botao'
import { Cartao } from '../../ui/Cartao'
import { Chip } from '../../ui/Chip'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { useMarcarStatus, usePainelDeHoje, type PendenciaDoPainel, type SessaoDoDia } from './queries'

const hora = (iso: string) =>
  deApi(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

/**
 * A frase que cada especie de pendencia escreve. O painel cresce por fatia
 * (spec 9): SEM_STATUS veio na Fatia 1, SEM_ANOTACAO na 2, e a de baixa chega
 * na 3 — acrescentar sera acrescentar um caso aqui.
 *
 * A frase diz O QUE FAZER, nao o que falta: quem le esta entre dois
 * atendimentos e precisa agir, nao diagnosticar.
 */
function frasePendente(tipo: PendenciaDoPainel['tipo']): string {
  switch (tipo) {
    case 'SEM_STATUS':
      return 'marque o que aconteceu'
    case 'SEM_ANOTACAO':
      return 'registre a anotacao'
  }
}

/**
 * Fora do corpo de `HojePage` de proposito: um componente definido DENTRO de
 * outro componente ganha uma identidade nova a cada renderizacao (o React
 * compara por referencia de funcao), entao o React desmonta e remonta o
 * `<span>` toda vez que `privacidade` muda — o que, alem de desperdicio,
 * troca o no do DOM debaixo de qualquer referencia que o teste tenha
 * guardado antes do clique.
 */
function Nome({ privacidade, children }: { privacidade: boolean; children: string }) {
  return <span data-borrado={privacidade ? 'true' : undefined}>{children}</span>
}

export function HojePage() {
  const { data, isLoading, isError } = usePainelDeHoje()
  const marcar = useMarcarStatus()
  const { privacidade } = usePrivacidade()

  if (isLoading) return <p>Carregando o dia…</p>
  if (isError) return <p role="alert">Nao foi possivel carregar o dia. Tente de novo.</p>
  if (!data) return null

  return (
    <div className="tela">
      <header className="cabecalho-dia">
        <h1>Hoje</h1>
      </header>

      {/* spec 4(1): a tela fica aberta em tela cheia no consultorio, com
          paciente entrando. Um clique borra os nomes — a decisao de esconder
          dado clinico preservada sem matar a utilidade. `[data-borrado]` e'
          estilizado em estilo/estilo.css (Critico 1 da revisao do
          conjunto: a regra nao existia) */}
      {data.proxima ? (
        <section aria-label="Proxima sessao">
          <Cartao destaque>
            <p className="etiqueta">{hora(data.proxima.dtConsulta)}</p>
            <p className="nome-grande">
              <Nome privacidade={privacidade}>{data.proxima.pacienteNome}</Nome>
            </p>
            <QuantoFalta para={data.proxima.dtConsulta} />
            <Link className="botao" to={`/pacientes/${data.proxima.pacienteId}`}>
              Abrir a ficha
            </Link>
          </Cartao>
        </section>
      ) : null}

      <section aria-label="Sessoes de hoje">
        {data.sessoes.length === 0 ? (
          <EstadoVazio
            titulo="Nenhuma sessao hoje"
            acao={
              <Link className="botao secundario" to="/agenda">
                Ver a agenda
              </Link>
            }
          />
        ) : (
          <ul className="lista-sessoes cartao">
            {data.sessoes.map((s) => (
              <LinhaDaSessao
                key={s.consultaId}
                sessao={s}
                privacidade={privacidade}
                aoMarcar={(status) => marcar.mutate({ id: s.consultaId, status })}
                erro={
                  marcar.isError && marcar.variables?.id === s.consultaId
                    ? marcar.error
                    : null
                }
              />
            ))}
          </ul>
        )}
      </section>

      {data.pendencias.length > 0 && (
        <section aria-label="Pendencias">
          <ul className="faixa-pendencias cartao">
            {data.pendencias.map((p) => (
              <li className="linha-pendencia" key={p.consultaId}>
                <span>
                  <span className="hora">{hora(p.dtConsulta)}</span> ·{' '}
                  <Nome privacidade={privacidade}>{p.pacienteNome}</Nome> ·{' '}
                  {frasePendente(p.tipo)}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

/**
 * A spec 4(1) pede "quanto falta" no destaque. A API NAO devolve isso de
 * proposito — seria um numero que nasce velho. O cliente calcula de
 * `dtConsulta`, que e um fato, e recalcula de minuto em minuto enquanto a tela
 * esta aberta: ela fica horas aberta no consultorio, e um "faltam 20 minutos"
 * congelado e pior que nenhum.
 */
function QuantoFalta({ para }: { para: string }) {
  const [agora, setAgora] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setAgora(Date.now()), 60_000)
    return () => clearInterval(id)
  }, [])

  const minutos = Math.round((deApi(para).getTime() - agora) / 60_000)

  if (minutos < 0) return <p>Ja passou do horario.</p>
  if (minutos === 0) return <p>Agora.</p>
  if (minutos < 60) return <p>Em {minutos} min</p>

  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  return (
    <p>
      Em {horas}h{resto > 0 ? ` ${resto}min` : ''}
    </p>
  )
}

function LinhaDaSessao({
  sessao,
  privacidade,
  aoMarcar,
  erro,
}: {
  sessao: SessaoDoDia
  privacidade: boolean
  aoMarcar: (status: 'REALIZADA' | 'FALTOU') => void
  erro: Error | null
}) {
  const variante = sessao.status === 'REALIZADA' ? 'realizada' : sessao.status === 'FALTOU' ? 'faltou' : undefined

  return (
    <li className="linha-pendencia" aria-label={`Sessao de ${sessao.pacienteNome}`}>
      <span>
        <span className="hora">{hora(sessao.dtConsulta)}</span>{' '}
        <Nome privacidade={privacidade}>{sessao.pacienteNome}</Nome>
      </span>
      <span className="acoes-linha">
        <Chip variante={variante}>{sessao.status}</Chip>
        {/* spec 4(1): em um clique NA PROPRIA LINHA. Hoje exige abrir dialogo */}
        {sessao.status === 'AGENDADA' && (
          <>
            <Botao variante="secundario" className="mini" onClick={() => aoMarcar('REALIZADA')}>
              Realizada
            </Botao>
            <Botao variante="secundario" className="mini" onClick={() => aoMarcar('FALTOU')}>
              Faltou
            </Botao>
          </>
        )}
      </span>
      {/* spec 6.2: botao nunca morre em silencio. `useMarcarStatus` e a unica
          escrita da fatia que nao passa pelo `useFormularioDoProduto` — sem
          isto, um 409 (a API recusa marcar presenca numa sessao CANCELADA) ou
          um 401 fica mudo e o chip continua dizendo AGENDADA */}
      {erro && (
        <p className="anuncio" role="alert">
          {erro instanceof ErroDaApi
            ? erro.mensagem
            : 'Nao foi possivel completar a acao. Tente de novo.'}
        </p>
      )}
    </li>
  )
}
