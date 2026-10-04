export type BlocoDoAviso = 'REMARCAR' | 'CHEGADA' | 'DURACAO'

export type DadosDaPrevia = {
  nome: string
  blocosAviso: BlocoDoAviso[]
  tituloNoEmail: boolean
  tituloProfissional: string
}

export type ExemploDaPrevia = {
  primeiroNome: string
  quando: Date
}

export const ASSUNTO_DO_LEMBRETE = 'Lembrete de agendamento'
export const LINK_DE_EXEMPLO = 'https://exemplo.local/descadastro/exemplo'

const FRASES: [BlocoDoAviso, string][] = [
  ['REMARCAR', 'Se precisar remarcar, é só responder este e-mail.'],
  ['CHEGADA', 'Chegue com alguns minutos de antecedência.'],
  ['DURACAO', 'Nosso encontro dura 50 minutos.'],
]

const DATA_POR_EXTENSO = new Intl.DateTimeFormat('pt-BR', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
})

function hora(quando: Date) {
  const dois = (n: number) => String(n).padStart(2, '0')
  return `${dois(quando.getHours())}:${dois(quando.getMinutes())}`
}

function nomeDoRemetente(dados: DadosDaPrevia) {
  const titulo = dados.tituloProfissional.trim()
  return dados.tituloNoEmail && titulo !== '' ? `${dados.nome} · ${titulo}` : dados.nome
}

export function montarPrevia(dados: DadosDaPrevia, exemplo: ExemploDaPrevia) {
  const frases = FRASES.filter(([bloco]) => dados.blocosAviso.includes(bloco)).map(([, frase]) => frase)
  const linhas = [
    `Olá, ${exemplo.primeiroNome}.`,
    '',
    `Você tem um horário marcado para ${DATA_POR_EXTENSO.format(exemplo.quando)}, às ${hora(exemplo.quando)}.`,
    '',
    ...(frases.length > 0 ? [...frases, ''] : []),
    'Não quer mais receber estes lembretes? Acesse:',
    LINK_DE_EXEMPLO,
  ]
  return { de: nomeDoRemetente(dados), assunto: ASSUNTO_DO_LEMBRETE, corpo: linhas.join('\n') }
}
