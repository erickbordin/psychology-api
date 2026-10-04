import { useState } from 'react'

import { deApi, paraApi } from '../../api/data'
import { Chip } from '../../ui/Chip'
import { useAjustes } from '../ajustes/queries'
import { DialogoDeConsulta } from './DialogoDeConsulta'
import { horasDaGrade } from './grade'
import { useConsultasDaSemana, type ConsultaDaAgenda } from './queries'

const ESTADO: Record<'REALIZADA' | 'AGENDADA' | 'FALTOU', { variante: 'realizada' | 'neutro' | 'faltou'; texto: string }> = {
  REALIZADA: { variante: 'realizada', texto: 'Realizada' },
  AGENDADA: { variante: 'neutro', texto: 'Agendada' },
  FALTOU: { variante: 'faltou', texto: 'Faltou' },
}

/** Segunda a domingo da semana que contem `base`. */
function diasDaSemana(base: Date): Date[] {
  const segunda = new Date(base)
  const desvio = (segunda.getDay() + 6) % 7 // domingo=0 vira 6
  segunda.setDate(segunda.getDate() - desvio)
  segunda.setHours(0, 0, 0, 0)
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(segunda)
    d.setDate(segunda.getDate() + i)
    return d
  })
}

const doisDigitos = (n: number) => String(n).padStart(2, '0')

/**
 * Data PURA (`yyyy-MM-dd`): `de`/`ate` de `GET /consultas` sao `date`, sem
 * hora — diferente do `dtConsulta`, que `paraApi` formata com hora. Formatado
 * a mao pelo mesmo motivo do comentario em `api/data.ts`: nao converter para
 * UTC antes de cortar, so pra depois esconder a conversao.
 */
function paraDataPura(d: Date): string {
  return `${d.getFullYear()}-${doisDigitos(d.getMonth() + 1)}-${doisDigitos(d.getDate())}`
}

/**
 * So a consulta que ocupa a celula (mesmo dia e mesma hora do grid) e
 * devolvida. CANCELADA nunca ocupa — mesma regra que os caminhos de
 * gravacao usam para liberar o horario para outra consulta.
 */
function encontrarConsulta(
  consultas: ConsultaDaAgenda[],
  celula: Date,
): ConsultaDaAgenda | undefined {
  return consultas.find((c) => {
    if (c.status === 'CANCELADA') return false
    const dt = deApi(c.dtConsulta)
    return (
      dt.getFullYear() === celula.getFullYear() &&
      dt.getMonth() === celula.getMonth() &&
      dt.getDate() === celula.getDate() &&
      dt.getHours() === celula.getHours()
    )
  })
}

export function AgendaPage() {
  const [semana] = useState(() => diasDaSemana(new Date()))
  const [quando, setQuando] = useState<string | null>(null)

  const {
    data: consultas = [],
    isLoading,
    isError,
  } = useConsultasDaSemana(paraDataPura(semana[0]), paraDataPura(semana[6]))
  const { data: ajustes, isLoading: carregandoAjustes } = useAjustes()

  // spec 6.2: botao nunca morre em silencio — e o mesmo principio vale para
  // uma busca que falha. Sem isto, a grade desenha as 91 celulas vazias e a
  // psicologa le "semana livre" onde na verdade a API caiu — uma mentira
  // silenciosa pior do que nao mostrar nada (Critico 4 da revisao do
  // conjunto). Comparar com `HojePage.tsx`, que ja trata os dois estados.
  if (isLoading || carregandoAjustes) return <p>Carregando a semana…</p>
  if (isError) return <p role="alert">Nao foi possivel carregar a semana. Tente de novo.</p>

  const horasOcupadas = consultas
    .filter((c) => c.status !== 'CANCELADA')
    .map((c) => deApi(c.dtConsulta).getHours())
  const horas = horasDaGrade(ajustes?.expedienteInicio ?? null, ajustes?.expedienteFim ?? null, horasOcupadas)

  const hoje = new Date()
  const ehHoje = (d: Date) =>
    d.getFullYear() === hoje.getFullYear() && d.getMonth() === hoje.getMonth() && d.getDate() === hoje.getDate()

  return (
    <div className="tela">
      {/* semana por padrao (spec 4-2): o "hoje" ja e a tela (1) */}
      <h1>Agenda</h1>

      <table className="agenda-grade" role="grid" aria-label="Semana">
        <thead>
          <tr>
            {/* `<td>`, nao `<th>`: este canto nao rotula coluna nenhuma — e
                so o rotulo da linha de horas. Um `<th scope="col">` aqui
                faria oito `columnheader`, um a mais que os sete dias */}
            <td className="slot rotulo">Hora</td>
            {semana.map((d) => (
              <th key={d.toISOString()} className="slot rotulo" scope="col">
                {/* "SEG" pequeno + numero grande embaixo, como o Calendar —
                    o dia de hoje ganha o numero num circulo preenchido
                    (linguagem do prototipo, ver estilo/estilo.css) */}
                <span className="dia-cabecalho">
                  <span className="dia-nome">
                    {d.toLocaleDateString('pt-BR', { weekday: 'short' })}
                  </span>
                  <span className={ehHoje(d) ? 'dia-numero hoje' : 'dia-numero'}>
                    {d.getDate()}
                  </span>
                </span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {horas.map((h) => {
            const horario = `${String(h).padStart(2, '0')}:00`
            return (
              <tr key={h}>
                <th className="slot hora" scope="row">
                  {horario}
                </th>
                {semana.map((d) => {
                  const celula = new Date(d)
                  celula.setHours(h, 0, 0, 0)
                  const ocupada = encontrarConsulta(consultas, celula)
                  return (
                    <td
                      key={celula.toISOString()}
                      className={ocupada ? 'slot ocupado' : 'slot'}
                      role="gridcell"
                      tabIndex={0}
                      aria-label={
                        ocupada
                          ? `Consulta de ${ocupada.pacienteNome} as ${celula.toLocaleString('pt-BR')}`
                          : `Agendar ${celula.toLocaleString('pt-BR')}`
                      }
                      onClick={() => setQuando(paraApi(celula))}
                    >
                      {ocupada && (
                        <span className="bloco-sessao" data-estado={ocupada.status.toLowerCase()}>
                          <span className="bloco-sessao-nome">{ocupada.pacienteNome}</span>
                          <span className="bloco-sessao-horario" aria-hidden="true">
                            {horario}
                          </span>
                          {ESTADO[ocupada.status as keyof typeof ESTADO] && (
                            <Chip variante={ESTADO[ocupada.status as keyof typeof ESTADO].variante}>
                              {ESTADO[ocupada.status as keyof typeof ESTADO].texto}
                            </Chip>
                          )}
                        </span>
                      )}
                    </td>
                  )
                })}
              </tr>
            )
          })}
        </tbody>
      </table>

      {quando && (
        <DialogoDeConsulta aberto dtInicial={quando} aoFechar={() => setQuando(null)} />
      )}
    </div>
  )
}
