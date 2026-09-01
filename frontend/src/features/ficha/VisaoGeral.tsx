import { useParams } from 'react-router-dom'

import { CartaoDeSecao } from '../../ui/Cartao'
import { IconeAnotacao, IconeCalendario, IconeRelogio, IconeSino } from '../../ui/icones'
import { dataHora } from '../../ui/data'
import { useAnotacoes, useConsultasDoPaciente, useLembretes } from './queries'

/**
 * A visao geral da ficha: quatro cartoes, um por sub-recurso, cada um mostrando
 * so as primeiras linhas e levando para a lista inteira. E a tela em que o
 * psicologo bate o olho antes da sessao comecar — "quando foi a ultima, o que
 * ficou pendente, o que eu anotei".
 *
 * As tres primeiras consultas ja chegam paginadas da API; o cartao de historico
 * reaproveita a MESMA pagina filtrando por REALIZADA, em vez de pedir de novo.
 */
export function VisaoGeral() {
  const { pacienteId = '' } = useParams()

  const consultas = useConsultasDoPaciente(pacienteId, 0)
  const anotacoes = useAnotacoes(pacienteId, 0)
  const lembretes = useLembretes(pacienteId, 0)

  const agendadas = (consultas.data?.content ?? []).filter((item) => item.status === 'AGENDADA')
  const realizadas = (consultas.data?.content ?? []).filter((item) => item.status === 'REALIZADA')
  const ultimaAnotacao = anotacoes.data?.content[0]
  const pendentes = (lembretes.data?.content ?? []).filter((item) => !item.concluido)

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <CartaoDeSecao titulo="Consultas" icone={<IconeCalendario tamanho={18} />} para="consultas">
        {agendadas.length === 0 ? (
          <p className="text-tinta-3">Nenhuma consulta agendada.</p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {agendadas.slice(0, 3).map((consulta) => (
              <li key={consulta.id} className="flex gap-2 tabular-nums">
                <span aria-hidden="true" className="text-tinta-3">
                  •
                </span>
                {dataHora(consulta.dtConsulta)}
              </li>
            ))}
          </ul>
        )}
      </CartaoDeSecao>

      <CartaoDeSecao titulo="Anotações" icone={<IconeAnotacao tamanho={18} />} para="anotacoes">
        {ultimaAnotacao ? (
          <p className="line-clamp-4 leading-relaxed">{ultimaAnotacao.conteudo}</p>
        ) : (
          <p className="text-tinta-3">Nenhuma sessão registrada ainda.</p>
        )}
      </CartaoDeSecao>

      {/*
        Lembrete e a unica coisa da ficha que pede acao do psicologo, e o unico
        cartao em tom de atencao. Se tudo fosse amarelo, nada seria.
      */}
      <CartaoDeSecao
        titulo="Lembretes"
        icone={<IconeSino tamanho={18} />}
        para="lembretes"
        verTodos="Ver todos"
        tom="atencao"
      >
        {pendentes.length === 0 ? (
          <p className="text-tinta-3">Nada pendente para este paciente.</p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {pendentes.slice(0, 3).map((lembrete) => (
              <li key={lembrete.id} className="flex gap-2">
                <span aria-hidden="true" className="text-atencao">
                  •
                </span>
                {lembrete.descricao}
              </li>
            ))}
          </ul>
        )}
      </CartaoDeSecao>

      {/*
        O historico aponta para a trilha de auditoria filtrada por este paciente,
        e nao para a lista de consultas: "o que aconteceu com este cadastro" e
        pergunta de auditoria, e a trilha e a unica fonte que nao se pode editar.
      */}
      <CartaoDeSecao
        titulo="Histórico"
        icone={<IconeRelogio tamanho={18} />}
        para={`/auditoria?entidade=Paciente&entidadeId=${pacienteId}`}
        verTodos="Ver histórico"
      >
        {realizadas.length === 0 ? (
          <p className="text-tinta-3">Nenhuma sessão realizada ainda.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {realizadas.slice(0, 3).map((consulta) => (
              <li key={consulta.id} className="flex items-start gap-2.5">
                <span
                  aria-hidden="true"
                  className="mt-1.5 inline-block size-2 shrink-0 rounded-full bg-acento-linha"
                />
                <span className="tabular-nums">{dataHora(consulta.dtConsulta)}</span>
              </li>
            ))}
          </ul>
        )}
      </CartaoDeSecao>
    </div>
  )
}
