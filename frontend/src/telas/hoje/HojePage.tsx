import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { deApi } from '../../api/data'
import { ErroDaApi } from '../../api/erro'
import { usePrivacidade } from '../../privacidade/usePrivacidade'
import { Botao } from '../../ui/Botao'
import { Cartao } from '../../ui/Cartao'
import { Chip } from '../../ui/Chip'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { reconciliar, type ItemDaFila } from './fila'
import { useMarcarStatus, usePainelDeHoje, type PendenciaDoPainel } from './queries'

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

  const { fila, resolvidas } = reconciliar(data.sessoes, data.pendencias)

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

      {fila.length === 0 && resolvidas.length === 0 ? (
        <EstadoVazio
          titulo="Nenhuma sessao hoje"
          acao={
            <Link className="botao secundario" to="/agenda">
              Ver a agenda
            </Link>
          }
        />
      ) : (
        <>
          {fila.length > 0 && (
            <section aria-label="Precisa de voce">
              <h2 className="titulo-secao">
                Precisa de voce
                <span className="contador-fila">{fila.length}</span>
              </h2>
              <ul className="lista-sessoes cartao">
                {fila.map((item) => (
                  <LinhaDaFila
                    key={item.consultaId}
                    item={item}
                    privacidade={privacidade}
                    aoMarcar={(status) => marcar.mutate({ id: item.consultaId, status })}
                    erro={
                      marcar.isError && marcar.variables?.id === item.consultaId
                        ? marcar.error
                        : null
                    }
                  />
                ))}
              </ul>
            </section>
          )}

          {resolvidas.length > 0 && (
            <section aria-label="Sessoes de hoje">
              <h2 className="titulo-secao">Sessoes de hoje</h2>
              <ul className="lista-resolvidas cartao">
                {resolvidas.map((s) => (
                  <li
                    className="linha-resolvida"
                    key={s.consultaId}
                    aria-label={`Sessao de ${s.pacienteNome}`}
                  >
                    <span className="hora">{hora(s.dtConsulta)}</span>
                    <Nome privacidade={privacidade}>{s.pacienteNome}</Nome>
                    <Chip variante={s.status === 'REALIZADA' ? 'realizada' : 'faltou'}>
                      {s.status}
                    </Chip>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
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

function LinhaDaFila({
  item,
  privacidade,
  aoMarcar,
  erro,
}: {
  item: ItemDaFila
  privacidade: boolean
  aoMarcar: (status: 'REALIZADA' | 'FALTOU') => void
  erro: Error | null
}) {
  const variante = item.status === 'REALIZADA' ? 'realizada' : item.status === 'FALTOU' ? 'faltou' : undefined

  return (
    <li className="linha-pendencia" aria-label={`Sessao de ${item.pacienteNome}`}>
      <span>
        <span className="hora">{hora(item.dtConsulta)}</span>{' '}
        <Nome privacidade={privacidade}>{item.pacienteNome}</Nome>
        {item.tipo && <span className="acao-pedida"> · {frasePendente(item.tipo)}</span>}
      </span>
      <span className="acoes-linha">
        {item.status && <Chip variante={variante}>{item.status}</Chip>}
        {/* spec 4(1): em um clique NA PROPRIA LINHA. Hoje exige abrir dialogo */}
        {item.status === 'AGENDADA' && (
          <>
            <Botao variante="secundario" className="mini" onClick={() => aoMarcar('REALIZADA')}>
              Realizada
            </Botao>
            <Botao variante="secundario" className="mini" onClick={() => aoMarcar('FALTOU')}>
              Faltou
            </Botao>
          </>
        )}
        {item.pacienteId && (
          <Link className="botao secundario mini" to={`/pacientes/${item.pacienteId}`}>
            Abrir a ficha
          </Link>
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
