import { describe, expect, it } from 'vitest'

import { ErroDaApi, erroDoCampo } from './erro'

describe('envelope de erro', () => {
  it('acha a mensagem do campo culpado', () => {
    const erro = new ErroDaApi(400, 'Dados invalidos.', [
      { campo: 'senha', erro: 'Essa senha é fácil de adivinhar. Escolha outra.' },
    ])

    expect(erroDoCampo(erro, 'senha')).toBe('Essa senha é fácil de adivinhar. Escolha outra.')
    expect(erroDoCampo(erro, 'email')).toBeUndefined()
  })

  it('nao explode com erro que nao e da API', () => {
    expect(erroDoCampo(new Error('rede caiu'), 'senha')).toBeUndefined()
    expect(erroDoCampo(undefined, 'senha')).toBeUndefined()
  })
})
