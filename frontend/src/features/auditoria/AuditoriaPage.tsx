import { useSearchParams } from 'react-router-dom'

import type { AcaoAuditoria } from '../../api/tipos'
import { Campo } from '../../ui/Campo'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { Paginacao } from '../../ui/Paginacao'
import { Selecao } from '../../ui/Selecao'
import { Tabela } from '../../ui/Tabela'
import { Botao } from '../../ui/Botao'
import { dataHora, hora as horaDe } from '../../ui/data'
import { useAuditoria } from './queries'

/** Os nomes que a API grava na coluna `entidade`. */
const ENTIDADES = ['Paciente', 'Consulta', 'Anotacao', 'Lembrete']

const ACAO: Record<AcaoAuditoria, string> = {
  CRIACAO: 'criou',
  VISUALIZACAO: 'consultou',
  ATUALIZACAO: 'alterou',
  EXCLUSAO: 'excluiu',
}

export function AuditoriaPage() {
  const [parametros, setParametros] = useSearchParams()
  const entidade = parametros.get('entidade') ?? ''
  const entidadeId = parametros.get('entidadeId') ?? ''
  const pagina = Number(parametros.get('page') ?? '0')

  const trilha = useAuditoria({
    entidade: entidade || undefined,
    entidadeId: entidadeId || undefined,
    pagina,
  })

  /** Trocar o filtro volta para a primeira pagina — a antiga pode nem existir. */
  function filtrar(mudanca: Partial<{ entidade: string; entidadeId: string; page: string }>) {
    const proximo: Record<string, string> = { entidade, entidadeId, page: '0', ...mudanca }
    for (const chave of Object.keys(proximo)) {
      if (!proximo[chave]) delete proximo[chave]
    }
    setParametros(proximo, { replace: true })
  }

  const total = trilha.data?.page.totalElements ?? 0
  const totalPaginas = trilha.data?.page.totalPages ?? 1
  const itens = trilha.data?.content ?? []

  return (
    <div className="flex flex-col gap-10">
      <header className="flex flex-col gap-2 border-b border-linha pb-6">
        <h1 className="font-serif text-4xl leading-none">Trilha</h1>
        <p className="max-w-xl text-sm text-tinta-2">
          Tudo o que foi criado, alterado e excluído nesta conta. A trilha não é editável nem por
          você.
        </p>
      </header>

      <div className="flex flex-wrap items-end gap-6">
        <Selecao
          rotulo="Tipo de registro"
          valor={entidade}
          aoMudar={(valor) => filtrar({ entidade: valor })}
          vazio="Tudo"
          opcoes={ENTIDADES.map((nome) => ({ valor: nome, rotulo: nome }))}
        />
        <div className="min-w-64 flex-1">
          <Campo
            rotulo="Identificador"
            valor={entidadeId}
            aoMudar={(valor) => filtrar({ entidadeId: valor })}
            exemplo="cole aqui o id de um paciente ou de uma consulta"
          />
        </div>
      </div>

      {itens.length === 0 ? (
        trilha.isPending ? null : (
          <EstadoVazio>
            {entidade || entidadeId
              ? 'Nenhum registro com esse filtro.'
              : 'A trilha começa no primeiro cadastro.'}
          </EstadoVazio>
        )
      ) : (
        <Tabela colunas={['Quando', 'Ação', 'Registro', 'Identificador']}>
          {itens.map((log, indice) => {
            /*
              A trilha e cronologica e um dia inteiro de trabalho cabe em poucos
              minutos de relogio: repetir "sex, 28 ago 2026" em vinte linhas
              seguidas enche a coluna sem informar nada. A data aparece quando
              muda; nas demais linhas fica so a hora.
             */
            const dia = log.createdAt.split('T')[0]
            const diaAnterior = indice > 0 ? itens[indice - 1].createdAt.split('T')[0] : null

            return (
              <tr key={log.id} className="border-b border-linha">
                <td className="py-3 pr-6 text-sm tabular-nums">
                  {dia === diaAnterior ? (
                    <span className="text-tinta-2">{horaDe(log.createdAt)}</span>
                  ) : (
                    dataHora(log.createdAt)
                  )}
                </td>
                <td className="py-3 pr-6 text-sm">{ACAO[log.acao]}</td>
                <td className="py-3 pr-6 text-sm">{log.entidade}</td>
                <td className="py-3 text-sm tabular-nums">
                  {/* clicar filtra a trilha por este registro — e o unico jeito
                      de seguir a historia de um paciente sem sair colando id */}
                  <Botao
                    variante="texto"
                    title={log.entidadeId}
                    onClick={() => filtrar({ entidadeId: log.entidadeId })}
                  >
                    {log.entidadeId.slice(0, 8)}…
                  </Botao>
                </td>
              </tr>
            )
          })}
        </Tabela>
      )}

      <Paginacao
        pagina={pagina}
        totalPaginas={totalPaginas}
        contagem={total === 1 ? '1 registro' : `${total} registros`}
        aoMudar={(destino) => filtrar({ page: String(destino) })}
      />
    </div>
  )
}
