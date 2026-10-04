import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { deApi } from '../../api/data'
import { Botao } from '../../ui/Botao'
import { Chip } from '../../ui/Chip'
import { EstadoVazio } from '../../ui/EstadoVazio'
import { DialogoDePaciente } from '../pacientes/DialogoDePaciente'
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
  const [editando, setEditando] = useState(false)

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
              {/* `alerta` so no estado NAO autorizado (prototipo/index.html:94
                  usa `chip alerta` exatamente ali): e o estado que pede uma
                  acao do psicologo. Autorizado e fato consumado — tom neutro.
                  Com os dois no mesmo cinza, a distincao existia so no texto. */}
              {data.paciente.consentimentoAvisoEm ? (
                <Chip variante="neutro">Aviso autorizado</Chip>
              ) : data.paciente.descadastradoEm ? (
                <Chip variante="alerta">
                  Descadastrou-se em {deApi(data.paciente.descadastradoEm).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })}
                </Chip>
              ) : (
                <Chip variante="alerta">Aviso nao autorizado</Chip>
              )}
              {/* A porta da edicao (prototipo/index.html:95, mesmo bloco
                  `.acoes-ficha`). Sem ela, `DialogoDePaciente` recebia a prop
                  `paciente` e desenhava o titulo "Editar paciente" que nenhuma
                  tela conseguia abrir — metade da feature ficava inalcancavel
                  em producao. */}
              <Botao variante="secundario" className="mini" onClick={() => setEditando(true)}>
                Editar
              </Botao>
            </div>
          </div>

          {/* Montagem condicional, o mesmo que `PacientesPage.tsx` e
              `AgendaPage.tsx` fazem: o Radix desmonta so o CONTEUDO ao fechar,
              e o `useForm` sobrevivente traria de volta os valores e os erros
              da abertura anterior. Aqui ela faz uma segunda coisa: os
              `defaultValues` do dialogo sao lidos UMA vez, na montagem — sem
              isto, reabrir depois de salvar mostraria o paciente velho. */}
          {editando && (
            <DialogoDePaciente
              aberto
              paciente={data.paciente}
              aoFechar={() => setEditando(false)}
            />
          )}

          <aside aria-label="Pendencias" className="faixa-pendencias-bloco">
            <h2 className="titulo-secao">
              Pendencias
              {data.pendencias.length > 0 && (
                <span className="contador-pendencias">{data.pendencias.length}</span>
              )}
            </h2>
            {data.pendencias.length === 0 ? (
              <p className="sub">Nenhuma pendencia.</p>
            ) : (
              <ul className="faixa-pendencias">
                {data.pendencias.map((p) => (
                  <li className="linha-pendencia" key={p.id}>
                    {p.descricao}
                  </li>
                ))}
              </ul>
            )}
          </aside>
          <div className="prontuario">
            <h2 className="titulo-secao">Sessoes</h2>
            {data.linhaDoTempo.length === 0 ? (
              <EstadoVazio titulo="Nenhuma sessao registrada ainda." />
            ) : (
              <LinhaDoTempo sessoes={data.linhaDoTempo} pacienteId={pacienteId} />
            )}
          </div>
        </>
      )}
    </div>
  )
}
