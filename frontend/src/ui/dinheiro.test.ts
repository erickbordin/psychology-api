import { describe, expect, it } from 'vitest'

import { emReais, mesPorExtenso, mesVizinho } from './dinheiro'

describe('dinheiro', () => {
  it('formata em reais com duas casas', () => {
    expect(emReais(150).replace(/ /g, ' ')).toBe('R$ 150,00')
    expect(emReais(1234.5).replace(/ /g, ' ')).toBe('R$ 1.234,50')
  })

  it('escreve o mes por extenso', () => {
    expect(mesPorExtenso(2026, 10)).toBe('outubro de 2026')
  })

  it('anda um mes para tras e para frente atravessando o ano', () => {
    expect(mesVizinho(2027, 1, -1)).toEqual({ ano: 2026, mes: 12 })
    expect(mesVizinho(2026, 12, 1)).toEqual({ ano: 2027, mes: 1 })
    expect(mesVizinho(2026, 6, 1)).toEqual({ ano: 2026, mes: 7 })
  })
})
