import { Link, useParams } from 'react-router-dom'

import { EstadoVazio } from '../../ui/EstadoVazio'
import type { Paciente } from '../pacientes/queries'
import { LinhaDoTempo } from './LinhaDoTempo'
import { useFicha } from './queries'

/** Idade a partir de `dataNascimento` (so data, "1992-03-14", sem hora) —
 *  calculada por partes, sem passar por `deApi`: `deApi` existe para o
 *  formato COM hora local (spec 5.9); usa-la aqui converteria a data via
 *  fuso e podia empurrar o dia num sentido ou noutro perto da meia-noite. */
function idade(dataNascimento: string | null): string | null {
  if (!dataNascimento) return null
  const [ano, mes, dia] = dataNascimento.split('-').map(Number)
  const hoje = new Date()
  let anos = hoje.getFullYear() - ano
  const aindaNaoFezAniversario =
    hoje.getMonth() + 1 < mes || (hoje.getMonth() + 1 === mes && hoje.getDate() < dia)
  if (aindaNaoFezAniversario) anos -= 1
  return `${anos} anos`
}

const valorPorSessao = (valor: Paciente['valorSessao']): string | null =>
  valor == null
    ? null
    : `${valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })} por sessao`

/** Spec 4③: cabecalho traz nome, idade, contato e valor de sessao. Os
 *  pedacos que faltarem (paciente sem data de nascimento, sem telefone nem
 *  email) somem em vez de deixar "· ·" no meio da frase. */
const linhaDoSub = (paciente: Paciente): string =>
  [idade(paciente.dataNascimento), paciente.telefone ?? paciente.email, valorPorSessao(paciente.valorSessao)]
    .filter(Boolean)
    .join(' · ')

export function FichaPage() {
  // `useParams()` e a API do router para isto, e ela exige um `<Route>`
  // casando acima na arvore. A primeira versao desta tela leu o id fatiando
  // `useLocation().pathname`, porque o `renderizar()` do teste embrulha so em
  // `<MemoryRouter>` e o `useParams` vinha vazio — mas isso era a producao se
  // curvando a uma limitacao do harness, e fatiar caminho quebra com query
  // string, barra no fim, ou qualquer mudanca na forma da rota. Quem se
  // ajustou foi o teste, que agora monta o `<Routes>` que a producao tem.
  const { pacienteId = '' } = useParams()
  const { data, isPending, isError } = useFicha(pacienteId)

  return (
    <div className="tela">
      <Link className="voltar" to="/pacientes">
        ← Pacientes
      </Link>

      {/* regiao viva sempre presente, mesmo padrao de PacientesPage.tsx: leitor
          de tela so anuncia conteudo que entra numa regiao que ja estava no
          DOM antes da mudanca. */}
      <p className="sub" aria-live="polite">
        {isPending ? 'Carregando…' : null}
      </p>

      {isError && (
        <p className="anuncio" role="alert">
          Nao foi possivel carregar a ficha. Tente de novo.
        </p>
      )}

      {data && (
        <>
          <div className="cabecalho-ficha cartao">
            <div>
              <h1>{data.paciente.nome}</h1>
              <p className="sub">{linhaDoSub(data.paciente)}</p>
            </div>
            <div className="acoes-ficha">
              <span className="chip">
                {data.paciente.consentimentoAvisoEm ? 'Aviso autorizado' : 'Aviso nao autorizado'}
              </span>
            </div>
          </div>

          <div className="colunas-ficha">
            <div>
              <h2 className="titulo-secao">Sessoes</h2>
              {data.linhaDoTempo.length === 0 ? (
                <EstadoVazio titulo="Nenhuma sessao registrada ainda." />
              ) : (
                <LinhaDoTempo sessoes={data.linhaDoTempo} pacienteId={pacienteId} />
              )}
            </div>

            <aside aria-label="Pendencias">
              <h2 className="titulo-secao">Pendencias</h2>
              {data.pendencias.length === 0 ? (
                <p className="sub">Nenhuma pendencia.</p>
              ) : (
                <ul className="cartao lista-sessoes">
                  {data.pendencias.map((p) => (
                    <li className="linha-pendencia" key={p.id}>
                      {p.descricao}
                    </li>
                  ))}
                </ul>
              )}
            </aside>
          </div>
        </>
      )}
    </div>
  )
}
