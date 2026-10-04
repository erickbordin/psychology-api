import { describe, expect, it } from 'vitest'

import { ASSUNTO_DO_LEMBRETE, LINK_DE_EXEMPLO, montarPrevia, type DadosDaPrevia } from './previa'

const QUINTA = new Date(2026, 9, 8, 14, 0)
const EXEMPLO = { primeiroNome: 'Helena', quando: QUINTA }

const base: DadosDaPrevia = {
  nome: 'Ana Souza',
  blocosAviso: [],
  tituloNoEmail: false,
  tituloProfissional: '',
}

describe('montarPrevia', () => {
  it('o corpo e igual ao do backend, exceto o endereco do link, que e de exemplo', () => {
    const { corpo } = montarPrevia({ ...base, blocosAviso: ['DURACAO', 'REMARCAR'] }, EXEMPLO)

    const esperadoDoBackend = [
      'Olá, Helena.',
      '',
      'Você tem um horário marcado para quinta-feira, 8 de outubro, às 14:00.',
      '',
      'Se precisar remarcar, é só responder este e-mail.',
      'Nosso encontro dura 50 minutos.',
      '',
      'Não quer mais receber estes lembretes? Acesse:',
    ].join('\n')

    const linhas = corpo.split('\n')
    expect(linhas.slice(0, -1).join('\n')).toBe(esperadoDoBackend)
    expect(linhas.at(-1)).toBe(LINK_DE_EXEMPLO)
    expect(LINK_DE_EXEMPLO).toMatch(/\/descadastro\/exemplo$/)
  })

  it('sem nenhum bloco nao deixa linha em branco sobrando', () => {
    const { corpo } = montarPrevia(base, EXEMPLO)

    expect(corpo).toBe(
      [
        'Olá, Helena.',
        '',
        'Você tem um horário marcado para quinta-feira, 8 de outubro, às 14:00.',
        '',
        'Não quer mais receber estes lembretes? Acesse:',
        LINK_DE_EXEMPLO,
      ].join('\n'),
    )
  })

  it('com todos os blocos usa a ordem fixa, nao a da lista', () => {
    const { corpo } = montarPrevia({ ...base, blocosAviso: ['DURACAO', 'CHEGADA', 'REMARCAR'] }, EXEMPLO)

    expect(corpo).toContain(
      [
        'Se precisar remarcar, é só responder este e-mail.',
        'Chegue com alguns minutos de antecedência.',
        'Nosso encontro dura 50 minutos.',
      ].join('\n'),
    )
  })

  it('cada bloco aparece so quando ligado', () => {
    const { corpo } = montarPrevia({ ...base, blocosAviso: ['CHEGADA'] }, EXEMPLO)

    expect(corpo).toContain('Chegue com alguns minutos de antecedência.')
    expect(corpo).not.toContain('remarcar')
    expect(corpo).not.toContain('50 minutos')
  })

  it('o remetente leva o titulo so quando ligado e nao em branco', () => {
    const ligado = { ...base, tituloNoEmail: true, tituloProfissional: ' Psicóloga · CRP 07/12345 ' }

    expect(montarPrevia(ligado, EXEMPLO).de).toBe('Ana Souza · Psicóloga · CRP 07/12345')
    expect(montarPrevia({ ...ligado, tituloNoEmail: false }, EXEMPLO).de).toBe('Ana Souza')
    expect(montarPrevia({ ...ligado, tituloProfissional: '   ' }, EXEMPLO).de).toBe('Ana Souza')
  })

  it('o assunto e sempre o mesmo', () => {
    expect(montarPrevia(base, EXEMPLO).assunto).toBe('Lembrete de agendamento')
    expect(montarPrevia({ ...base, blocosAviso: ['REMARCAR'], tituloNoEmail: true }, EXEMPLO).assunto).toBe(
      ASSUNTO_DO_LEMBRETE,
    )
  })

  it('formata hora com zero a esquerda', () => {
    const { corpo } = montarPrevia(base, { primeiroNome: 'Ana', quando: new Date(2026, 9, 9, 9, 5) })

    expect(corpo).toContain('sexta-feira, 9 de outubro, às 09:05.')
  })
})
