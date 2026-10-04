import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'
import type { DadosDosAjustes } from './esquema'

type AjustesDTO = components['schemas']['AjustesResponseDTO']

export type Ajustes = {
  nome: NonNullable<AjustesDTO['nome']>
  email: NonNullable<AjustesDTO['email']>
  valorPadraoSessao: NonNullable<AjustesDTO['valorPadraoSessao']> | null
  antecedencia: NonNullable<AjustesDTO['antecedencia']>
  blocosAviso: NonNullable<AjustesDTO['blocosAviso']>
  tituloNoEmail: NonNullable<AjustesDTO['tituloNoEmail']>
  tituloProfissional: NonNullable<AjustesDTO['tituloProfissional']> | null
  resumoDiario: NonNullable<AjustesDTO['resumoDiario']>
  expedienteInicio: NonNullable<AjustesDTO['expedienteInicio']> | null
  expedienteFim: NonNullable<AjustesDTO['expedienteFim']> | null
}

export const CHAVE_DOS_AJUSTES = ['ajustes'] as const

export function useAjustes() {
  return useQuery({
    queryKey: CHAVE_DOS_AJUSTES,
    queryFn: () => api<Ajustes>('/ajustes'),
    retry: 1,
    retryDelay: 0,
  })
}

export function useSalvarAjustes() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (valores: DadosDosAjustes) =>
      api<Ajustes>('/ajustes', {
        method: 'PUT',
        body: JSON.stringify({
          nome: valores.nome,
          valorPadraoSessao: valores.valorPadraoSessao ? Number(valores.valorPadraoSessao) : null,
          antecedencia: valores.antecedencia,
          blocosAviso: valores.blocosAviso,
          tituloNoEmail: valores.tituloNoEmail,
          tituloProfissional: valores.tituloProfissional,
          resumoDiario: valores.resumoDiario,
          expedienteInicio: valores.expedienteInicio === '' ? null : Number(valores.expedienteInicio),
          expedienteFim: valores.expedienteFim === '' ? null : Number(valores.expedienteFim),
        }),
      }),
    onSuccess: () =>
      Promise.all([
        cliente.invalidateQueries({ queryKey: CHAVE_DOS_AJUSTES }),
        cliente.invalidateQueries({ queryKey: ['trilha'] }),
      ]),
  })
}
