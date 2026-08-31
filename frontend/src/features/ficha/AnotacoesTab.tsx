import { useEffect, useId, useState } from 'react'
import { useBlocker, useParams, useSearchParams } from 'react-router-dom'

import { ErroApi } from '../../api/erro'
import { Botao } from '../../ui/Botao'
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
    <div className="flex flex-col gap-10">
      {bloqueio.state === 'blocked' ? (
        <div className="flex flex-col gap-4 border-l-2 border-atencao bg-superficie px-5 py-4">
          <p className="text-sm">Você tem uma anotação não enviada. Sair perde o texto.</p>
          <div className="flex flex-wrap items-center gap-6">
            <Botao onClick={() => bloqueio.reset()}>Continuar aqui</Botao>
            <Botao variante="texto" onClick={() => bloqueio.proceed()}>
              Sair sem salvar
            </Botao>
          </div>
        </div>
      ) : null}

      {/*
        O compositor e o unico bloco da pagina sem filete embaixo: a borda aberta
        e o modelo de dados aparecendo na tela. A trilha so cresce, e o que ja
        foi registrado esta selado — cada anotacao abaixo e fechada por um filete.
      */}
      <section className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between gap-4">
          <label htmlFor={idDoRascunho} className="text-sm font-medium text-tinta-2">
            Anotação da sessão
          </label>
          <span className="text-sm text-tinta-2">
            {total === 1 ? '1 anotação' : `${total} anotações`}
          </span>
        </div>
        <div className="border-l-2 border-acento pl-4">
          <textarea
            id={idDoRascunho}
            rows={4}
            value={rascunho}
            placeholder="O que aconteceu nesta sessão."
            onChange={(evento) => setRascunho(evento.target.value)}
            className="w-full resize-y bg-transparent font-serif text-[17px] leading-relaxed outline-none placeholder:font-sans placeholder:text-base placeholder:text-tinta-3"
          />
        </div>
        <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
          <Botao onClick={() => void registrar()} disabled={criar.isPending}>
            Registrar anotação
          </Botao>
          <span className="text-sm text-tinta-2">
            Não dá para editar depois. Apagar tira da ficha, mas o registro fica na trilha.
          </span>
        </div>
        {criar.error instanceof ErroApi ? (
          <p className="border-l-2 border-perigo bg-superficie px-4 py-3 text-sm">
            {criar.error.mensagem}
          </p>
        ) : null}
      </section>

      <section className="flex flex-col gap-4">
        {itens.length === 0 ? (
          anotacoes.isPending ? null : (
            <EstadoVazio>Nenhuma sessão registrada ainda.</EstadoVazio>
          )
        ) : (
          <ol className="border-t border-linha">
            {itens.map((anotacao) => (
              <li
                key={anotacao.id}
                className="grid gap-2 border-b border-linha py-6 sm:grid-cols-[7.5rem_1fr] sm:gap-7"
              >
                <span className="text-sm tabular-nums text-tinta-2">
                  {dataDeSessao(anotacao.createdAt)}
                </span>
                <div className="flex flex-col items-start gap-3">
                  <p className="font-serif text-[17px] leading-relaxed">{anotacao.conteudo}</p>
                  <Botao variante="texto" onClick={() => setAApagar(anotacao.id)}>
                    Apagar
                  </Botao>
                </div>
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
        <div className="flex flex-wrap items-center gap-6">
          <Botao onClick={() => void apagar()} disabled={excluir.isPending}>
            Apagar anotação
          </Botao>
          <Botao variante="texto" onClick={() => setAApagar(null)}>
            Manter
          </Botao>
        </div>
      </Dialogo>
    </div>
  )
}
