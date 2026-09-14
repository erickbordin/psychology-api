import { useQuery } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'

type ConflitoResponseDTO = components['schemas']['ConflitoResponseDTO']

/**
 * O container e escrito a mao, e so os TIPOS dos campos vem do contrato —
 * `Required<>` sozinho nao serve aqui porque e raso, mas o problema real e
 * outro: `pacienteNome` e `consultaId` vem nulos quando o horario esta livre,
 * e o contrato (gerado do springdoc) so consegue marca-los como opcionais,
 * nunca como nulaveis de verdade. Escrever o container aqui deixa isso
 * explicito sem afirmar uma mentira. Nomear os campos a partir do contrato
 * (`ConflitoResponseDTO['livre']` etc.) ainda garante que renomear um campo no
 * backend quebra o build aqui.
 */
export type Conflito = {
  livre: NonNullable<ConflitoResponseDTO['livre']>
  pacienteNome: NonNullable<ConflitoResponseDTO['pacienteNome']> | null
  consultaId: NonNullable<ConflitoResponseDTO['consultaId']> | null
}

/**
 * Checagem PREVIA (spec 5.6 e 6.3). A API ja recusa conflito no envio; sem
 * esta rota o front so descobre apanhando.
 *
 * `excluirConsultaId` e opcional e existe para a EDICAO: sem ele, remarcar a
 * consulta da Helena responderia "ocupado por Helena Vaz", impedindo um
 * salvamento que a API aceitaria.
 */
export function useConflito(dtConsulta: string | null, excluirConsultaId?: string) {
  return useQuery({
    queryKey: ['conflito', dtConsulta, excluirConsultaId],
    enabled: Boolean(dtConsulta),
    queryFn: () => {
      const parametros = new URLSearchParams({ dtConsulta: dtConsulta! })
      if (excluirConsultaId) parametros.set('excluirConsultaId', excluirConsultaId)
      return api<Conflito>(`/consultas/conflito?${parametros}`)
    },
  })
}

/**
 * Os quatro campos usados na grade sao planos (nenhum array/objeto aninhado
 * dentro deles), entao `Required<Pick<...>>` alcanca certinho — a licao da
 * Task 7 era sobre `Required<>` nao descer para DENTRO de um campo que e ele
 * mesmo um array de objetos (`sessoes[]`), o que nao e o caso aqui.
 */
export type ConsultaDaAgenda = Required<
  Pick<components['schemas']['ConsultaResponseDTO'], 'id' | 'pacienteNome' | 'dtConsulta' | 'status'>
>

/**
 * A grade da semana (spec 4②): sem esta busca a Agenda so mostra celulas
 * vazias clicaveis — "semana por padrao" que nao mostra o que ja esta
 * marcado nao e agenda, e seletor de data. A chave leva `de`/`ate`: sem eles,
 * trocar de semana reaproveitaria o cache da semana anterior.
 *
 * `de`/`ate` sao DATA PURA (`yyyy-MM-dd`), nao data-hora — o endpoint aceita
 * os dois tipos para campos diferentes, e este e o de faixa (`date`), nao o
 * de agendamento (`date-time`).
 */
export function useConsultasDaSemana(de: string, ate: string) {
  return useQuery({
    queryKey: ['consultas', de, ate],
    queryFn: () => api<ConsultaDaAgenda[]>(`/consultas?de=${de}&ate=${ate}`),
  })
}
