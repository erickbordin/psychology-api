import { useState } from 'react'

import { ErroApi } from '../../api/erro'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { Dialogo } from '../../ui/Dialogo'
import { Selecao } from '../../ui/Selecao'
import { hojeIso, paraLocalDateTime } from '../../ui/data'
import { usePacientes } from '../pacientes/queries'
import { useCriarConsulta } from './queries'

export function NovaConsultaDialogo({ aoFechar }: { aoFechar: () => void }) {
  const pacientes = usePacientes()
  const criar = useCriarConsulta()

  const [pacienteId, setPacienteId] = useState('')
  const [data, setData] = useState(hojeIso())
  const [hora, setHora] = useState('15:00')

  const falha = criar.error instanceof ErroApi ? criar.error : null

  async function agendar() {
    if (!pacienteId || !data || !hora) return
    try {
      await criar.mutateAsync({ pacienteId, dtConsulta: paraLocalDateTime(data, hora) })
      aoFechar()
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  return (
    <Dialogo aberto aoFechar={aoFechar} titulo="Nova consulta">
      <Selecao
        rotulo="Paciente"
        valor={pacienteId}
        aoMudar={setPacienteId}
        vazio="Escolha o paciente"
        opcoes={(pacientes.data ?? []).map((paciente) => ({
          valor: paciente.idPaciente,
          rotulo: paciente.nome,
        }))}
        erro={falha?.mensagemDoCampo('pacienteId')}
      />
      <Campo
        rotulo="Data"
        tipo="date"
        valor={data}
        aoMudar={setData}
        erro={falha?.mensagemDoCampo('dtConsulta')}
      />
      <Campo rotulo="Hora" tipo="time" valor={hora} aoMudar={setHora} />

      {falha && falha.erros.length === 0 ? (
        <p className="rounded-xl border border-perigo/30 bg-perigo-suave px-4 py-3 text-sm text-perigo">{falha.mensagem}</p>
      ) : null}

      <div className="flex flex-wrap items-center gap-4">
        <Botao onClick={() => void agendar()} disabled={criar.isPending}>
          Agendar
        </Botao>
        <Botao variante="secundario" onClick={aoFechar}>
          Cancelar
        </Botao>
      </div>
    </Dialogo>
  )
}
