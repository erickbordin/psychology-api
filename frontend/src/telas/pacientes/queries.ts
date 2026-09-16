import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'
import type { DadosDoPaciente } from './esquema'

type PacienteDTO = components['schemas']['PacienteResponseDTO']

/**
 * Container a mao, tipos dos campos vindos do contrato — o padrao da casa,
 * pelo mesmo motivo descrito em `telas/agenda/queries.ts`: `Required<>` e raso
 * e o springdoc nao sabe dizer "nulavel de verdade", so "opcional".
 *
 * `valorSessao` e `consentimentoAvisoEm` sao nulaveis DE VERDADE: paciente sem
 * valor definido e paciente que nunca consentiu sao estados normais, nao dado
 * faltando.
 */
export type Paciente = {
  idPaciente: NonNullable<PacienteDTO['idPaciente']>
  nome: NonNullable<PacienteDTO['nome']>
  email: NonNullable<PacienteDTO['email']> | null
  telefone: NonNullable<PacienteDTO['telefone']> | null
  dataNascimento: NonNullable<PacienteDTO['dataNascimento']> | null
  valorSessao: NonNullable<PacienteDTO['valorSessao']> | null
  canalAviso: NonNullable<PacienteDTO['canalAviso']>
  consentimentoAvisoEm: NonNullable<PacienteDTO['consentimentoAvisoEm']> | null
}

export const CHAVE_DOS_PACIENTES = ['pacientes'] as const

export function usePacientes(busca = '') {
  const termo = busca.trim()
  return useQuery({
    queryKey: [...CHAVE_DOS_PACIENTES, termo],
    queryFn: () => {
      const caminho = termo
        ? `/pacientes?${new URLSearchParams({ nome: termo })}`
        : '/pacientes'
      return api<Paciente[]>(caminho)
    },
    // mantem a lista anterior na tela enquanto a busca nova viaja: sem isto a
    // lista pisca em branco a cada tecla e parece que o cadastro sumiu
    placeholderData: (anterior) => anterior,
  })
}

/**
 * Cadastro e edicao na mesma mutacao: a unica diferenca e o metodo e a rota, e
 * separar em duas duplicaria a invalidacao e o tratamento de erro.
 *
 * Campo vazio do formulario vira `null` no corpo: o backend trata ausencia como
 * "nao mexa" desde a correcao do I6, e mandar string vazia gravaria vazio.
 */
export function useSalvarPaciente(idExistente?: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (valores: DadosDoPaciente) => {
      const corpo = {
        nome: valores.nome,
        email: valores.email || null,
        telefone: valores.telefone || null,
        dataNascimento: valores.dataNascimento || null,
        valorSessao: valores.valorSessao ? Number(valores.valorSessao) : null,
        canalAviso: valores.canalAviso,
      }
      return api<Paciente>(idExistente ? `/pacientes/${idExistente}` : '/pacientes', {
        method: idExistente ? 'PUT' : 'POST',
        body: JSON.stringify(corpo),
      })
    },
    onSuccess: () => cliente.invalidateQueries({ queryKey: CHAVE_DOS_PACIENTES }),
  })
}
