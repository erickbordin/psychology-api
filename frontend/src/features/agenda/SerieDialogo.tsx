import { useState } from 'react'

import { ErroApi } from '../../api/erro'
import { Botao } from '../../ui/Botao'
import { Campo } from '../../ui/Campo'
import { Dialogo } from '../../ui/Dialogo'
import { Selecao } from '../../ui/Selecao'
import { dataDeSessao, hojeIso, paraLocalDateTime } from '../../ui/data'
import { usePacientes } from '../pacientes/queries'
import { MAX_OCORRENCIAS, MIN_OCORRENCIAS, ocorrenciasSemanais } from './ocorrencias'
import { useCriarSerie } from './queries'

type Horizonte = 'quantidade' | 'limite'

export function SerieDialogo({ aoFechar }: { aoFechar: () => void }) {
  const pacientes = usePacientes()
  const criar = useCriarSerie()

  const [pacienteId, setPacienteId] = useState('')
  const [data, setData] = useState(hojeIso())
  const [hora, setHora] = useState('15:00')
  const [horizonte, setHorizonte] = useState<Horizonte>('quantidade')
  const [quantidade, setQuantidade] = useState('8')
  const [limite, setLimite] = useState('')

  const falha = criar.error instanceof ErroApi ? criar.error : null
  const porQuantidade = horizonte === 'quantidade'

  const datas = ocorrenciasSemanais(
    data,
    porQuantidade ? Number(quantidade) : undefined,
    porQuantidade ? undefined : limite || undefined,
  )

  async function criarSerie() {
    if (!pacienteId || !data || !hora) return
    try {
      await criar.mutateAsync({
        pacienteId,
        dtPrimeiraConsulta: paraLocalDateTime(data, hora),
        // XOR: a API recusa os dois juntos e os dois ausentes
        quantidadeSessoes: porQuantidade ? Number(quantidade) : undefined,
        dtLimite: porQuantidade ? undefined : limite,
      })
      aoFechar()
    } catch (problema) {
      if (!(problema instanceof ErroApi)) throw problema
    }
  }

  return (
    <Dialogo
      aberto
      aoFechar={aoFechar}
      titulo="Série semanal"
      descricao="Mesma hora, toda semana. A API gera as sessões de uma vez só."
    >
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
        rotulo="Primeira sessão"
        tipo="date"
        valor={data}
        aoMudar={setData}
        erro={falha?.mensagemDoCampo('dtPrimeiraConsulta')}
      />
      <Campo rotulo="Hora" tipo="time" valor={hora} aoMudar={setHora} />

      <Selecao
        rotulo="Até quando"
        valor={horizonte}
        aoMudar={(valor) => setHorizonte(valor as Horizonte)}
        opcoes={[
          { valor: 'quantidade', rotulo: 'Um número de sessões' },
          { valor: 'limite', rotulo: 'Uma data limite' },
        ]}
      />

      {porQuantidade ? (
        <Campo
          rotulo="Quantidade de sessões"
          tipo="number"
          valor={quantidade}
          aoMudar={setQuantidade}
          exemplo={`de ${MIN_OCORRENCIAS} a ${MAX_OCORRENCIAS}`}
          erro={falha?.mensagemDoCampo('quantidadeSessoes')}
        />
      ) : (
        <Campo
          rotulo="Data limite"
          tipo="date"
          valor={limite}
          aoMudar={setLimite}
          erro={falha?.mensagemDoCampo('dtLimite') ?? falha?.mensagemDoCampo('quantidadeSessoes')}
        />
      )}

      {/*
        O preview existe porque criar doze sessoes as cegas e a pior forma de
        descobrir que a primeira data estava errada.
      */}
      {datas.length > 0 ? (
        <div className="flex flex-col gap-2 border-l-2 border-acento pl-4">
          <p className="text-sm font-medium text-tinta-2">
            {datas.length === 1 ? '1 sessão' : `${datas.length} sessões`} às {hora}
          </p>
          <ul className="flex flex-col gap-1 text-sm tabular-nums text-tinta-2">
            {datas.slice(0, 6).map((dia) => (
              <li key={dia}>{dataDeSessao(dia)}</li>
            ))}
            {datas.length > 6 ? (
              <li className="text-tinta-3">
                … e mais {datas.length - 6}, até {dataDeSessao(datas[datas.length - 1])}
              </li>
            ) : null}
          </ul>
        </div>
      ) : null}

      {falha ? (
        <p className="border-l-2 border-perigo bg-superficie px-4 py-3 text-sm">
          {falha.status === 409
            ? `Conflito de horário: nenhuma sessão foi criada. ${falha.mensagem}`
            : falha.erros.length === 0
              ? falha.mensagem
              : null}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-6">
        <Botao onClick={() => void criarSerie()} disabled={criar.isPending}>
          Criar série
        </Botao>
        <Botao variante="texto" onClick={aoFechar}>
          Cancelar
        </Botao>
      </div>
    </Dialogo>
  )
}
