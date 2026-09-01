import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'

import { Avatar } from '../../ui/Avatar'
import { Cartao } from '../../ui/Cartao'
import { IconeCalendario, IconePessoas } from '../../ui/icones'
import { dataLonga, hojeIso } from '../../ui/data'
import { useAgenda } from '../agenda/queries'
import { usePacientes } from '../pacientes/queries'
import { useSessao } from '../auth/useSessao'

/**
 * Numero grande com rotulo pequeno: e um cartao de leitura, nao um link. Quem
 * quer agir clica no atalho abaixo, que tem area de clique de verdade.
 */
function Indicador({
  icone,
  rotulo,
  valor,
}: {
  icone: ReactNode
  rotulo: string
  valor: string
}) {
  return (
    <Cartao className="flex items-center gap-4 p-5">
      <span className="inline-flex size-11 shrink-0 items-center justify-center rounded-xl bg-acento-suave text-acento">
        {icone}
      </span>
      <div className="flex min-w-0 flex-col">
        <span className="text-sm text-tinta-2">{rotulo}</span>
        <span className="text-3xl font-bold leading-tight tabular-nums">{valor}</span>
      </div>
    </Cartao>
  )
}

function Atalho({ para, rotulo, icone }: { para: string; rotulo: string; icone: ReactNode }) {
  return (
    <Link
      to={para}
      className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-linha bg-superficie px-5 py-9 text-acento shadow-[0_1px_2px_rgba(15,23,42,0.04)] transition-colors hover:border-acento-linha hover:bg-acento-suave focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-acento"
    >
      {icone}
      <span className="text-lg font-semibold text-tinta">{rotulo}</span>
    </Link>
  )
}

/**
 * A porta do produto. Duas contagens e dois atalhos: o psicologo abre o sistema
 * para saber quantas sessoes tem hoje e para entrar na agenda ou na lista. Nada
 * aqui e dado clinico — o painel fica em tela cheia num consultorio, e ninguem
 * quer o nome de um paciente aparecendo nele.
 */
export function InicioPage() {
  const { usuario } = useSessao()
  const hoje = hojeIso()
  const agenda = useAgenda(hoje, hoje)
  const pacientes = usePacientes()

  const nome = usuario?.nome
  const saudacao = nome ? `Olá, ${nome}` : 'Olá'

  return (
    <div className="flex flex-col gap-7">
      <header className="flex items-start justify-between gap-6">
        <div className="flex flex-col gap-1">
          <h1 className="text-3xl font-bold tracking-tight">{saudacao}</h1>
          <p className="text-sm text-tinta-2">{dataLonga(hoje)}</p>
        </div>
        {usuario ? <Avatar nome={nome ?? usuario.email} medida="m" /> : null}
      </header>

      <div className="grid gap-4 sm:grid-cols-2">
        <Indicador
          icone={<IconeCalendario tamanho={22} />}
          rotulo="Consultas hoje"
          valor={agenda.data ? String(agenda.data.length) : '—'}
        />
        <Indicador
          icone={<IconePessoas tamanho={22} />}
          rotulo="Pacientes cadastrados"
          valor={pacientes.data ? String(pacientes.data.length) : '—'}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Atalho para="/agenda" rotulo="Agenda" icone={<IconeCalendario tamanho={30} />} />
        <Atalho para="/pacientes" rotulo="Pacientes" icone={<IconePessoas tamanho={30} />} />
      </div>
    </div>
  )
}
