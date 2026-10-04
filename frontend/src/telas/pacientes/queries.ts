import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from '../../api/cliente'
import type { components } from '../../api/contrato'
// A chave da ficha vem de quem a declara. O caminho de volta
// (`ficha/queries.ts` -> `Paciente` daqui) e `import type`, apagado na
// compilacao — nao ha ciclo em tempo de execucao.
import { CHAVE_DA_FICHA } from '../ficha/queries'
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
  descadastradoEm: NonNullable<PacienteDTO['descadastradoEm']> | null
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
 * **O mapeamento de campo vazio e diferente nos dois metodos, e a diferenca e
 * a fronteira entre "limpa" e "nao faz nada em silencio".**
 *
 * - `POST`: campo vazio vira `null`. Nao ha o que limpar num cadastro novo, e
 *   `null` e o que a coluna nulavel guarda para "nao informado". Mandar `""`
 *   gravaria uma string vazia, que nao e a mesma coisa e obrigaria toda a
 *   leitura a tratar dois "sem valor".
 * - `PUT`: `email` e `telefone` vao COMO ESTAO, inclusive `""`. A regra nova do
 *   servidor e explicita: campo em branco LIMPA, campo ausente do payload fica
 *   intocado. Como o dialogo sempre abre com o valor atual preenchido, o que
 *   sobrou na caixa e a intencao do usuario — esvaziar a caixa e pedir para
 *   limpar, e mandar `null` (ou omitir) faria o botao "Salvar" nao fazer nada
 *   visivel, que e o defeito que essa regra existe para matar.
 *
 * `dataNascimento` e `valorSessao` seguem indo como `null` nos dois: a regra do
 * branco-limpa vale so para `email` e `telefone`, e uma data em branco nao tem
 * representacao de "string vazia" no `LocalDate` do servidor.
 *
 * Limpar o e-mail com `canalAviso = EMAIL` e recusado pelo servidor com 400 e
 * mensagem acionavel — ela chega ao campo pelo envelope de sempre, via
 * `useFormularioDoProduto`.
 */
export function useSalvarPaciente(idExistente?: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (valores: DadosDoPaciente) => {
      const editando = Boolean(idExistente)
      const corpo = {
        nome: valores.nome,
        email: editando ? valores.email : valores.email || null,
        telefone: editando ? valores.telefone : valores.telefone || null,
        dataNascimento: valores.dataNascimento || null,
        valorSessao: valores.valorSessao ? Number(valores.valorSessao) : null,
        canalAviso: valores.autorizaAviso ? 'EMAIL' : 'NENHUM',
      }
      return api<Paciente>(editando ? `/pacientes/${idExistente}` : '/pacientes', {
        method: editando ? 'PUT' : 'POST',
        body: JSON.stringify(corpo),
      })
    },
    // A ficha tambem mostra nome, contato e valor no cabecalho (spec 4(3)):
    // editar A PARTIR DELA e deixar so a lista fresca deixaria a tela de onde
    // o usuario clicou "Salvar" exibindo o dado velho. A convencao da casa
    // (frontend-design.md) e declarar TODA chave afetada aqui, nao contar com
    // o `refetchOnMount` para consertar depois.
    onSuccess: async () => {
      await cliente.invalidateQueries({ queryKey: CHAVE_DOS_PACIENTES })
      if (idExistente) {
        await cliente.invalidateQueries({ queryKey: CHAVE_DA_FICHA(idExistente) })
      }
    },
  })
}
