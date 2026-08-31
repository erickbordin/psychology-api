/**
 * O campo da mensagem se chama `erro`, nao `mensagem`: e o nome do
 * `ErroCampoDTO(String campo, String erro)` da API. Os mocks deste projeto
 * usavam `mensagem` e a suite inteira passava verde enquanto, contra a API real,
 * nenhum campo era marcado.
 */
export type ErroCampo = { campo: string; erro: string }

export type EnvelopeDeErro = {
  status: number
  mensagem: string
  erros: ErroCampo[]
}

export type Pagina<T> = {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

export type TokenResposta = { token: string }

export type Paciente = {
  idPaciente: string
  idUsuario: string
  nome: string
  email: string | null
  telefone: string | null
  dataNascimento: string
  createdAt: string
}

export type NovoPaciente = {
  nome: string
  telefone?: string
  email?: string
  dataNascimento: string
}

export type Anotacao = {
  id: string
  conteudo: string
  createdAt: string
  pacienteId: string
}

/** Espelha o enum `Status` da API. */
export type StatusConsulta = 'AGENDADA' | 'REALIZADA' | 'CANCELADA' | 'FALTOU'

export type Consulta = {
  id: string
  pacienteId: string
  pacienteNome: string
  /** `LocalDateTime` da API: `2026-09-02T15:00:00`, sem fuso. */
  dtConsulta: string
  status: StatusConsulta
  createdAt: string
  /** Preenchido só quando a consulta nasceu de uma série recorrente. */
  serieId: string | null
}

export type NovaConsulta = { pacienteId: string; dtConsulta: string }

/** Os dois campos são opcionais no `ConsultaAtualizacaoDTO`. */
export type AtualizacaoConsulta = { dtConsulta?: string; status?: StatusConsulta }

/**
 * `quantidadeSessoes` e `dtLimite` são um XOR — a API recusa os dois juntos e os
 * dois ausentes, com a violação pendurada em `quantidadeSessoes`.
 */
export type NovaSerie = {
  pacienteId: string
  dtPrimeiraConsulta: string
  quantidadeSessoes?: number
  dtLimite?: string
}

export type Serie = { serieId: string; quantidade: number; consultas: Consulta[] }

export type CancelamentoDeSerie = { serieId: string; ocorrenciasRemovidas: number }

export type Lembrete = {
  id: string
  descricao: string
  concluido: boolean
  createdAt: string
}

export type NovoLembrete = { descricao: string }

/** Espelha o enum `AcaoAuditoria` da API. */
export type AcaoAuditoria = 'CRIACAO' | 'VISUALIZACAO' | 'ATUALIZACAO' | 'EXCLUSAO'

export type LogAuditoria = {
  id: string
  acao: AcaoAuditoria
  entidade: string
  entidadeId: string
  createdAt: string
}

export type AtualizacaoPaciente = {
  nome?: string
  telefone?: string
  email?: string
  dataNascimento?: string
}
