import { useState } from 'react'

import { ErroApi } from '../../api/erro'
import type { Consulta, StatusConsulta } from '../../api/tipos'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { Dialogo } from '../../ui/Dialogo'
import { Selecao } from '../../ui/Selecao'
import { paraLocalDateTime, separarDataEHora } from '../../ui/data'
import { useAtualizarConsulta, useCancelarSerie, useExcluirConsulta } from './queries'

const STATUS: StatusConsulta[] = ['AGENDADA', 'REALIZADA', 'CANCELADA', 'FALTOU']

type Passo = 'edicao' | 'exclusao' | 'serie'

export function ConsultaDialogo({
  consulta,
  aoFechar,
}: {
  consulta: Consulta
  aoFechar: () => void
}) {
  const inicial = separarDataEHora(consulta.dtConsulta)
  const atualizar = useAtualizarConsulta()
  const excluir = useExcluirConsulta()
  const cancelarSerie = useCancelarSerie()

  const [data, setData] = useState(inicial.data)
  const [hora, setHora] = useState(inicial.hora)
  const [status, setStatus] = useState<StatusConsulta>(consulta.status)
  const [passo, setPasso] = useState<Passo>('edicao')

  const falha = [atualizar.error, excluir.error, cancelarSerie.error].find(
    (erro): erro is ErroApi => erro instanceof ErroApi,
  )

  async function executar(acao: () => Promise<unknown>) {
    try {
      await acao()
      aoFechar()
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  const titulo =
    passo === 'exclusao'
      ? 'Excluir esta consulta?'
      : passo === 'serie'
        ? 'Cancelar a série inteira?'
        : `Consulta de ${consulta.pacienteNome}`

  return (
    <Dialogo
      aberto
      aoFechar={aoFechar}
      titulo={titulo}
      descricao={
        passo === 'serie'
          ? 'Todas as sessões futuras desta série são removidas de uma vez. As que já aconteceram ficam.'
          : undefined
      }
    >
      {passo === 'edicao' ? (
        <>
          <Campo rotulo="Data" tipo="date" valor={data} aoMudar={setData} />
          <Campo rotulo="Hora" tipo="time" valor={hora} aoMudar={setHora} />
          <Selecao
            rotulo="Status"
            valor={status}
            aoMudar={(valor) => setStatus(valor as StatusConsulta)}
            opcoes={STATUS.map((valor) => ({ valor, rotulo: valor.toLowerCase() }))}
          />

          <div className="flex flex-wrap items-center gap-6">
            <Botao
              disabled={atualizar.isPending}
              onClick={() =>
                void executar(() =>
                  atualizar.mutateAsync({
                    id: consulta.id,
                    mudanca: { dtConsulta: paraLocalDateTime(data, hora), status },
                  }),
                )
              }
            >
              Salvar
            </Botao>
            <Botao variante="texto" onClick={aoFechar}>
              Cancelar
            </Botao>
            <span className="flex-1" />
            <Botao variante="texto" onClick={() => setPasso('exclusao')}>
              Excluir
            </Botao>
          </div>

          {consulta.serieId ? (
            <div className="border-t border-linha pt-5">
              <Botao variante="texto" onClick={() => setPasso('serie')}>
                Cancelar série semanal
              </Botao>
            </div>
          ) : null}
        </>
      ) : passo === 'exclusao' ? (
        <div className="flex flex-wrap items-center gap-6">
          <Botao
            disabled={excluir.isPending}
            onClick={() => void executar(() => excluir.mutateAsync(consulta.id))}
          >
            Excluir consulta
          </Botao>
          <Botao variante="texto" onClick={() => setPasso('edicao')}>
            Manter
          </Botao>
        </div>
      ) : (
        <div className="flex flex-wrap items-center gap-6">
          <Botao
            disabled={cancelarSerie.isPending}
            onClick={() =>
              void executar(() => cancelarSerie.mutateAsync(consulta.serieId as string))
            }
          >
            Cancelar série
          </Botao>
          <Botao variante="texto" onClick={() => setPasso('edicao')}>
            Manter a série
          </Botao>
        </div>
      )}

      {falha ? (
        <p className="border-l-2 border-perigo bg-superficie px-4 py-3 text-sm">{falha.mensagem}</p>
      ) : null}
    </Dialogo>
  )
}
