import { describe, expect, it } from 'vitest'

import { fraseDaTrilha } from './frases'

describe('fraseDaTrilha', () => {
  it.each([
    ['VISUALIZACAO', 'FichaDoPaciente', 'Abriu a ficha'],
    ['CRIACAO', 'Paciente', 'Cadastrou paciente'],
    ['VISUALIZACAO', 'Paciente', 'Viu o cadastro'],
    ['ATUALIZACAO', 'Paciente', 'Editou paciente'],
    ['EXCLUSAO', 'Paciente', 'Excluiu paciente'],
    ['CRIACAO', 'Consulta', 'Agendou sessão'],
    ['VISUALIZACAO', 'Consulta', 'Viu sessão'],
    ['ATUALIZACAO', 'Consulta', 'Alterou sessão'],
    ['EXCLUSAO', 'Consulta', 'Excluiu sessão'],
    ['CRIACAO', 'Anotacao', 'Registrou anotação'],
    ['VISUALIZACAO', 'Anotacao', 'Leu anotações'],
    ['EXCLUSAO', 'Anotacao', 'Excluiu anotação'],
    ['CRIACAO', 'Pendencia', 'Criou pendência'],
    ['ATUALIZACAO', 'Pendencia', 'Concluiu pendência'],
    ['EXCLUSAO', 'Pendencia', 'Excluiu pendência'],
    ['ATUALIZACAO', 'ConsentimentoDoPaciente', 'Alterou a autorização de aviso'],
    ['ATUALIZACAO', 'DescadastroDoPaciente', 'Paciente parou de receber lembretes'],
    ['ATUALIZACAO', 'Ajustes', 'Alterou os ajustes'],
    ['ATUALIZACAO', 'Senha', 'Trocou a senha'],
  ])('%s em %s vira "%s"', (acao, entidade, frase) => {
    expect(fraseDaTrilha(acao, entidade)).toBe(frase)
  })

  it('combinação fora da tabela cai no formato ação · entidade', () => {
    expect(fraseDaTrilha('CRIACAO', 'Ajustes')).toBe('criacao · Ajustes')
    expect(fraseDaTrilha('EXCLUSAO', 'Senha')).toBe('exclusao · Senha')
    expect(fraseDaTrilha('VISUALIZACAO', 'Pendencia')).toBe('visualizacao · Pendencia')
  })
})
