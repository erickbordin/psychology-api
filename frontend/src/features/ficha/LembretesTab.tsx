import { useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'

import { ErroApi } from '../../api/erro'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { Paginacao } from '../../ui/Paginacao'
import { dataCurta } from '../../ui/data'
import { useConcluirLembrete, useCriarLembrete, useExcluirLembrete, useLembretes } from './queries'

export function LembretesTab() {
  const { pacienteId = '' } = useParams()
  const [parametros, setParametros] = useSearchParams()
  const pagina = Number(parametros.get('page') ?? '0')

  const lembretes = useLembretes(pacienteId, pagina)
  const criar = useCriarLembrete(pacienteId)
  const concluir = useConcluirLembrete(pacienteId, pagina)
  const excluir = useExcluirLembrete(pacienteId)

  const [descricao, setDescricao] = useState('')

  const falha = criar.error instanceof ErroApi ? criar.error : null

  async function adicionar() {
    if (!descricao.trim()) return
    try {
      await criar.mutateAsync(descricao)
      setDescricao('')
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  const total = lembretes.data?.page.totalElements ?? 0
  const totalPaginas = lembretes.data?.page.totalPages ?? 1
  const itens = lembretes.data?.content ?? []

  return (
    <div className="flex flex-col gap-10">
      <section className="flex max-w-xl flex-col gap-4">
        <Campo
          rotulo="Novo lembrete"
          valor={descricao}
          aoMudar={setDescricao}
          exemplo="Retomar o registro de sono na próxima sessão."
          erro={falha?.mensagemDoCampo('descricao')}
        />
        <div className="flex flex-wrap items-center gap-6">
          <Botao onClick={() => void adicionar()} disabled={criar.isPending}>
            Adicionar lembrete
          </Botao>
          <span className="text-sm text-tinta-2">
            {total === 1 ? '1 lembrete' : `${total} lembretes`}
          </span>
        </div>
      </section>

      <section className="flex flex-col gap-4">
        {itens.length === 0 ? (
          lembretes.isPending ? null : (
            <EstadoVazio>Nada pendente para este paciente.</EstadoVazio>
          )
        ) : (
          <ul className="border-t border-linha">
            {itens.map((lembrete) => (
              <li
                key={lembrete.id}
                className="flex flex-wrap items-baseline gap-x-6 gap-y-2 border-b border-linha py-4"
              >
                <span
                  className={`flex-1 text-sm ${
                    lembrete.concluido ? 'text-tinta-3 line-through' : ''
                  }`}
                >
                  {lembrete.descricao}
                </span>
                <span className="text-sm tabular-nums text-tinta-3">
                  {dataCurta(lembrete.createdAt)}
                </span>
                {lembrete.concluido ? (
                  <span className="text-sm text-sucesso">concluído</span>
                ) : (
                  <Botao variante="texto" onClick={() => concluir.mutate(lembrete.id)}>
                    Concluir
                  </Botao>
                )}
                <Botao variante="discreto" onClick={() => excluir.mutate(lembrete.id)}>
                  Excluir
                </Botao>
              </li>
            ))}
          </ul>
        )}

        <Paginacao
          pagina={pagina}
          totalPaginas={totalPaginas}
          contagem={total === 1 ? '1 lembrete' : `${total} lembretes`}
          aoMudar={(destino) => setParametros({ page: String(destino) })}
        />
      </section>
    </div>
  )
}
