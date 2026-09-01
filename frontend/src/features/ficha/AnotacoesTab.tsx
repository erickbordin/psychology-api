import { useEffect, useId, useState } from 'react'
import { useBlocker, useParams, useSearchParams } from 'react-router-dom'

import { ErroApi } from '../../api/erro'
import { Botao } from '../../ui/Botao'
import { Cartao } from '../../ui/Cartao'
import { Dialogo } from '../../ui/Dialogo'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { Paginacao } from '../../ui/Paginacao'
import { dataDeSessao } from '../../ui/data'
import { useAnotacoes, useCriarAnotacao, useExcluirAnotacao } from './queries'

export function AnotacoesTab() {
  const { pacienteId = '' } = useParams()
  const [parametros, setParametros] = useSearchParams()
  const pagina = Number(parametros.get('page') ?? '0')

  const anotacoes = useAnotacoes(pacienteId, pagina)
  const criar = useCriarAnotacao(pacienteId)
  const excluir = useExcluirAnotacao(pacienteId)

  const [rascunho, setRascunho] = useState('')
  const [aApagar, setAApagar] = useState<string | null>(null)
  const idDoRascunho = useId()
  const temRascunho = rascunho.trim().length > 0

  /**
   * O rascunho vive so em memoria — anotacao clinica nao fica em claro no
   * navegador. Isso obriga a guarda: sem ela, "em memoria" viraria "perde em
   * silencio" numa navegacao acidental.
   */
  const bloqueio = useBlocker(temRascunho)

  useEffect(() => {
    if (!temRascunho) return

    function avisar(evento: BeforeUnloadEvent) {
      evento.preventDefault()
    }

    window.addEventListener('beforeunload', avisar)
    return () => window.removeEventListener('beforeunload', avisar)
  }, [temRascunho])

  async function registrar() {
    if (!temRascunho) return
    try {
      await criar.mutateAsync(rascunho)
      setRascunho('')
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  async function apagar() {
    if (!aApagar) return
    try {
      await excluir.mutateAsync(aApagar)
      setAApagar(null)
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  const total = anotacoes.data?.page.totalElements ?? 0
  const totalPaginas = anotacoes.data?.page.totalPages ?? 1
  const itens = anotacoes.data?.content ?? []

  return (
    <div className="flex flex-col gap-6">
      {bloqueio.state === 'blocked' ? (
        <Cartao tom="atencao" className="flex flex-col gap-4 p-5">
          <p className="text-sm">Você tem uma anotação não enviada. Sair perde o texto.</p>
          <div className="flex flex-wrap items-center gap-4">
            <Botao onClick={() => bloqueio.reset()}>Continuar aqui</Botao>
            <Botao variante="texto" onClick={() => bloqueio.proceed()}>
              Sair sem salvar
            </Botao>
          </div>
        </Cartao>
      ) : null}

      <Cartao className="flex flex-col gap-4 p-5">
        <div className="flex items-baseline justify-between gap-4">
          <label htmlFor={idDoRascunho} className="text-sm font-semibold">
            Anotação da sessão
          </label>
          <span className="text-sm text-tinta-2">
            {total === 1 ? '1 anotação' : `${total} anotações`}
          </span>
        </div>
        <textarea
          id={idDoRascunho}
          rows={4}
          value={rascunho}
          placeholder="O que aconteceu nesta sessão."
          onChange={(evento) => setRascunho(evento.target.value)}
          className="w-full resize-y rounded-xl border border-linha bg-superficie-2 px-3.5 py-3 text-[15px] leading-relaxed outline-none transition-colors placeholder:text-tinta-3 focus:border-acento focus:bg-superficie focus:ring-4 focus:ring-acento/15"
        />
        <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
          <Botao onClick={() => void registrar()} disabled={criar.isPending}>
            Registrar anotação
          </Botao>
          <span className="text-sm text-tinta-2">
            Não dá para editar depois. Apagar tira da ficha, mas o registro fica na trilha.
          </span>
        </div>
        {criar.error instanceof ErroApi ? (
          <p className="rounded-xl border border-perigo/30 bg-perigo-suave px-4 py-3 text-sm text-perigo">
            {criar.error.mensagem}
          </p>
        ) : null}
      </Cartao>

      <section className="flex flex-col gap-4">
        {itens.length === 0 ? (
          anotacoes.isPending ? null : (
            <EstadoVazio>Nenhuma sessão registrada ainda.</EstadoVazio>
          )
        ) : (
          /*
            A trilha so cresce e o que ja foi registrado esta selado: cada
            anotacao e um cartao fechado, com a data na coluna da esquerda para
            o olho descer pelas datas sem ler os textos.
          */
          <ol className="flex flex-col gap-2.5">
            {itens.map((anotacao) => (
              <li key={anotacao.id}>
                <Cartao className="grid gap-3 p-5 sm:grid-cols-[8rem_1fr] sm:gap-6">
                  <span className="text-sm font-medium tabular-nums text-tinta-2">
                    {dataDeSessao(anotacao.createdAt)}
                  </span>
                  <div className="flex flex-col items-start gap-3">
                    <p className="text-[15px] leading-relaxed">{anotacao.conteudo}</p>
                    <Botao variante="discreto" onClick={() => setAApagar(anotacao.id)}>
                      Apagar
                    </Botao>
                  </div>
                </Cartao>
              </li>
            ))}
          </ol>
        )}

        <Paginacao
          pagina={pagina}
          totalPaginas={totalPaginas}
          contagem={total === 1 ? '1 anotação' : `${total} anotações`}
          aoMudar={(destino) => setParametros({ page: String(destino) })}
        />
      </section>

      <Dialogo
        aberto={aApagar !== null}
        aoFechar={() => setAApagar(null)}
        titulo="Apagar esta anotação?"
        descricao="Ela sai da ficha e não volta. O registro da exclusão fica na trilha de auditoria."
      >
        <div className="flex flex-wrap items-center gap-4">
          <Botao onClick={() => void apagar()} disabled={excluir.isPending}>
            Apagar anotação
          </Botao>
          <Botao variante="secundario" onClick={() => setAApagar(null)}>
            Manter
          </Botao>
        </div>
      </Dialogo>
    </div>
  )
}
