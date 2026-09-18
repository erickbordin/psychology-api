import { deApi } from '../../api/data'
import { Chip } from '../../ui/Chip'
import { RegistroDaSessao } from './RegistroDaSessao'
import type { SessaoDaFicha } from './queries'

/** `src/api/data.ts` so exporta `paraApi`/`deApi` — nao existe `dataBR` nem
 *  `dataHoraBR` (o plano supunha um formatador pronto que nao existe la).
 *  Formatacao manual em pt-BR aqui, mesmo padrao da funcao `hora` de
 *  `telas/hoje/HojePage.tsx`. */
const dataEHora = (iso: string) => {
  const d = deApi(iso)
  return `${d.toLocaleDateString('pt-BR')} · ${d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`
}

/** A MESMA correspondencia usada em `telas/hoje/HojePage.tsx` (LinhaDaSessao):
 *  o status de uma sessao tem que ter uma cor so no produto inteiro. Quando
 *  esta tela desenhava `<span className="chip">` cru, REALIZADA saia verde na
 *  Hoje e cinza aqui — duas linguagens visuais para o mesmo dado. */
const varianteDoStatus = (status: SessaoDaFicha['status']) =>
  status === 'REALIZADA' ? 'realizada' : status === 'FALTOU' ? 'faltou' : undefined

type Props = {
  sessoes: SessaoDaFicha[]
  pacienteId: string
}

export function LinhaDoTempo({ sessoes, pacienteId }: Props) {
  return (
    // `aria-label` casando com o <h2> "Sessoes" que FichaPage poe acima: e o
    // que deixa o teste (e o leitor de tela) separar esta lista da lista de
    // pendencias do aside, POR ROTULO ESCRITO — a convencao da casa proibe
    // navegar por `className`.
    <ul className="linha-tempo cartao" aria-label="Sessoes">
      {sessoes.map((s) => (
        <li key={s.consultaId}>
          <div className="acoes-linha">
            <span className="hora">{dataEHora(s.dtConsulta)}</span>
            <Chip variante={varianteDoStatus(s.status)}>{s.status}</Chip>
          </div>
          {/* A anotacao vive DENTRO da linha da sessao — e a fusao das abas
              Consultas e Anotacoes que 4(3) pede. Sessao sem anotacao nao
              ganha caixa vazia: ausencia de anotacao e informacao, e uma
              moldura vazia parece defeito de carregamento. */}
          {s.anotacoes.map((a) => (
            <p key={a.anotacaoId} className="sub">
              {a.conteudo}
            </p>
          ))}
          {/* So sessao REALIZADA e sem anotacao ganha a caixa de registro
              (Tarefa 5): escrever anotacao de sessao que ainda nao aconteceu
              (AGENDADA) nao e fluxo do produto, e a caixa ali convida ao
              engano. Sessao ja anotada tambem nao ganha — a anotacao acima
              ja é o registro dela. */}
          {s.status === 'REALIZADA' && s.anotacoes.length === 0 && (
            <RegistroDaSessao pacienteId={pacienteId} sessao={s} />
          )}
        </li>
      ))}
    </ul>
  )
}
